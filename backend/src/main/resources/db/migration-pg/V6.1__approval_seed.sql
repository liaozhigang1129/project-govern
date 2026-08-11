-- ============================================================
-- V6.1 审批流程种子数据 (PostgreSQL 版)
-- 与 MySQL 版 V6.1__approval_seed.sql 同构 (数据类型已对齐)
-- ============================================================

INSERT INTO approval_flow_def (kind, code, name, version, enabled, description) VALUES
  ('init', 'STANDARD_INITIATION', '立项标准三级审批', 1, TRUE,
   '立项审批: 部门负责人 → PMO → 执行层 (默认三级)'),
  ('init', 'SIMPLE_INITIATION', '立项快速审批 (金额 < ¥100k)', 1, TRUE,
   '立项审批: 仅需部门负责人 (小额快速通道)'),
  ('timesheet', 'STANDARD_TIMESHEET', '工时审批 (单级)', 1, TRUE,
   '工时提交后由 PMO 审核 (单级审批)')
ON CONFLICT (kind, code, version) DO NOTHING;

-- 立项标准三级
INSERT INTO approval_flow_step (flow_def_id, step_no, role_code, name, required, auto_approve_when, timeout_hours)
SELECT id, 1, 'DEPT_LEAD', '部门负责人审核', TRUE, FALSE, 48 FROM approval_flow_def WHERE code = 'STANDARD_INITIATION' AND version = 1
ON CONFLICT (flow_def_id, step_no) DO NOTHING;
INSERT INTO approval_flow_step (flow_def_id, step_no, role_code, name, required, auto_approve_when, timeout_hours)
SELECT id, 2, 'PMO_ADMIN', 'PMO 审核', TRUE, FALSE, 48 FROM approval_flow_def WHERE code = 'STANDARD_INITIATION' AND version = 1
ON CONFLICT (flow_def_id, step_no) DO NOTHING;
INSERT INTO approval_flow_step (flow_def_id, step_no, role_code, name, required, auto_approve_when, timeout_hours)
SELECT id, 3, 'EXEC', '执行层批准', TRUE, FALSE, 72 FROM approval_flow_def WHERE code = 'STANDARD_INITIATION' AND version = 1
ON CONFLICT (flow_def_id, step_no) DO NOTHING;

-- 立项快速
INSERT INTO approval_flow_step (flow_def_id, step_no, role_code, name, required, auto_approve_when, timeout_hours)
SELECT id, 1, 'DEPT_LEAD', '部门负责人审核 (快速)', TRUE, FALSE, 24 FROM approval_flow_def WHERE code = 'SIMPLE_INITIATION' AND version = 1
ON CONFLICT (flow_def_id, step_no) DO NOTHING;

-- 工时审批
INSERT INTO approval_flow_step (flow_def_id, step_no, role_code, name, required, auto_approve_when, timeout_hours)
SELECT id, 1, 'PMO_ADMIN', 'PMO 审核', TRUE, FALSE, 24 FROM approval_flow_def WHERE code = 'STANDARD_TIMESHEET' AND version = 1
ON CONFLICT (flow_def_id, step_no) DO NOTHING;