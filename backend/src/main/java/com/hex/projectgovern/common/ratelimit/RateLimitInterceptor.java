package com.hex.projectgovern.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流拦截器 (P2 #29 报表导出 100 req/min/IP).
 *
 * <p>策略: sliding window, 每个 IP 单独计数, 内存存储.
 * <p>v5 计划: 切到 Redis (多实例共享)
 */
@Component
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    /** key = IP, value = 该 IP 最近 60s 的请求时间戳 */
    private final ConcurrentHashMap<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod hm)) return true;
        RateLimit rl = hm.getMethodAnnotation(RateLimit.class);
        if (rl == null) return true;

        String ip = clientIp(request);
        int permits = rl.permitsPerMinute();
        long now = System.currentTimeMillis();
        long windowMs = 60_000L;

        Deque<Long> dq = buckets.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (dq) {
            // 清理过期时间戳
            while (!dq.isEmpty() && now - dq.peekFirst() > windowMs) {
                dq.pollFirst();
            }
            if (dq.size() >= permits) {
                log.warn("[RateLimit] blocked ip={} method={} permits={}/min", ip, request.getRequestURI(), permits);
                response.setStatus(429);
                response.setHeader("Retry-After", "60");
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":429,\"message\":\"Too Many Requests (rate limit " + permits + "/min/IP)\"}");
                return false;
            }
            dq.offerLast(now);
        }
        return true;
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}