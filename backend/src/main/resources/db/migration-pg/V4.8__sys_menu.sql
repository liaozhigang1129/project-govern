-- ============================================================
-- V4.8 菜单管理 + 角色菜单授权 (PG 版)
-- 与 MySQL 版 V4.8__sys_menu.sql 同构
-- ============================================================

-- ① sys_menu 表
CREATE TABLE IF NOT EXISTS sys_menu (
    id              BIGSERIAL    PRIMARY KEY,
    code            VARCHAR(64)  NOT NULL UNIQUE,
    name            VARCHAR(64)  NOT NULL,
    parent_id       BIGINT       NULL REFERENCES sys_menu(id) ON DELETE CASCADE,
    path            VARCHAR(128) NULL,
    icon            VARCHAR(32)  NULL,
    sort_order      INT          NOT NULL DEFAULT 100,
    menu_type       VARCHAR(16)  NOT NULL DEFAULT 'PAGE',
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    builtin         BOOLEAN      NOT NULL DEFAULT FALSE,
    description     VARCHAR(256) NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_menu_parent  ON sys_menu(parent_id);
CREATE INDEX IF NOT EXISTS idx_menu_enabled ON sys_menu(enabled);
CREATE INDEX IF NOT EXISTS idx_menu_sort    ON sys_menu(sort_order);

-- ② role_menu 表
CREATE TABLE IF NOT EXISTS role_menu (
    role_id     BIGINT       NOT NULL REFERENCES role(id)     ON DELETE CASCADE,
    menu_id     BIGINT       NOT NULL REFERENCES sys_menu(id)  ON DELETE CASCADE,
    granted_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    granted_by  BIGINT       REFERENCES app_user(id),
    PRIMARY KEY (role_id, menu_id)
);
CREATE INDEX IF NOT EXISTS idx_role_menu_role ON role_menu(role_id);
CREATE INDEX IF NOT EXISTS idx_role_menu_menu ON role_menu(menu_id);

-- ③ seed: 顶层菜单
INSERT INTO sys_menu (code, name, parent_id, path, icon, sort_order, menu_type, enabled, builtin) VALUES
  ('DASHBOARD',           'Dashboard',       NULL, '/',                       'House',          10, 'PAGE', TRUE, TRUE),
  ('PROJECT_LIST',        '项目',            NULL, '/projects',               'List',           20, 'PAGE', TRUE, TRUE),
  ('INITIATION_LIST',     '立项审批',        NULL, '/initiations',            'Setting',        30, 'PAGE', TRUE, TRUE),
  ('TIMESHEET_MGMT',      '工时管理',        NULL, NULL,                       'Calendar',       40, 'DIR',  TRUE, TRUE),
  ('GANTT',               '甘特图',          NULL, '/gantt',                  'Calendar',       50, 'PAGE', TRUE, TRUE),
  ('MILESTONE_ANALYSIS',  '里程碑分析',      NULL, '/milestones/analysis',    'Flag',           60, 'PAGE', TRUE, TRUE),
  ('MILESTONE_AI',        'AI 里程碑预警',   NULL, '/milestones/ai-advisor',  'MagicStick',     70, 'PAGE', TRUE, TRUE),
  ('COST_USER_MONTH',     '工时成本核算',    NULL, '/cost/user-month',        'Histogram',      80, 'PAGE', TRUE, TRUE),
  ('RISK_MGMT',           '风险管理',        NULL, NULL,                       'Warning',        90, 'DIR',  TRUE, TRUE),
  ('IM_BINDING',          'IM 绑定',         NULL, '/im-bindings',            'ChatDotRound',  100, 'PAGE', TRUE, TRUE),
  ('IM_QUIET_HOURS',      '勿扰时段',        NULL, '/im-quiet-hours',         'Moon',          110, 'PAGE', TRUE, TRUE),
  ('SYSTEM_MGMT',         '系统管理',        NULL, NULL,                       'Tools',         120, 'DIR',  TRUE, TRUE)
ON CONFLICT (code) DO NOTHING;

-- 子菜单
INSERT INTO sys_menu (code, name, parent_id, path, icon, sort_order, menu_type, enabled, builtin)
SELECT c_code, c_name, p.id, c_path, c_icon, c_sort, 'PAGE', TRUE, TRUE FROM (
  SELECT 'TIMESHEET'         AS c_code, '工时周报'   AS c_name, 'TIMESHEET_MGMT' AS c_parent, '/timesheets'           AS c_path, 'Calendar'      AS c_icon, 10 AS c_sort UNION ALL
  SELECT 'TIMESHEET_APPROVE','工时审批',     'TIMESHEET_MGMT',           '/timesheets/approvals', 'Check',         20 UNION ALL
  SELECT 'WORKLOAD',         '人员负载',     'TIMESHEET_MGMT',           '/workload',             'DataLine',      30 UNION ALL
  SELECT 'HOURLY_RATE',      '工时费率',     'TIMESHEET_MGMT',           '/admin/hourly-rates',   'Money',         40 UNION ALL
  SELECT 'RISK_LIST',        '风险列表',     'RISK_MGMT',                '/risks',                'List',          10 UNION ALL
  SELECT 'RISK_MATRIX',      '风险矩阵',     'RISK_MGMT',                '/risks/matrix',         'Histogram',     20 UNION ALL
  SELECT 'RISK_HEALTH',      '健康度看板',   'RISK_MGMT',                '/risks/health',         'DataLine',      30 UNION ALL
  SELECT 'USER_MGMT',        '用户管理',     'SYSTEM_MGMT',              '/admin/users',          'User',          10 UNION ALL
  SELECT 'ROLE_MGMT',        '角色管理',     'SYSTEM_MGMT',              '/admin/roles',          'UserFilled',    20 UNION ALL
  SELECT 'DEPT_MGMT',        '部门管理',     'SYSTEM_MGMT',              '/admin/departments',    'OfficeBuilding',30 UNION ALL
  SELECT 'SYS_CONFIG',       '系统参数',     'SYSTEM_MGMT',              '/admin/system-config',  'Tools',         40 UNION ALL
  SELECT 'MENU_MGMT',        '菜单管理',     'SYSTEM_MGMT',              '/admin/menus',          'Menu',          50 UNION ALL
  SELECT 'AUDIT_LOG',        '审计日志',     'SYSTEM_MGMT',              '/audit-logs',           'Document',      60
) child
JOIN sys_menu p ON p.code = child.c_parent
ON CONFLICT (code) DO NOTHING;

-- ④ 默认授权
INSERT INTO role_menu (role_id, menu_id, granted_by)
SELECT r.id, m.id, 1
FROM role r, sys_menu m
WHERE r.enabled = TRUE AND m.enabled = TRUE
  AND r.code IN ('PMO_ADMIN', 'ADMIN')
ON CONFLICT DO NOTHING;

INSERT INTO role_menu (role_id, menu_id, granted_by)
SELECT r.id, m.id, 1
FROM role r, sys_menu m
WHERE r.code = 'VIEWER'
  AND m.code IN ('DASHBOARD','PROJECT_LIST','INITIATION_LIST','RISK_LIST','RISK_MATRIX','RISK_HEALTH')
ON CONFLICT DO NOTHING;

INSERT INTO role_menu (role_id, menu_id, granted_by)
SELECT r.id, m.id, 1
FROM role r, sys_menu m
WHERE r.code = 'PM'
  AND m.code IN ('DASHBOARD','PROJECT_LIST','INITIATION_LIST',
                 'TIMESHEET_MGMT','TIMESHEET','WORKLOAD',
                 'GANTT','MILESTONE_ANALYSIS','MILESTONE_AI',
                 'COST_USER_MONTH',
                 'RISK_MGMT','RISK_LIST','RISK_MATRIX','RISK_HEALTH',
                 'IM_BINDING','IM_QUIET_HOURS')
ON CONFLICT DO NOTHING;

INSERT INTO role_menu (role_id, menu_id, granted_by)
SELECT r.id, m.id, 1
FROM role r, sys_menu m
WHERE r.code = 'DEPT_LEAD'
  AND m.code IN ('DASHBOARD','PROJECT_LIST','INITIATION_LIST',
                 'TIMESHEET_MGMT','TIMESHEET','TIMESHEET_APPROVE','WORKLOAD','HOURLY_RATE',
                 'GANTT','MILESTONE_ANALYSIS','MILESTONE_AI',
                 'COST_USER_MONTH',
                 'RISK_MGMT','RISK_LIST','RISK_MATRIX','RISK_HEALTH',
                 'IM_BINDING','IM_QUIET_HOURS')
ON CONFLICT DO NOTHING;

INSERT INTO role_menu (role_id, menu_id, granted_by)
SELECT r.id, m.id, 1
FROM role r, sys_menu m
WHERE r.code = 'EXEC'
  AND m.enabled = TRUE
  AND m.code NOT IN ('SYSTEM_MGMT','USER_MGMT','ROLE_MGMT','DEPT_MGMT','SYS_CONFIG','MENU_MGMT','AUDIT_LOG')
ON CONFLICT DO NOTHING;