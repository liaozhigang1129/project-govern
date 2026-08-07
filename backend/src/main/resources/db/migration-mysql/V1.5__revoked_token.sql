-- V1.5-c: 撤销 token 黑名单(JWT 短过期方案需要服务端失效能力)

CREATE TABLE revoked_token (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    jti         VARCHAR(64)  NOT NULL,
    user_id     BIGINT       NOT NULL,
    revoked_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at  TIMESTAMP    NOT NULL,
    UNIQUE KEY uk_revoked_token_jti (jti),
    KEY idx_revoked_token_expires (expires_at),
    CONSTRAINT fk_revoked_token_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
