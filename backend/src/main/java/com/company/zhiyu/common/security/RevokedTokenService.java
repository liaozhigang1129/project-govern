package com.company.zhiyu.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 黑名单服务。
 *
 *  - revoke(jti, userId, expiresAt):写入黑名单
 *  - isRevoked(jti):Filter 鉴权前查
 *  - revokeAllByUserId(uid):离职时吊销该用户所有未过期 token
 *  - cleanExpired():每天凌晨 03:30 清理已过期的黑名单条目
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RevokedTokenService {

    private final RevokedTokenRepository repository;

    /** 写入黑名单(主键冲突时幂等忽略) */
    @Transactional
    public void revoke(String jti, Long userId, Instant expiresAt) {
        if (repository.existsByJtiAndNotExpired(jti, Instant.now())) {
            return;  // 已存在,无需重复
        }
        try {
            repository.save(new RevokedToken(jti, userId, Instant.now(), expiresAt));
        } catch (Exception e) {
            // 唯一索引冲突(jti 已存在)→ 静默,等价于已撤销
            log.debug("revoke jti={} idempotent: {}", jti, e.getMessage());
        }
    }

    /** 鉴权前调用:未过期且在黑名单 → true */
    @Transactional(readOnly = true)
    public boolean isRevoked(String jti) {
        if (jti == null) return false;
        return repository.existsByJtiAndNotExpired(jti, Instant.now());
    }

    /** 吊销该用户所有未过期的 token (离职 / 改密后 全踢下线) */
    @Transactional
    public int revokeAllByUserId(Long userId) {
        List<String> jtis = repository.findActiveJtisByUserId(userId, Instant.now());
        Instant farFuture = Instant.now().plusSeconds(365L * 24 * 3600);
        int n = 0;
        for (String jti : jtis) {
            revoke(jti, userId, farFuture);
            n++;
        }
        if (n > 0) log.info("[RevokedToken] user {} all {} sessions revoked", userId, n);
        return n;
    }

    /** 定时清理过期黑名单(每天 03:30) */
    @Scheduled(cron = "0 30 3 * * *")
    public void cleanExpired() {
        int n = doCleanExpired();
        if (n > 0) log.info("[RevokedToken] cleaned {} expired rows", n);
    }

    /** 工具方法:清理过期 (从 cleanExpired 调) */
    public int doCleanExpired() {
        try {
            return repository.deleteExpired(Instant.now());
        } catch (Exception e) {
            log.warn("doCleanExpired skipped: {}", e.getMessage());
            return 0;
        }
    }
}
