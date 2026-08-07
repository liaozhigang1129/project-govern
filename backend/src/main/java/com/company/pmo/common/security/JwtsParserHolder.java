package com.company.pmo.common.security;

import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * 单例 JwtParser 持有者。
 *
 * 避免每次 Filter 解析时重新 build parser(虽然轻量,但 hot path 节省 GC)。
 * secret 来自配置,key 必须 ≥ 32 字节(HS512 要求)。
 */
final class JwtsParserHolder {

    /** 真实部署时由 Spring 注入 secret 进来;测试环境用 dev 默认 */
    private static volatile String CURRENT_SECRET =
            "pmo-pms-dev-secret-please-change-in-production-2025-min-32-bytes";

    /** 包内可见(JwtAuthFilter 用) */
    static volatile JwtParser PARSER = buildParser(CURRENT_SECRET);

    private JwtsParserHolder() {}

    static synchronized void setSecret(String secret) {
        CURRENT_SECRET = secret;
        PARSER = buildParser(secret);
    }

    static String getSecret() {
        return CURRENT_SECRET;
    }

    private static JwtParser buildParser(String secret) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .clockSkewSeconds(JwtService.CLOCK_SKEW_SECONDS)
                .build();
    }
}
