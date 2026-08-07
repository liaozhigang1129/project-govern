package com.company.zhiyu.common.security;

import com.company.zhiyu.module.admin.SystemConfigService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具服务。
 *
 * 时长优先级: SystemConfigService (UI 可改) > @Value 环境变量 > 默认
 *  - security.session.access_token_hours  (默认 2)
 *  - security.session.refresh_token_days  (默认 30)
 *
 * 任何 SystemConfigService.getInt 失败,降级到 @Value (env 覆盖)
 */
@Service
public class JwtService {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";
    public static final long CLOCK_SKEW_SECONDS = 60L;

    private final SecretKey signingKey;
    @Getter private volatile long accessExpirationMs;
    @Getter private volatile long refreshExpirationMs;

    private final SystemConfigService systemConfig;
    private final long envAccessHours;
    private final long envRefreshDays;

    public JwtService(SystemConfigService systemConfig,
                      @Value("${pmo.security.jwt.secret}") String secret,
                      @Value("${pmo.security.jwt.access-expiration-hours:2}") long envAccessHours,
                      @Value("${pmo.security.jwt.refresh-expiration-days:30}") long envRefreshDays) {
        this.systemConfig = systemConfig;
        this.envAccessHours = envAccessHours;
        this.envRefreshDays = envRefreshDays;
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        refreshTtlFromConfig();
        com.company.zhiyu.common.security.JwtsParserHolder.setSecret(secret);
    }

    /** 每次生成 token 重新读 config (让 UI 改完立刻生效) */
    private void refreshTtlFromConfig() {
        long ah = systemConfig.getInt("security.session.access_token_hours", (int) envAccessHours);
        long rd = systemConfig.getInt("security.session.refresh_token_days", (int) envRefreshDays);
        this.accessExpirationMs = ah * 3600_000L;
        this.refreshExpirationMs = rd * 86_400_000L;
    }

    public String generateAccessToken(String username) {
        refreshTtlFromConfig();
        return build(username, TYPE_ACCESS, accessExpirationMs);
    }

    public String generateRefreshToken(String username) {
        refreshTtlFromConfig();
        return build(username, TYPE_REFRESH, refreshExpirationMs);
    }

    private String build(String username, String type, long ttlMs) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .claim("typ", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) { return parseClaims(token).getPayload().getSubject(); }
    public String extractJti(String token) { return parseClaims(token).getPayload().getId(); }
    public String extractType(String token) {
        Object typ = parseClaims(token).getPayload().get("typ");
        return typ == null ? null : typ.toString();
    }

    public boolean isTokenValid(String token, String username, String expectedType) {
        try {
            var claims = parseClaims(token).getPayload();
            if (username != null && !username.equals(claims.getSubject())) return false;
            String typ = claims.get("typ", String.class);
            if (expectedType != null && !expectedType.equals(typ)) return false;
            return true;
        } catch (Exception e) { return false; }
    }

    private Jws<Claims> parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .clockSkewSeconds(CLOCK_SKEW_SECONDS)
                .build()
                .parseSignedClaims(token);
    }
}
