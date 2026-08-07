package com.hex.projectgovern.common.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, Long> {

    /**
     * 是否在黑名单(jti 存在且未过期)。
     * 带 expires_at 过滤的目的:即使清理 job 漏跑,Filter 也不会误判。
     */
    @Query("""
            SELECT COUNT(rt) > 0
            FROM RevokedToken rt
            WHERE rt.jti = :jti AND rt.expiresAt > :now
            """)
    boolean existsByJtiAndNotExpired(@Param("jti") String jti, @Param("now") Instant now);

    /** 删除已过期的黑名单条目(定时调用) */
    @Modifying
    @Query("DELETE FROM RevokedToken rt WHERE rt.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);

    /** 找出该用户所有未过期的黑名单条目 (离职时吊销所有 token 用) */
    @Query("SELECT rt.jti FROM RevokedToken rt WHERE rt.userId = :userId AND rt.expiresAt > :now")
    java.util.List<String> findActiveJtisByUserId(@Param("userId") Long userId, @Param("now") Instant now);
}
