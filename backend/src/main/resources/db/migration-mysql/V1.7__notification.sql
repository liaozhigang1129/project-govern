-- P1.5 收尾: 通知中心持久化表
CREATE TABLE IF NOT EXISTS notification (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    recipient_id    BIGINT NOT NULL,
    category        VARCHAR(32) NOT NULL,
    resource_id     BIGINT,
    resource_code   VARCHAR(64),
    title           VARCHAR(256) NOT NULL,
    content         TEXT,
    status          VARCHAR(16) NOT NULL,
    read_at         DATETIME(6),
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX ix_notification_recipient_unread (recipient_id, status, created_at),
    INDEX ix_notification_recipient_created (recipient_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
