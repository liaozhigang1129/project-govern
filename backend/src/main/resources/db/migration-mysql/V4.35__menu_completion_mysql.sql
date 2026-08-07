-- ============================================================
-- V4.35 补齐 MySQL 侧缺失的菜单登记 + 角色授权 (MySQL 版)
--
-- 背景 / 根因:
--   1) migration-pg 下有 V4.13__menu_completion.sql, 但 MySQL 侧从未补写,
--      而 docker compose 跑的是 SPRING_PROFILES_ACTIVE=mysql
--      → 资源管理/成本中心/商机漏斗等菜单在 MySQL 库里从来没登记过.
--   2) V4.30(dingtalk_attendance) / V4.33(dingtalk_attendance_daily) 只建业务表,
--      漏了 sys_menu 注册 → 「考勤同步」「钉钉请休假」页面有路由有组件, 但菜单不显示.
--   3) App.vue:139 的过滤是双闸门: 角色 + /api/role-menus/mine 返回的 code 白名单.
--      code 不在 sys_menu → 菜单项被静默隐藏 (父目录 code 缺失时整个二级分组消失).
--
-- 本迁移补齐前端 App.vue 有、sys_menu 没有的 6 个 code:
--   DINGTALK_ATTENDANCE  考勤同步        (工时管理 子项)
--   DINGTALK_LEAVE       钉钉请休假      (工时管理 子项)
--   DINGTALK_SYNC_LOG    同步日志        (系统管理 子项)
--   COST_MGMT            成本中心        (顶层 DIR)
--   COST_DASHBOARD       多维成本看板    (成本中心 子项)
--   RESOURCE_MGMT        资源管理        (顶层 DIR)
--
-- 并做 2 处结构归位 + 1 处 code 统一:
--   COST_USER_MONTH   顶层 PAGE → COST_MGMT 子项
--   RESOURCE_PIPELINE 顶层 PAGE → RESOURCE_MGMT 子项
--   OPPORTUNITY: DB 侧保留既有 OPPORTUNITY_FUNNEL (已有授权数据),
--                前端 App.vue 的 code 'OPPORTUNITY_MGMT' 同步改为 'OPPORTUNITY_FUNNEL'
--                → 不新增 OPPORTUNITY_MGMT, 避免出现两个「商机漏斗」入口
--
-- 幂等性: 全部 INSERT 走 ON DUPLICATE KEY UPDATE / INSERT IGNORE, 可重复执行.
-- MySQL 陷阱: UPDATE 的子查询不能直接引用被更新的同一张表 (error 1093),
--             故所有 (SELECT id FROM sys_menu WHERE ...) 都多包一层派生表.
-- ============================================================

-- ------------------------------------------------------------
-- ① 新增顶层父目录 (DIR)
--    sort_order 对齐 App.vue menuItems 的视觉顺序:
--      ... 80 里程碑 → 82 成本中心 → 91 商机漏斗 → 95 资源管理 → 100 IM 绑定 ...
-- ------------------------------------------------------------
INSERT INTO sys_menu (code, name, parent_id, path, icon, sort_order, menu_type, enabled, builtin) VALUES
  ('COST_MGMT',     '成本中心', NULL, NULL, 'Money', 82, 'DIR', 1, 1),
  ('RESOURCE_MGMT', '资源管理', NULL, NULL, 'Box',   95, 'DIR', 1, 1)
ON DUPLICATE KEY UPDATE code = code;

-- ------------------------------------------------------------
-- ② 新增子菜单 (PAGE) — 挂到各自父目录下
--    父目录必须已存在, 故与 ① 分开两条语句 (同 V4.8 的写法)
-- ------------------------------------------------------------
INSERT INTO sys_menu (code, name, parent_id, path, icon, sort_order, menu_type, enabled, builtin)
SELECT t.c_code, t.c_name, p.id, t.c_path, t.c_icon, t.c_sort, 'PAGE', 1, 1
FROM (
  -- 工时管理 (TIMESHEET_MGMT): 现有子项 10/20/30/40, 考勤类接在 50/60
  SELECT 'DINGTALK_ATTENDANCE' AS c_code, '考勤同步'     AS c_name, 'TIMESHEET_MGMT' AS c_parent,
         '/timesheets/attendance'      AS c_path, 'Timer'     AS c_icon, 50 AS c_sort UNION ALL
  SELECT 'DINGTALK_LEAVE',      '钉钉请休假',  'TIMESHEET_MGMT',
         '/timesheets/dingtalk-leaves', 'Document',  60 UNION ALL
  -- 系统管理 (SYSTEM_MGMT): 现有 10..40 + AUDIT_LOG 60, 同步日志插 50
  --   注: MENU_MGMT 现为 50, 这里用 55 避免与之并列时排序抖动
  SELECT 'DINGTALK_SYNC_LOG',   '同步日志',    'SYSTEM_MGMT',
         '/admin/dingtalk-sync-log',    'Document',  55 UNION ALL
  -- 成本中心 (COST_MGMT): 工时成本核算 10 (由 ③ 归位) + 多维成本看板 20
  SELECT 'COST_DASHBOARD',      '多维成本看板','COST_MGMT',
         '/cost/dashboard',             'DataBoard', 20
) t
JOIN sys_menu p ON p.code = t.c_parent
ON DUPLICATE KEY UPDATE sys_menu.code = sys_menu.code;

-- ------------------------------------------------------------
-- ③ 结构归位: 两个原本挂在顶层的 PAGE 收进新建的父目录
--    多包一层派生表规避 MySQL error 1093
-- ------------------------------------------------------------
-- 工时成本核算 → 成本中心
UPDATE sys_menu
SET parent_id  = (SELECT id FROM (SELECT id FROM sys_menu WHERE code = 'COST_MGMT') AS x),
    sort_order = 10,
    menu_type  = 'PAGE'
WHERE code = 'COST_USER_MONTH';

-- 资源管道 → 资源管理 (顺带把 icon 从 Histogram 对齐成 App.vue 的 Box)
UPDATE sys_menu
SET parent_id  = (SELECT id FROM (SELECT id FROM sys_menu WHERE code = 'RESOURCE_MGMT') AS x),
    sort_order = 10,
    menu_type  = 'PAGE',
    icon       = 'Box'
WHERE code = 'RESOURCE_PIPELINE';

-- 商机漏斗: code 保持 OPPORTUNITY_FUNNEL (前端同步改), 仅对齐 icon 与排序
UPDATE sys_menu
SET icon       = 'TrendCharts',
    sort_order = 91,
    menu_type  = 'PAGE'
WHERE code = 'OPPORTUNITY_FUNNEL';

-- ------------------------------------------------------------
-- ④ 授权: 钉钉三项 (考勤同步 / 钉钉请休假 / 同步日志)
--    App.vue 标注 roles: ['PMO_ADMIN','ADMIN'];
--    注意 role 表中并不存在 ADMIN 角色, 实际只落到 PMO_ADMIN.
--    这里用 r.code IN (...) 写全, ADMIN 将来补建时自动生效.
-- ------------------------------------------------------------
INSERT IGNORE INTO role_menu (role_id, menu_id, granted_by)
SELECT r.id, m.id, 1
FROM role r, sys_menu m
WHERE r.enabled = 1
  AND r.code IN ('PMO_ADMIN', 'ADMIN')
  AND m.code IN ('DINGTALK_ATTENDANCE', 'DINGTALK_LEAVE', 'DINGTALK_SYNC_LOG');

-- ------------------------------------------------------------
-- ⑤ 授权: 成本中心 (COST_MGMT + COST_DASHBOARD)
--    对齐 App.vue: roles ['PMO_ADMIN','ADMIN','EXEC','PM','DEPT_LEAD']
--    (COST_USER_MONTH 的既有授权不动, 已是 DEPT_LEAD/EXEC/PM/PMO_ADMIN)
-- ------------------------------------------------------------
INSERT IGNORE INTO role_menu (role_id, menu_id, granted_by)
SELECT r.id, m.id, 1
FROM role r, sys_menu m
WHERE r.enabled = 1
  AND r.code IN ('PMO_ADMIN', 'ADMIN', 'EXEC', 'PM', 'DEPT_LEAD')
  AND m.code IN ('COST_MGMT', 'COST_DASHBOARD');

-- ------------------------------------------------------------
-- ⑥ 授权: 商机漏斗 — 按 App.vue roles 补齐缺的 SR/FR/PM/DEPT_LEAD
--    (DB 现状只有 AR/EXEC/PMO_ADMIN)
-- ------------------------------------------------------------
INSERT IGNORE INTO role_menu (role_id, menu_id, granted_by)
SELECT r.id, m.id, 1
FROM role r, sys_menu m
WHERE r.enabled = 1
  AND r.code IN ('PMO_ADMIN', 'ADMIN', 'EXEC', 'DEPT_LEAD', 'PM', 'SR', 'AR', 'FR')
  AND m.code = 'OPPORTUNITY_FUNNEL';

-- ------------------------------------------------------------
-- ⑦ 父目录授权继承 (关键)
--    App.vue visibleItem(): 父项显示 = 「父 code 在白名单」AND「任一子项可见」
--    → 只要某角色拿到了任一子项, 就必须同时拿到父目录, 否则整组静默消失.
--    这里对全部 DIR 统一补齐, 一次性修掉此类隐患 (不止本次新增的两个目录).
-- ------------------------------------------------------------
INSERT IGNORE INTO role_menu (role_id, menu_id, granted_by)
SELECT DISTINCT rm.role_id, parent.id, 1
FROM role_menu rm
JOIN sys_menu child  ON child.id = rm.menu_id
JOIN sys_menu parent ON parent.id = child.parent_id
WHERE parent.enabled = 1;
