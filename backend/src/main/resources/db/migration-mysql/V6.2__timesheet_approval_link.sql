-- ============================================================
-- V6.2 timesheet_week.approval_instance_id 关联 (MySQL 版)
-- 与 PostgreSQL 版 V6.2__timesheet_approval_link.sql 同构
-- 接入通用审批工作流引擎 (WP-M7-04)
-- ============================================================

-- 1) 列存在检查后添加 (幂等)
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'timesheet_week'
                     AND COLUMN_NAME = 'approval_instance_id');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE timesheet_week ADD COLUMN approval_instance_id BIGINT NULL',
              'SELECT ''approval_instance_id exists'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 索引
SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'timesheet_week'
                     AND INDEX_NAME = 'idx_timesheet_approval_instance');
SET @sql = IF(@idx_exists = 0,
              'CREATE INDEX idx_timesheet_approval_instance ON timesheet_week(approval_instance_id)',
              'SELECT ''idx_timesheet_approval_instance exists'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;