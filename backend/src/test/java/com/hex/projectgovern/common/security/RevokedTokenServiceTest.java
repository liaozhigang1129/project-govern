package com.hex.projectgovern.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RevokedTokenService 单测(4 case):
 *  - revoke 后 isRevoked → true
 *  - 没 revoke → false
 *  - 重复 revoke 幂等
 *  - 过期黑名单 isRevoked → false
 *  - cleanExpired 只删过期行
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(RevokedTokenService.class)
class RevokedTokenServiceTest {

    @Autowired RevokedTokenService service;
    @Autowired RevokedTokenRepository repo;

    @Test
    @DisplayName("revoke 后 isRevoked=true")
    void revokeThenCheck() {
        service.revoke("jti-1", 1L, Instant.now().plusSeconds(3600));
        assertThat(service.isRevoked("jti-1")).isTrue();
    }

    @Test
    @DisplayName("没 revoke → isRevoked=false")
    void notRevoked() {
        assertThat(service.isRevoked("jti-2")).isFalse();
    }

    @Test
    @DisplayName("重复 revoke 幂等,不抛异常")
    void revokeIdempotent() {
        service.revoke("jti-3", 1L, Instant.now().plusSeconds(3600));
        service.revoke("jti-3", 1L, Instant.now().plusSeconds(3600));
        assertThat(service.isRevoked("jti-3")).isTrue();
        assertThat(repo.findAll().stream().filter(r -> r.getJti().equals("jti-3")).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("过期黑名单 isRevoked=false;cleanExpired 删掉")
    void expiredCleanup() {
        // 写一条已过期的
        repo.save(new RevokedToken("jti-old", 1L,
                Instant.now().minusSeconds(7200),
                Instant.now().minusSeconds(60)));
        assertThat(service.isRevoked("jti-old")).isFalse();
        int n = service.doCleanExpired();
        assertThat(n).isGreaterThanOrEqualTo(1);
        assertThat(repo.findAll().stream().anyMatch(r -> r.getJti().equals("jti-old"))).isFalse();
    }
}
