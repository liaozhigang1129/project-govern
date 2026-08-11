-- ============================================================
-- V6.3 project_initiation.approval_instance_id 关联 (MySQL 版)
-- 与 PostgreSQL 版 V6.3__initiation_approval_link.sql 同构
-- WP-M7-05 立项审批全量迁移引擎
-- ============================================================

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.columns
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'project_initiation'
                     AND COLUMN_NAME = 'approval_instance_id');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE project_initiation ADD COLUMN approval_instance_id BIGINT NULL',
              'SELECT ''approval_instance_id exists'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'project_initiation'
                     AND INDEX_NAME = 'idx_initiation_approval_instance');
SET @sql = IF(@idx_exists = 0,
              'CREATE INDEX idx_initiation_approval_instance ON project_initiation(approval_instance_id)',
              'SELECT ''idx_initiation_approval_instance exists'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;