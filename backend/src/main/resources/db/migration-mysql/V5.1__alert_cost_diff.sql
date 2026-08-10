-- ============================================================
-- V5.1 COST_DIFF 告警 (MySQL 版)
-- 与 PostgreSQL 版 V5.1__alert_cost_diff.sql 同构
-- ============================================================

-- 1) 字典: 新增类型
INSERT IGNORE INTO alert_type_def (code, name, default_threshold, default_comparison, target_type, description) VALUES
  ('COST_DIFF', '财务-成本对账差异', 100, 'GT', 'PROJECT', '3-way match cost_reconciliation.diff_amount ≥ 阈值');

-- 2) 种子规则 (idempotent)
INSERT INTO alert_rule (code, name, type_code, threshold, comparison, severity, enabled, description, notify_emails)
SELECT * FROM (VALUES
  ('RULE_COST_DIFF_100', '成本对账差异 ≥ ¥100 警告', 'COST_DIFF', 100, 'GT', 'HIGH', 1,
   '3-way match 中差异金额超过 ¥100 时触发 (24h 内同 project 同 diff_bucket 去重)',
   'pmo@company.com,finance@company.com')
) AS v(code, name, type_code, threshold, comparison, severity, enabled, description, emails)
WHERE NOT EXISTS (SELECT 1 FROM alert_rule WHERE alert_rule.code = v.code);

-- 3) alert_event 加 project_id
-- 注意: MySQL 不支持 IF NOT EXISTS 语法,这里需要 INFORMATION_SCHEMA 兼容检查
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'alert_event'
                     AND COLUMN_NAME = 'project_id');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE alert_event ADD COLUMN project_id BIGINT NULL',
              'SELECT ''project_id exists'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'alert_event'
                     AND INDEX_NAME = 'idx_alert_event_project_id');
SET @sql = IF(@idx_exists = 0,
              'CREATE INDEX idx_alert_event_project_id ON alert_event(project_id)',
              'SELECT ''idx_alert_event_project_id exists'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
