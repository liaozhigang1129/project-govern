-- ============================================================
-- V4.4: F4 预警实体补审计列 + soft-delete (MySQL 版)
-- 修复: AlertEvent / AlertRule 继承 SoftDeletableEntity → AuditableEntity,
--      期望 created_at/updated_at/created_by/updated_by/deleted 列
-- alert_rule V4.3 已带 created_at/updated_at, 此处只补 created_by/updated_by/deleted
-- alert_event V4.3 未带, 此处全补
-- ============================================================

-- alert_event 补审计 + soft-delete
SET @db = DATABASE();
SET @stmt = (SELECT IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='alert_event' AND COLUMN_NAME='created_at')=0,
  'ALTER TABLE alert_event ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)', 'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = (SELECT IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='alert_event' AND COLUMN_NAME='updated_at')=0,
  'ALTER TABLE alert_event ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)', 'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = (SELECT IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='alert_event' AND COLUMN_NAME='created_by')=0,
  'ALTER TABLE alert_event ADD COLUMN created_by BIGINT NULL', 'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = (SELECT IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='alert_event' AND COLUMN_NAME='updated_by')=0,
  'ALTER TABLE alert_event ADD COLUMN updated_by BIGINT NULL', 'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = (SELECT IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='alert_event' AND COLUMN_NAME='deleted')=0,
  'ALTER TABLE alert_event ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0', 'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- alert_event 加 idx (MySQL 8.0 不支持 CREATE INDEX IF NOT EXISTS, 用存储过程)
SET @idx = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='alert_event' AND INDEX_NAME='idx_alert_event_created_at');
SET @stmt = IF(@idx=0, 'CREATE INDEX idx_alert_event_created_at ON alert_event(created_at)', 'SELECT 1');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- alert_rule 补 created_by/updated_by/deleted (created_at/updated_at V4.3 已有)
SET @stmt = (SELECT IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='alert_rule' AND COLUMN_NAME='created_by')=0,
  'ALTER TABLE alert_rule ADD COLUMN created_by BIGINT NULL', 'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = (SELECT IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='alert_rule' AND COLUMN_NAME='updated_by')=0,
  'ALTER TABLE alert_rule ADD COLUMN updated_by BIGINT NULL', 'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = (SELECT IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='alert_rule' AND COLUMN_NAME='deleted')=0,
  'ALTER TABLE alert_rule ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0', 'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @idx = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='alert_rule' AND INDEX_NAME='idx_alert_rule_created_at');
SET @stmt = IF(@idx=0, 'CREATE INDEX idx_alert_rule_created_at ON alert_rule(created_at)', 'SELECT 1');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;
