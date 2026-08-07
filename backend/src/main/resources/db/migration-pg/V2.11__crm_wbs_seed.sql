-- ============================================================
-- V2.11 客户CRM 系统 (project_id=3) WBS / BAC 初始化
-- 5 大阶段, 8 条 SUMMARY + 15 条 EXECUTION
-- 里程碑由 project_initiation 同步,此处不重插
-- ============================================================

-- 1. 补 BAC
UPDATE project SET bac = 1500000.00 WHERE id = 3 AND bac IS NULL;

-- 2. 删旧 WBS (项目 3 除 id 8/9/10 之外没有,所以只清空重做,先把原 3 条保留作为子项...
-- 实际策略: 整体清空 project_id=3 的 WBS,重做
DELETE FROM wbs_task WHERE project_id = 3;
ALTER SEQUENCE wbs_task_id_seq RESTART WITH 1;  -- 让 id 干净, 可选

-- 3. 插入 8 条 SUMMARY (5阶段 + 1协调 + 1客户验收 + 1质保) — 作为父节点
INSERT INTO wbs_task (project_id, wbs_code, name, task_type, status, plan_hours, actual_hours, progress_pct, weight) VALUES
(3, '1', '项目规划与需求',   'SUMMARY', 'IN_PROGRESS', 320.00,  160.00, 50, 3),
(3, '2', '系统设计与架构',   'SUMMARY', 'IN_PROGRESS', 240.00,   48.00, 20, 2),
(3, '3', '后端开发',         'SUMMARY', 'NOT_STARTED', 960.00,    0.00,  0, 5),
(3, '4', '前端开发',         'SUMMARY', 'NOT_STARTED', 720.00,    0.00,  0, 4),
(3, '5', '集成测试',         'SUMMARY', 'NOT_STARTED', 400.00,    0.00,  0, 4),
(3, '6', 'UAT 用户验收',     'SUMMARY', 'NOT_STARTED', 200.00,    0.00,  0, 3),
(3, '7', '上线与质保',       'SUMMARY', 'NOT_STARTED', 160.00,    0.00,  0, 2);

-- 4. 插入 15 条 EXECUTION (叶子) — 分布到 7 个父阶段
-- 阶段 1: 项目规划与需求 (4条)
INSERT INTO wbs_task (project_id, parent_id, wbs_code, name, task_type, status, owner_user_id, plan_start_date, plan_end_date, actual_start_date, actual_end_date, plan_hours, actual_hours, progress_pct, weight) VALUES
(3, (SELECT id FROM wbs_task WHERE project_id=3 AND wbs_code='1'), '1.1', '需求调研与业务建模', 'EXECUTION', 'COMPLETED', 2, DATE '2026-05-15', DATE '2026-05-19', DATE '2026-05-15', DATE '2026-05-19', 120.00, 120.00, 100, 3),
(3, (SELECT id FROM wbs_task WHERE project_id=3 AND wbs_code='1'), '1.2', '需求评审与确认',     'EXECUTION', 'COMPLETED', 2, DATE '2026-05-20', DATE '2026-05-22', DATE '2026-05-20', DATE '2026-05-22',  80.00,  40.00,  50, 2),
(3, (SELECT id FROM wbs_task WHERE project_id=3 AND wbs_code='1'), '1.3', '项目立项与启动',     'EXECUTION', 'NOT_STARTED', 2, DATE '2026-05-25', DATE '2026-05-26', NULL, NULL, 40.00, 0.00, 0, 1),
(3, (SELECT id FROM wbs_task WHERE project_id=3 AND wbs_code='1'), '1.4', '需求基线冻结 (V1)',  'EXECUTION', 'NOT_STARTED', 2, DATE '2026-05-28', DATE '2026-05-30', NULL, NULL, 80.00, 0.00, 0, 3);

-- 阶段 2: 系统设计与架构 (3条)
INSERT INTO wbs_task (project_id, parent_id, wbs_code, name, task_type, status, owner_user_id, plan_start_date, plan_end_date, actual_start_date, plan_hours, actual_hours, progress_pct, weight, is_critical) VALUES
(3, (SELECT id FROM wbs_task WHERE project_id=3 AND wbs_code='2'), '2.1', '技术选型与架构设计', 'EXECUTION', 'IN_PROGRESS', 3, DATE '2026-05-20', DATE '2026-05-30', DATE '2026-05-20', 160.00, 48.00, 30, 3, true),
(3, (SELECT id FROM wbs_task WHERE project_id=3 AND wbs_code='2'), '2.2', '数据库设计',         'EXECUTION', 'IN_PROGRESS', 3, DATE '2026-05-25', DATE '2026-05-30', DATE '2026-05-25',  48.00,  0.00,  0, 2, true),
(3, (SELECT id FROM wbs_task WHERE project_id=3 AND wbs_code='2'), '2.3', 'API 契约设计',       'EXECUTION', 'NOT_STARTED', 3, DATE '2026-05-28', DATE '2026-05-30', NULL, 32.00, 0.00, 0, 2, false);

-- 阶段 3: 后端开发 (4条)
INSERT INTO wbs_task (project_id, parent_id, wbs_code, name, task_type, status, owner_user_id, plan_start_date, plan_end_date, plan_hours, actual_hours, progress_pct, weight, is_critical) VALUES
(3, (SELECT id FROM wbs_task WHERE project_id=3 AND wbs_code='3'), '3.1', '客户主数据模块',     'EXECUTION', 'NOT_STARTED', 2, DATE '2026-06-01', DATE '2026-06-10', 240.00, 0.00, 0, 5, true),
(3, (SELECT id FROM wbs_task WHERE project_id=3 AND wbs_code='3'), '3.2', '销售线索与商机',     'EXECUTION', 'NOT_STARTED', 2, DATE '2026-06-08', DATE '2026-06-18', 280.00, 0.00, 0, 4, true),
(3, (SELECT id FROM wbs_task WHERE project_id=3 AND wbs_code='3'), '3.3', '合同与回款',         'EXECUTION', 'NOT_STARTED', 2, DATE '2026-06-15', DATE '2026-06-25', 240.00, 0.00, 0, 3, false),
(3, (SELECT id FROM wbs_task WHERE project_id=3 AND wbs_code='3'), '3.4', '报表与数据服务',     'EXECUTION', 'NOT_STARTED', 2, DATE '2026-06-22', DATE '2026-06-30', 200.00, 0.00, 0, 3, false);

-- 阶段 4: 前端开发 (2条)
INSERT INTO wbs_task (project_id, parent_id, wbs_code, name, task_type, status, owner_user_id, plan_start_date, plan_end_date, plan_hours, actual_hours, progress_pct, weight, is_critical) VALUES
(3, (SELECT id FROM wbs_task WHERE project_id=3 AND wbs_code='4'), '4.1', '门户 + 销售模块 H5',  'EXECUTION', 'NOT_STARTED', 3, DATE '2026-06-15', DATE '2026-07-10', 400.00, 0.00, 0, 4, true),
(3, (SELECT id FROM wbs_task WHERE project_id=3 AND wbs_code='4'), '4.2', '数据分析 + 看板',     'EXECUTION', 'NOT_STARTED', 3, DATE '2026-07-01', DATE '2026-07-15', 320.00, 0.00, 0, 3, false);

-- 阶段 5: 集成测试 (1条 — 含联调/性能/安全)
INSERT INTO wbs_task (project_id, parent_id, wbs_code, name, task_type, status, owner_user_id, plan_start_date, plan_end_date, plan_hours, actual_hours, progress_pct, weight, is_critical) VALUES
(3, (SELECT id FROM wbs_task WHERE project_id=3 AND wbs_code='5'), '5.1', '系统集成 + 性能 + 安全测试', 'EXECUTION', 'NOT_STARTED', 2, DATE '2026-07-15', DATE '2026-08-05', 400.00, 0.00, 0, 4, true);

-- 阶段 6: UAT (1条)
INSERT INTO wbs_task (project_id, parent_id, wbs_code, name, task_type, status, owner_user_id, plan_start_date, plan_end_date, plan_hours, actual_hours, progress_pct, weight) VALUES
(3, (SELECT id FROM wbs_task WHERE project_id=3 AND wbs_code='6'), '6.1', '客户 UAT 验收',  'EXECUTION', 'NOT_STARTED', 4, DATE '2026-08-05', DATE '2026-08-20', 200.00, 0.00, 0, 3);

-- 阶段 7: 上线与质保 (1条 — 含演练/灰度/质保期)
INSERT INTO wbs_task (project_id, parent_id, wbs_code, name, task_type, status, owner_user_id, plan_start_date, plan_end_date, plan_hours, actual_hours, progress_pct, weight) VALUES
(3, (SELECT id FROM wbs_task WHERE project_id=3 AND wbs_code='7'), '7.1', '上线演练 + 灰度 + 质保', 'EXECUTION', 'NOT_STARTED', 2, DATE '2026-08-20', DATE '2026-08-30', 160.00, 0.00, 0, 2);
