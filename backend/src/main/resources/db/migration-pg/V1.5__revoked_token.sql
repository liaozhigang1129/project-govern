-- V1.5-c: 撤销 token 黑名单(JWT 短过期方案需要服务端失效能力)
-- jti 唯一索引便于 Filter 快速查
-- expires_at 索引便于定时清理过期条目

CREATE TABLE revoked_token (
    id          BIGSERIAL    PRIMARY KEY,
    jti         VARCHAR(64)  NOT NULL,
    user_id     BIGINT       NOT NULL,
    revoked_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_revoked_token_jti UNIQUE (jti),
    CONSTRAINT fk_revoked_token_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE INDEX idx_revoked_token_expires ON revoked_token (expires_at);
