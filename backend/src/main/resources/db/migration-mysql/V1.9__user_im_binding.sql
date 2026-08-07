-- P2-A IM 通知: 用户 ↔ IM 账号绑定表 (MySQL)
--
-- 与 PG 版同构,字段顺序保持一致,便于 cross-dialect 维护。
-- MySQL 8+ 用 SET @cnt + IF 模式保证幂等。

SET @cnt = (SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema=DATABASE() AND table_name='user_im_binding');
SET @sql = IF(@cnt=0, 'CREATE TABLE user_im_binding (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    channel          VARCHAR(32) NOT NULL,
    external_user_id VARCHAR(128) NOT NULL,
    enabled          TINYINT(1) NOT NULL DEFAULT 1,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_channel (user_id, channel),
    KEY ix_im_binding_user (user_id),
    KEY ix_im_binding_channel_external (channel, external_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
