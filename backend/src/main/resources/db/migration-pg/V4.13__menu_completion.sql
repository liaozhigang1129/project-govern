-- ============================================================
-- V4.13 补齐缺失菜单 + 资源管道/商机漏斗/多维成本看板/资源分配矩阵 (PG 版)
-- 2026-06-15: 经审计发现以下 router 已存在但 sys_menu 没登记:
--   - /resource-pipeline          资源管道  (P1-1)
--   - /opportunity-funnel         商机漏斗  (P1-2)
--   - /cost/dashboard             多维成本看板
--   - /projects/:id/wbs           WBS 工作分解 (项目子页, 不单独授权)
--   - /projects/:id/assignments   资源分配矩阵 (项目子页, 不单独授权)
-- 另补:
--   - RESOURCE_PIPELINE_MGMT 父目录 (与 RISK_MGMT/TIMESHEET_MGMT 平级)
--   - OPPORTUNITY_FUNNEL_MGMT 父目录
--   - COST_MGMT 父目录 (含 COST_USER_MONTH + COST_DASHBOARD)
-- ============================================================

-- ① 新增顶层父目录
INSERT INTO sys_menu (code, name, parent_id, path, icon, sort_order, menu_type, enabled, builtin) VALUES
  ('RESOURCE_MGMT',  '资源管理',  NULL, NULL,                    'Box',         95, 'DIR',  TRUE, TRUE),
  ('OPPORTUNITY_MGMT','商机漏斗',  NULL, '/opportunity-funnel',   'TrendCharts', 85, 'DIR',  TRUE, TRUE),
  ('COST_MGMT',      '成本中心',  NULL, NULL,                    'Money',       82, 'DIR',  TRUE, TRUE)
ON CONFLICT (code) DO NOTHING;

-- ② 新增独立菜单项
INSERT INTO sys_menu (code, name, parent_id, path, icon, sort_order, menu_type, enabled, builtin)
SELECT v.c_code, v.c_name, p.id, v.c_path, v.c_icon, v.c_sort, v.c_type, TRUE, TRUE
FROM (VALUES
  -- 资源管道: 顶级独立菜单
  ('RESOURCE_PIPELINE', '资源管道', NULL,           '/resource-pipeline', 'Box',        96, 'PAGE'),
  -- 成本中心子菜单: 多维成本看板 (COST_USER_MONTH 已存在, 改成子菜单)
  ('COST_DASHBOARD',    '多维成本看板', 'COST_MGMT',  '/cost/dashboard',    'DataBoard',  20, 'PAGE')
) AS v(c_code, c_name, c_parent, c_path, c_icon, c_sort, c_type)
LEFT JOIN sys_menu p ON p.code = v.c_parent
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE code = v.c_code);

-- ③ 把 COST_USER_MONTH 改成 COST_MGMT 的子菜单 (原是顶级, 现在归入"成本中心")
UPDATE sys_menu SET parent_id = (SELECT id FROM sys_menu WHERE code='COST_MGMT'),
                    sort_order = 10,
                    menu_type = 'PAGE'
WHERE code = 'COST_USER_MONTH';

-- ④ 默认授权: PMO_ADMIN/ADMIN 自动获得所有新菜单
INSERT INTO role_menu (role_id, menu_id, granted_by)
SELECT r.id, m.id, 1
FROM role r, sys_menu m
WHERE r.code IN ('PMO_ADMIN', 'ADMIN')
  AND m.code IN ('RESOURCE_MGMT','RESOURCE_PIPELINE','OPPORTUNITY_MGMT','OPPORTUNITY_FUNNEL','COST_MGMT','COST_DASHBOARD')
ON CONFLICT DO NOTHING;

-- ⑤ PM 默认授权: 资源管道 + 商机漏斗
INSERT INTO role_menu (role_id, menu_id, granted_by)
SELECT r.id, m.id, 1
FROM role r, sys_menu m
WHERE r.code = 'PM'
  AND m.code IN ('RESOURCE_MGMT','RESOURCE_PIPELINE','OPPORTUNITY_MGMT','OPPORTUNITY_FUNNEL','COST_MGMT','COST_DASHBOARD')
ON CONFLICT DO NOTHING;

-- ⑥ DEPT_LEAD 默认授权 (除"成本中心"给, 其余都给)
INSERT INTO role_menu (role_id, menu_id, granted_by)
SELECT r.id, m.id, 1
FROM role r, sys_menu m
WHERE r.code = 'DEPT_LEAD'
  AND m.code IN ('RESOURCE_MGMT','RESOURCE_PIPELINE','OPPORTUNITY_MGMT','OPPORTUNITY_FUNNEL','COST_MGMT','COST_DASHBOARD')
ON CONFLICT DO NOTHING;

-- ⑦ EXEC 分管副总: 给资源管道 + 商机漏斗 + 成本中心
INSERT INTO role_menu (role_id, menu_id, granted_by)
SELECT r.id, m.id, 1
FROM role r, sys_menu m
WHERE r.code = 'EXEC'
  AND m.code IN ('RESOURCE_MGMT','RESOURCE_PIPELINE','OPPORTUNITY_MGMT','OPPORTUNITY_FUNNEL','COST_MGMT','COST_DASHBOARD')
ON CONFLICT DO NOTHING;

-- ⑧ SR/AR/FR/DEV/TEST: 给资源管道 (售前/客户经理需要看人力配置)
INSERT INTO role_menu (role_id, menu_id, granted_by)
SELECT r.id, m.id, 1
FROM role r, sys_menu m
WHERE r.code IN ('SR','AR','FR')
  AND m.code IN ('RESOURCE_MGMT','RESOURCE_PIPELINE','OPPORTUNITY_FUNNEL')
ON CONFLICT DO NOTHING;

-- ⑨ VIEWER 只读访客: 只给看基础菜单 (资源管道+成本中心给只读权限)
INSERT INTO role_menu (role_id, menu_id, granted_by)
SELECT r.id, m.id, 1
FROM role r, sys_menu m
WHERE r.code = 'VIEWER'
  AND m.code IN ('DASHBOARD','PROJECT_LIST','INITIATION_LIST','RISK_LIST','RISK_MATRIX','RISK_HEALTH','RESOURCE_PIPELINE','COST_USER_MONTH')
ON CONFLICT DO NOTHING;