package com.company.zhiyu.common.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 已撤销的 JWT token。
 * 由 AuthController.logout 写入,JwtAuthFilter 在鉴权前先查这里。
 *
 * 不继承 SoftDeletableEntity(物理删除的过期数据,不需要软删能力)。
 * 字段:
 *  - jti:JWT 唯一 ID
 *  - userId:被撤销 token 的用户
 *  - revokedAt:撤销时间
 *  - expiresAt:token 本身的过期时间(到期后可清理此行)
 */
@Entity
@Table(name = "revoked_token")
@Getter @Setter @NoArgsConstructor
public class RevokedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "jti", nullable = false, length = 64, unique = true)
    private String jti;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "revoked_at", nullable = false)
    private Instant revokedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public RevokedToken(String jti, Long userId, Instant revokedAt, Instant expiresAt) {
        this.jti = jti;
        this.userId = userId;
        this.revokedAt = revokedAt;
        this.expiresAt = expiresAt;
    }
}
