-- ============================================================
-- V5.1 COST_DIFF 告警 (PostgreSQL 版)
-- 与 MySQL 版 V5.1__alert_cost_diff.sql 同构
--
-- 增量:
--   1. alert_type_def 加 COST_DIFF (用于 3-way match 差异告警)
--   2. alert_rule 种子 RULE_COST_DIFF_100 (差异 ¥100 触发,MISMATCH 状态,24h 内去重)
--   3. alert_event 加 project_id 字段 (供前端按项目跳转)
-- ============================================================

-- 1) 字典: 新增类型
INSERT INTO alert_type_def (code, name, default_threshold, default_comparison, target_type, description) VALUES
  ('COST_DIFF',  '财务-成本对账差异', 100, 'GT', 'PROJECT', '3-way match cost_reconciliation.diff_amount ≥ 阈值')
ON CONFLICT (code) DO NOTHING;

-- 2) 种子规则
INSERT INTO alert_rule (code, name, type_code, threshold, comparison, severity, enabled, description, notify_emails)
SELECT * FROM (VALUES
  ('RULE_COST_DIFF_100', '成本对账差异 ≥ ¥100 警告', 'COST_DIFF', 100, 'GT', 'HIGH', TRUE,
   '3-way match 中差异金额超过 ¥100 时触发 (24h 内同 project 同 diff_bucket 去重)',
   'pmo@company.com,finance@company.com')
) AS v(code, name, type_code, threshold, comparison, severity, enabled, description, emails)
WHERE NOT EXISTS (SELECT 1 FROM alert_rule WHERE alert_rule.code = v.code);

-- 3) alert_event 加 project_id (供前端快速按项目过滤)
ALTER TABLE alert_event ADD COLUMN IF NOT EXISTS project_id BIGINT NULL;
CREATE INDEX IF NOT EXISTS idx_alert_event_project_id ON alert_event(project_id);

COMMENT ON COLUMN alert_event.project_id IS '可选,关联 project.id (COST_DIFF 等 PROJECT 类型告警专用)';
