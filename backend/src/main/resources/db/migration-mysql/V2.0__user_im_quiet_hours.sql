-- P2 #2 勿扰时段: 每用户多窗口
--
-- 设计:
--  - 一行 = 一个时间窗口(start, end) — 同一人可建多个(午餐+深夜)
--  - end < start 表示跨午夜(如 22:00 ~ 08:00)
--  - timezone 字段支持海外/出差场景(V2.1 启用)
--  - 与 user_im_binding 解耦: 用户可只配 binding 不配 DND(默认永远推)
--
-- 与 PG 版同构(MySQL 8+ 幂等)。

SET @cnt = (SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema=DATABASE() AND table_name='user_im_quiet_hours');
SET @sql = IF(@cnt=0, 'CREATE TABLE user_im_quiet_hours (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    start_time  VARCHAR(5) NOT NULL,
    end_time    VARCHAR(5) NOT NULL,
    timezone    VARCHAR(64) NOT NULL DEFAULT ''Asia/Shanghai'',
    enabled     TINYINT(1) NOT NULL DEFAULT 1,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY ix_quiet_hours_user (user_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
