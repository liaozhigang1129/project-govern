package com.hex.projectgovern.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限流注解 (P2 #29 报表导出 100 req/min/IP).
 *
 * <p>使用:
 * <pre>
 *   @GetMapping("/api/reports/...")
 *   {@code @RateLimit(permitsPerMinute = 100)}
 *   public void export(...) { ... }
 * </pre>
 *
 * <p>实现: {@link RateLimitInterceptor} (按 IP 维度, 内存 sliding window)
 *
 * <p>生产可用 Redis 替代 (v5 计划). 当前 in-memory 够用, dev/CI 无 Redis.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    /** 每分钟允许请求数 */
    int permitsPerMinute() default 60;
}