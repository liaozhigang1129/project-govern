-- P1.5 收尾: 通知中心持久化表
CREATE TABLE IF NOT EXISTS notification (
    id              BIGSERIAL PRIMARY KEY,
    recipient_id    BIGINT NOT NULL,
    category        VARCHAR(32) NOT NULL,
    resource_id     BIGINT,
    resource_code   VARCHAR(64),
    title           VARCHAR(256) NOT NULL,
    content         TEXT,
    status          VARCHAR(16) NOT NULL,
    read_at         TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_notification_recipient_unread ON notification (recipient_id, status, created_at);
CREATE INDEX IF NOT EXISTS ix_notification_recipient_created ON notification (recipient_id, created_at);

-- 找不到收件人时跳过,所以 NOT FK
-- 业务上 recipient_id = app_user.id,不强 FK 避免误删/批量清空
