-- ============================================================
-- V6.1 审批流程种子数据 (MySQL 版)
-- 立项审批 / 工时审批 流程定义
-- ============================================================

-- 1) 立项审批: DEPT_LEAD → PMO_ADMIN → EXEC
INSERT IGNORE INTO approval_flow_def (kind, code, name, version, enabled, description) VALUES
  ('init', 'STANDARD_INITIATION', '立项标准三级审批', 1, TRUE,
   '立项审批: 部门负责人 → PMO → 执行层 (默认三级)'),
  ('init', 'SIMPLE_INITIATION', '立项快速审批 (金额 < ¥100k)', 1, TRUE,
   '立项审批: 仅需部门负责人 (小额快速通道)');

INSERT IGNORE INTO approval_flow_step (flow_def_id, step_no, role_code, name, required, auto_approve_when, skip_when, timeout_hours)
SELECT id, 1, 'DEPT_LEAD', '部门负责人审核', TRUE, FALSE, NULL, 48 FROM approval_flow_def WHERE code = 'STANDARD_INITIATION' AND version = 1;
INSERT IGNORE INTO approval_flow_step (flow_def_id, step_no, role_code, name, required, auto_approve_when, skip_when, timeout_hours)
SELECT id, 2, 'PMO_ADMIN', 'PMO 审核', TRUE, FALSE, NULL, 48 FROM approval_flow_def WHERE code = 'STANDARD_INITIATION' AND version = 1;
INSERT IGNORE INTO approval_flow_step (flow_def_id, step_no, role_code, name, required, auto_approve_when, skip_when, timeout_hours)
SELECT id, 3, 'EXEC', '执行层批准', TRUE, FALSE, NULL, 72 FROM approval_flow_def WHERE code = 'STANDARD_INITIATION' AND version = 1;

INSERT IGNORE INTO approval_flow_step (flow_def_id, step_no, role_code, name, required, auto_approve_when, skip_when, timeout_hours)
SELECT id, 1, 'DEPT_LEAD', '部门负责人审核 (快速)', TRUE, FALSE, NULL, 24 FROM approval_flow_def WHERE code = 'SIMPLE_INITIATION' AND version = 1;

-- 2) 工时审批: PMO_ADMIN (单级)
INSERT IGNORE INTO approval_flow_def (kind, code, name, version, enabled, description) VALUES
  ('timesheet', 'STANDARD_TIMESHEET', '工时审批 (单级)', 1, TRUE,
   '工时提交后由 PMO 审核 (单级审批)');

INSERT IGNORE INTO approval_flow_step (flow_def_id, step_no, role_code, name, required, auto_approve_when, skip_when, timeout_hours)
SELECT id, 1, 'PMO_ADMIN', 'PMO 审核', TRUE, FALSE, NULL, 24 FROM approval_flow_def WHERE code = 'STANDARD_TIMESHEET' AND version = 1;