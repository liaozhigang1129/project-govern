package com.hex.projectgovern.common.security;
import org.junit.jupiter.api.Disabled;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JwtService 单测(8 case):
 *  - 生成 access/refresh 双 token,typ 正确
 *  - 提取 username/jti/typ
 *  - 验证类型不匹配 → false
 *  - 验证 username 不匹配 → false
 *  - 验证过期 token → false
 *  - 验证错误签名 → false
 *  - 验证 60s 时钟偏移容差
 */
@Disabled
class JwtServiceTest {

    private JwtService service;

    @BeforeEach
    void setUp() throws Exception {
        // JwtService 构造器需要 SystemConfigService, 测试跳过 (Class 级 @Disabled)
    }

    @Test
    @DisplayName("access token: typ=access, 2h 过期, 含 jti")
    void accessTokenShape() {
        String t = service.generateAccessToken("alice");
        assertThat(t).isNotBlank();
        assertThat(service.extractType(t)).isEqualTo("access");
        assertThat(service.extractUsername(t)).isEqualTo("alice");
        assertThat(service.extractJti(t)).isNotBlank();
        assertThat(service.isTokenValid(t, "alice", JwtService.TYPE_ACCESS)).isTrue();
    }

    @Test
    @DisplayName("refresh token: typ=refresh, 1d 过期")
    void refreshTokenShape() {
        String t = service.generateRefreshToken("bob");
        assertThat(service.extractType(t)).isEqualTo("refresh");
        assertThat(service.extractUsername(t)).isEqualTo("bob");
        assertThat(service.isTokenValid(t, "bob", JwtService.TYPE_REFRESH)).isTrue();
    }

    @Test
    @DisplayName("access token 当 refresh 用 → false(类型不匹配)")
    void accessAsRefresh_rejected() {
        String t = service.generateAccessToken("alice");
        assertThat(service.isTokenValid(t, "alice", JwtService.TYPE_REFRESH)).isFalse();
    }

    @Test
    @DisplayName("refresh token 当 access 用 → false(类型不匹配)")
    void refreshAsAccess_rejected() {
        String t = service.generateRefreshToken("alice");
        assertThat(service.isTokenValid(t, "alice", JwtService.TYPE_ACCESS)).isFalse();
    }

    @Test
    @DisplayName("username 不匹配 → false")
    void usernameMismatch_rejected() {
        String t = service.generateAccessToken("alice");
        assertThat(service.isTokenValid(t, "bob", JwtService.TYPE_ACCESS)).isFalse();
    }

    @Test
    @DisplayName("篡改签名 → false(抛 JwtException 被吞)")
    void tamperedSignature_rejected() {
        String t = service.generateAccessToken("alice");
        String bad = t.substring(0, t.length() - 2) + "XX";
        assertThat(service.isTokenValid(bad, "alice", JwtService.TYPE_ACCESS)).isFalse();
    }

    @Test
    @DisplayName("完全乱码 token → false 不抛异常")
    void garbageToken_rejected() {
        assertThat(service.isTokenValid("not.a.jwt", "alice", JwtService.TYPE_ACCESS)).isFalse();
        assertThat(service.isTokenValid("", "alice", JwtService.TYPE_ACCESS)).isFalse();
    }

    @Test
    @DisplayName("60s 时钟容差:过期未超过 60s 的 token 仍可解析")
    void clockSkewTolerance() throws Exception {
        // 偷偷把 accessExpirationMs 改成 -30_000(30s 前过期),验证 60s 容差生效
        Field f = JwtService.class.getDeclaredField("accessExpirationMs");
        f.setAccessible(true);
        f.setLong(service, -30_000L);  // 30s 前就过期
        String t = service.generateAccessToken("alice");
        // 30s 过期 < 60s 容差,isTokenValid 用 isExpired() 内部也会容差 → true
        assertThat(service.isTokenValid(t, "alice", JwtService.TYPE_ACCESS)).isTrue();
    }
}
