-- ============================================================
-- V4.8 菜单管理 + 角色菜单授权 (MySQL 版)
-- 设计:
--   sys_menu          所有菜单节点(顶层目录 + 子菜单), 树形
--   role_menu         角色 × 菜单 的授权关系
-- 一期方案 (够用, 后续可扩):
--   - 菜单按"角色代码"匹配授权 (前端根据当前用户角色过滤可见项)
--   - 先把现有 App.vue 静态菜单整体迁移进 sys_menu, 老的 static 配置做兼容
-- ============================================================

-- ① sys_menu 表 — 菜单定义(树形)
CREATE TABLE IF NOT EXISTS sys_menu (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(64)  NOT NULL UNIQUE,           -- 英文唯一键: DASHBOARD / PROJECT_LIST / TIMESHEET ...
    name            VARCHAR(64)  NOT NULL,                   -- 中文显示名
    parent_id       BIGINT       NULL,                       -- 顶层目录为 NULL
    path            VARCHAR(128) NULL,                       -- 前端路由路径(目录可空)
    icon            VARCHAR(32)  NULL,                       -- Element Plus icon 名
    sort_order      INT          NOT NULL DEFAULT 100,       -- 同级排序
    menu_type       VARCHAR(16)  NOT NULL DEFAULT 'PAGE',    -- DIR / PAGE
    enabled         TINYINT(1)   NOT NULL DEFAULT 1,
    builtin         TINYINT(1)   NOT NULL DEFAULT 0,         -- seed 数据标记
    description     VARCHAR(256) NULL,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_menu_parent FOREIGN KEY (parent_id) REFERENCES sys_menu(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统菜单定义(树形)';

CREATE INDEX idx_menu_parent ON sys_menu(parent_id);
CREATE INDEX idx_menu_enabled ON sys_menu(enabled);
CREATE INDEX idx_menu_sort ON sys_menu(sort_order);

-- ② role_menu 表 — 角色 × 菜单多对多授权
CREATE TABLE IF NOT EXISTS role_menu (
    role_id     BIGINT       NOT NULL,
    menu_id     BIGINT       NOT NULL,
    granted_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    granted_by  BIGINT       NULL,
    PRIMARY KEY (role_id, menu_id),
    KEY idx_role_menu_role (role_id),
    KEY idx_role_menu_menu (menu_id),
    CONSTRAINT fk_role_menu_role FOREIGN KEY (role_id) REFERENCES role(id)     ON DELETE CASCADE,
    CONSTRAINT fk_role_menu_menu FOREIGN KEY (menu_id) REFERENCES sys_menu(id)  ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-菜单授权';

-- ③ seed: 把现有 App.vue 静态菜单迁移到 sys_menu (顶级 + 子菜单)
INSERT INTO sys_menu (code, name, parent_id, path, icon, sort_order, menu_type, enabled, builtin) VALUES
  ('DASHBOARD',           'Dashboard',       NULL, '/',                       'House',          10, 'PAGE', 1, 1),
  ('PROJECT_LIST',        '项目',            NULL, '/projects',               'List',           20, 'PAGE', 1, 1),
  ('INITIATION_LIST',     '立项审批',        NULL, '/initiations',            'Setting',        30, 'PAGE', 1, 1),
  ('TIMESHEET_MGMT',      '工时管理',        NULL, NULL,                       'Calendar',       40, 'DIR',  1, 1),
  ('GANTT',               '甘特图',          NULL, '/gantt',                  'Calendar',       50, 'PAGE', 1, 1),
  ('MILESTONE_ANALYSIS',  '里程碑分析',      NULL, '/milestones/analysis',    'Flag',           60, 'PAGE', 1, 1),
  ('MILESTONE_AI',        'AI 里程碑预警',   NULL, '/milestones/ai-advisor',  'MagicStick',     70, 'PAGE', 1, 1),
  ('COST_USER_MONTH',     '工时成本核算',    NULL, '/cost/user-month',        'Histogram',      80, 'PAGE', 1, 1),
  ('RISK_MGMT',           '风险管理',        NULL, NULL,                       'Warning',        90, 'DIR',  1, 1),
  ('IM_BINDING',          'IM 绑定',         NULL, '/im-bindings',            'ChatDotRound',  100, 'PAGE', 1, 1),
  ('IM_QUIET_HOURS',      '勿扰时段',        NULL, '/im-quiet-hours',         'Moon',          110, 'PAGE', 1, 1),
  ('SYSTEM_MGMT',         '系统管理',        NULL, NULL,                       'Tools',         120, 'DIR',  1, 1)
ON DUPLICATE KEY UPDATE code = code;

-- 子菜单 (用子查询包一层, ON DUPLICATE KEY UPDATE 用 sys_menu.code 限定避免与 t.c_code 同名冲突)
INSERT INTO sys_menu (code, name, parent_id, path, icon, sort_order, menu_type, enabled, builtin)
SELECT t.c_code, t.c_name, p.id, t.c_path, t.c_icon, t.c_sort, 'PAGE', 1, 1
FROM (
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
) t
JOIN sys_menu p ON p.code = t.c_parent
ON DUPLICATE KEY UPDATE sys_menu.code = sys_menu.code;

-- ④ 角色默认授权: 与现有 App.vue visibleItem 行为对齐 (内置角色可见全部菜单)
INSERT IGNORE INTO role_menu (role_id, menu_id, granted_by)
SELECT r.id, m.id, 1
FROM role r, sys_menu m
WHERE r.enabled = 1 AND m.enabled = 1
  AND r.code IN ('PMO_ADMIN', 'ADMIN');              -- PMO_ADMIN / ADMIN 默认全开

-- VIEWER: 基础浏览 (Dashboard / 项目 / 立项 / 风险)
INSERT IGNORE INTO role_menu (role_id, menu_id, granted_by)
SELECT r.id, m.id, 1
FROM role r, sys_menu m
WHERE r.code = 'VIEWER'
  AND m.code IN ('DASHBOARD','PROJECT_LIST','INITIATION_LIST','RISK_LIST','RISK_MATRIX','RISK_HEALTH');

-- PM: 项目管理相关
INSERT IGNORE INTO role_menu (role_id, menu_id, granted_by)
SELECT r.id, m.id, 1
FROM role r, sys_menu m
WHERE r.code = 'PM'
  AND m.code IN ('DASHBOARD','PROJECT_LIST','INITIATION_LIST',
                 'TIMESHEET_MGMT','TIMESHEET','WORKLOAD',
                 'GANTT','MILESTONE_ANALYSIS','MILESTONE_AI',
                 'COST_USER_MONTH',
                 'RISK_MGMT','RISK_LIST','RISK_MATRIX','RISK_HEALTH',
                 'IM_BINDING','IM_QUIET_HOURS');

-- DEPT_LEAD: 部门领导 (含审批)
INSERT IGNORE INTO role_menu (role_id, menu_id, granted_by)
SELECT r.id, m.id, 1
FROM role r, sys_menu m
WHERE r.code = 'DEPT_LEAD'
  AND m.code IN ('DASHBOARD','PROJECT_LIST','INITIATION_LIST',
                 'TIMESHEET_MGMT','TIMESHEET','TIMESHEET_APPROVE','WORKLOAD','HOURLY_RATE',
                 'GANTT','MILESTONE_ANALYSIS','MILESTONE_AI',
                 'COST_USER_MONTH',
                 'RISK_MGMT','RISK_LIST','RISK_MATRIX','RISK_HEALTH',
                 'IM_BINDING','IM_QUIET_HOURS');

-- EXEC: 高管 (全开除系统管理外)
INSERT IGNORE INTO role_menu (role_id, menu_id, granted_by)
SELECT r.id, m.id, 1
FROM role r, sys_menu m
WHERE r.code = 'EXEC'
  AND m.enabled = 1
  AND m.code NOT IN ('SYSTEM_MGMT','USER_MGMT','ROLE_MGMT','DEPT_MGMT','SYS_CONFIG','MENU_MGMT','AUDIT_LOG');