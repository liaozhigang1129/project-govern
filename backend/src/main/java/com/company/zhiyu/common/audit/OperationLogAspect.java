package com.company.zhiyu.common.audit;
import com.company.zhiyu.common.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审计切面:拦截标了 {@link AuditLog} 的方法,自动落 operation_log 表。
 *
 * <p>核心流程:
 * <ol>
 *   <li>执行原方法(proceed)</li>
 *   <li>记录耗时和返回结果</li>
 *   <li>异步落表(@Async → auditExecutor 8 线程池)</li>
 *   <li>异常也落表(result=FAILURE,error=ex.message)</li>
 * </ol>
 *
 * <p>payload 字段:
 * <ul>
 *   <li><b>request</b>  - 请求体/查询参数(摘要)</li>
 *   <li><b>response</b> - 响应结果(摘要)</li>
 *   <li><b>result</b>   - SUCCESS / FAILURE</li>
 *   <li><b>error</b>    - 异常信息(仅 FAILURE)</li>
 *   <li><b>duration</b> - 耗时 ms</li>
 * </ul>
 *
 * <p>resource_id 提取优先级:
 * <ol>
 *   <li>方法返回值(若为 Long / Number,直接取)</li>
 *   <li>返回值 ApiResponse.data.id(若 Map 含 data 字段且 data 是 Map)</li>
 *   <li>路径变量(若 URL 含 /{id} 且是数字)</li>
 *   <li>无法提取 → null</li>
 * </ol>
 *
 * @see AuditLog
 * @since 2026-Q1 P1.5-d
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class OperationLogAspect {

    private final OperationLogRepository repository;
    private final SecurityUtils securityUtils;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** payload 上限 8KB,超过截断(防止日志表撑爆) */
    private static final int PAYLOAD_MAX = 8 * 1024;

    @Around("@annotation(com.company.zhiyu.common.audit.AuditLog)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        AuditLog anno = method.getAnnotation(AuditLog.class);

        // 1) 抓请求上下文(只有 HTTP 线程才有,定时任务/异步直接为 null)
        HttpServletRequest req = currentRequest();
        String ip = extractIp(req);
        Long userId = currentUserId();
        String methodName = method.getDeclaringClass().getSimpleName() + "." + method.getName();

        // 2) 执行原方法
        Object result = null;
        Throwable thrown = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable t) {
            thrown = t;
            throw t;
        } finally {
            long duration = System.currentTimeMillis() - start;
            try {
                Long resourceId = anno.extractResourceId() ? extractResourceId(result, req) : null;
                String payload = buildPayload(pjp, result, thrown, duration);
                // 3) 异步落表(@Async → auditExecutor)
                asyncPersist(anno, userId, resourceId, ip, payload, methodName);
            } catch (Exception ex) {
                // 审计失败不能影响主业务
                log.warn("[Audit] persist failed: method={} err={}", methodName, ex.getMessage());
            }
        }
    }

    /**
     * 显式 @Async 异步落表 + 失败重试 1 次(仍失败 WARN,不阻塞主业务)。
     * <p>失败不抛,因为 @Async void 方法异常会被吞,这里自己 try/catch 即可。
     * <p>不引新的 retry 库,避免引入 spring-retry 依赖。
     */
    @Async("auditExecutor")
    void asyncPersist(AuditLog anno, Long userId, Long resourceId,
                      String ip, String payload, String methodName) {
        OperationLog row = new OperationLog();
        row.setUserId(userId);
        row.setResourceType(anno.module());
        row.setResourceId(resourceId);
        row.setAction(anno.action());
        row.setPayload(truncate(payload));
        row.setIpAddress(ip);
        row.setCreatedAt(Instant.now());
        // 失败重试 1 次
        Exception last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                repository.save(row);
                if (attempt > 1) log.info("[Audit] save retry succeeded on attempt={} method={}", attempt, methodName);
                return;
            } catch (Exception ex) {
                last = ex;
                log.warn("[Audit] save failed (attempt={}): module={} action={} method={} err={}",
                        attempt, anno.module(), anno.action(), methodName, ex.getMessage());
            }
        }
        log.error("[Audit] save give up after 2 attempts: module={} action={} method={} lastErr={}",
                anno.module(), anno.action(), methodName, last == null ? "?" : last.getMessage());
    }

    /* ---------------- helpers ---------------- */

    private String buildPayload(ProceedingJoinPoint pjp, Object result,
                                Throwable thrown, long duration) {
        Map<String, Object> m = new LinkedHashMap<>();
        // request(只看参数摘要,避免巨大 body)
        Object[] args = pjp.getArgs();
        if (args != null && args.length > 0) {
            m.put("request", summarizeArgs(args));
        }
        // response(若是 ApiResponse,只取 data 摘要)
        if (result != null) {
            m.put("response", summarizeResponse(result));
        }
        if (thrown != null) {
            m.put("result", "FAILURE");
            m.put("error", thrown.getClass().getSimpleName() + ": " + safeMsg(thrown.getMessage()));
        } else {
            m.put("result", "SUCCESS");
        }
        m.put("duration", duration);
        try {
            return objectMapper.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            return "{\"result\":\"PARSE_ERROR\"}";
        }
    }

    private Map<String, Object> summarizeArgs(Object[] args) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            if (a == null) continue;
            String name = "arg" + i;
            if (a instanceof jakarta.servlet.http.HttpServletRequest) continue;
            if (a instanceof jakarta.servlet.http.HttpServletResponse) continue;
            // 摘要式记录
            map.put(name, preview(a));
        }
        return map;
    }

    private Object summarizeResponse(Object result) {
        // ApiResponse {code, message, data, timestamp}
        try {
            // 反射读 code/message/data 字段,失败则原样
            Class<?> c = result.getClass();
            Object code = c.getMethod("getCode").invoke(result);
            Object msg = c.getMethod("getMessage").invoke(result);
            Object data = c.getMethod("getData").invoke(result);
            return Map.of("code", code, "message", msg, "data", preview(data));
        } catch (Exception e) {
            return preview(result);
        }
    }
    /** 摘要式:字符串截断,对象 toString,集合大小,Map 浅拷 */
    private Object preview(Object o) {
        if (o == null) return null;
        if (o instanceof Number || o instanceof Boolean) return o;
        // LocalDate / Instant / LocalDateTime → 序列化为字符串
        if (o instanceof java.time.temporal.TemporalAccessor t) return t.toString();
        // 集合
        if (o instanceof java.util.Collection<?> c) return "[" + c.size() + " items]";
        if (o instanceof Map<?, ?> m) return "{" + m.size() + " keys}";
        // JPA entity(@Entity 类 / 包含 getId 的 DTO)→ 反射读 id + 几个业务字段
        if (looksLikeEntity(o)) return entityToMap(o);
        // 兜底 toString 截断
        String s = o.toString();
        // JPA entity 默认 toString 是 com.x.MyEntity@hash,识别后变 null
        if (s.contains("@") && s.startsWith(o.getClass().getName())) return "{id:see_response}";
        if (s.length() > 200) return s.substring(0, 200) + "...";
        return s;
    }

    private boolean looksLikeEntity(Object o) {
        if (o == null) return false;
        try {
            // 含 getId() + 是非基础类型 + 非 String/Number/Boolean
            o.getClass().getMethod("getId");
            return !o.getClass().isPrimitive()
                    && !(o instanceof String)
                    && !(o instanceof Number)
                    && !(o instanceof Boolean)
                    && !(o instanceof java.time.temporal.TemporalAccessor);
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private Map<String, Object> entityToMap(Object o) {
        Map<String, Object> m = new LinkedHashMap<>();
        // 反射所有 getter
        for (java.lang.reflect.Method getter : o.getClass().getMethods()) {
            String n = getter.getName();
            if (!n.startsWith("get") || n.equals("getClass")) continue;
            if (getter.getParameterCount() != 0) continue;
            try {
                Object v = getter.invoke(o);
                if (v == null) continue;
                String fn = Character.toLowerCase(n.charAt(3)) + n.substring(4);
                m.put(fn, preview(v));
            } catch (Exception ignore) {
            }
        }
        return m;
    }

    private String safeMsg(String s) {
        if (s == null) return "(no message)";
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    private String truncate(String s) {
        if (s == null) return null;
        if (s.length() <= PAYLOAD_MAX) return s;
        return s.substring(0, PAYLOAD_MAX) + "...";
    }

    private Long extractResourceId(Object result, HttpServletRequest req) {
        // 1) 直接是 Long / Number
        if (result instanceof Number n) return n.longValue();
        // 2) 反射 ApiResponse.getData().getId()
        if (result != null) {
            try {
                Object data = result.getClass().getMethod("getData").invoke(result);
                if (data == null) {
                    // 路径变量兜底
                    return extractFromPath(req);
                }
                // data 可能是 Map / DTO / 直接 Long
                if (data instanceof Number n2) return n2.longValue();
                if (data instanceof Map<?, ?> map) {
                    Object id = map.get("id");
                    if (id instanceof Number n3) return n3.longValue();
                }
                // DTO: 反射 getId()
                try {
                    Object id = data.getClass().getMethod("getId").invoke(data);
                    if (id instanceof Number n4) return n4.longValue();
                } catch (NoSuchMethodException ignore) {}
            } catch (Exception ignore) {}
        }
        return extractFromPath(req);
    }

    private Long extractFromPath(HttpServletRequest req) {
        if (req == null) return null;
        String path = req.getRequestURI();
        // 找最后一个数字段
        if (path != null) {
            String[] parts = path.split("/");
            for (int i = parts.length - 1; i >= 0; i--) {
                if (parts[i].matches("\\d+")) return Long.parseLong(parts[i]);
            }
        }
        return null;
    }

    private String extractIp(HttpServletRequest req) {
        if (req == null) return null;
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return req.getRemoteAddr();
    }

    private Long currentUserId() {
        try {
            return securityUtils.currentUserId();
        } catch (Exception e) {
            return null;
        }
    }

    private HttpServletRequest currentRequest() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                return sra.getRequest();
            }
        } catch (Exception ignore) {}
        return null;
    }
}
