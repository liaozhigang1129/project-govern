-- =============================================================================
-- Seed: 客户CRM系统(P-2025-003, id=3)甘特图样例数据
-- 日期: 2026-06-08
-- 状态: ⚠️  DEPRECATED (2026-06-09)
-- 原因: 此文件假设 id=3 客户CRM 缺失, 实际数据库中:
--       - 5432 库(后端真正连的)已有 18 个项目 64 里程碑, id=3 客户CRM 完整
--       - 此文件 hardcode 的 id IN (64,65,66,67,68) 在不同环境可能撞 e2e fixture
-- 替代: 客户CRM 甘特图问题已由 P2.C 算法修复解决 (GanttService 把 milestone
--       时间窗纳入 autoFrom/autoTo), 不再需要这条 seed
--       见: P2.C-gantt-axis-fix.md
-- 原目的: 校正 project + milestone 时间窗/进度,让甘特图有合理的可视展示
-- 风险: 低(只更新 1 个 project + 5 个 milestone,均带 WHERE 限定)
-- 回滚: 见文末
-- 执行: ⚠️  不建议执行
-- =============================================================================

BEGIN;

-- ---------------------------------------------------------------------------
-- 1) 项目主数据: 把时间窗移到 2026 Q2-Q3,跟现有里程碑(2026-05~2026-07)对齐
-- ---------------------------------------------------------------------------
UPDATE project
   SET plan_start_date  = DATE '2026-05-15',
       plan_end_date    = DATE '2026-08-30',
       actual_start_date = DATE '2026-05-15',
       -- 进度: 5 个里程碑 2 个已完成 → 40%
       progress_pct     = 40,
       -- 健康度改为"关注"(原 RED 严重,但 2 个里程碑已按时完成,降一级)
       health_id        = 2
 WHERE id = 3
   AND deleted = FALSE;

-- ---------------------------------------------------------------------------
-- 2) 里程碑: 微调 plan_date 让 5 个节点均匀分布
--    现状(2026-06-08 探查):
--      64 需求评审  plan 2026-05-17  actual 2026-05-19  COMPLETED
--      65 架构设计  plan 2026-05-22  actual 2026-05-24  COMPLETED
--      66 开发完成  plan 2026-06-01  actual (空)        IN_PROGRESS
--      67 UAT 测试  plan 2026-06-21  actual (空)        PENDING
--      68 正式上线  plan 2026-07-21  actual (空)        PENDING
--    调整:
--      64 实际完成 2026-05-19  ✅ 保留
--      65 实际完成 2026-05-24  ✅ 保留
--      66 计划 2026-06-15(留 2 周开发余量)
--      67 计划 2026-07-15(UAT 2 周)
--      68 计划 2026-08-20(上线 & 收尾)
-- ---------------------------------------------------------------------------

-- 64 需求评审: 补 completed_at
UPDATE milestone
   SET completed_at = TIMESTAMP '2026-05-19 18:00:00+08'
 WHERE id = 64;

-- 65 架构设计: 补 completed_at
UPDATE milestone
   SET completed_at = TIMESTAMP '2026-05-24 18:00:00+08'
 WHERE id = 65;

-- 66 开发完成: 延期到 2026-06-15(原 2026-06-01 偏激进,留 2 周)
UPDATE milestone
   SET plan_date = DATE '2026-06-15'
 WHERE id = 66;

-- 67 UAT 测试: 2026-07-15 (开发完成 + 2 周 UAT)
UPDATE milestone
   SET plan_date = DATE '2026-07-15'
 WHERE id = 67;

-- 68 正式上线: 2026-08-20 (UAT 收尾 + 1 周上线准备)
UPDATE milestone
   SET plan_date = DATE '2026-08-20'
 WHERE id = 68;

-- ---------------------------------------------------------------------------
-- 3) (可选)给现有里程碑加权重,让"开发完成"权重最大,匹配业务实际
--    现状所有 weight=1,改成更具区分度的权重
-- ---------------------------------------------------------------------------
UPDATE milestone SET weight = 1 WHERE id = 64;  -- 需求评审 权 1
UPDATE milestone SET weight = 2 WHERE id = 65;  -- 架构设计 权 2
UPDATE milestone SET weight = 4 WHERE id = 66;  -- 开发完成 权 4(最大)
UPDATE milestone SET weight = 2 WHERE id = 67;  -- UAT 测试 权 2
UPDATE milestone SET weight = 1 WHERE id = 68;  -- 正式上线 权 1

-- ---------------------------------------------------------------------------
-- 4) (可选)更新前 2 个里程碑的备注,体现实际交付
-- ---------------------------------------------------------------------------
UPDATE milestone
   SET remark = '客户方业务部门 8 人参与,完成 32 条需求条目确认',
       deliverable = '《需求规格说明书 V1.0》 + 《需求追溯矩阵》'
 WHERE id = 64;

UPDATE milestone
   SET remark = '微服务架构 6 个服务,MySQL 8.0 + Redis 7,完成 P1 评审',
       deliverable = '《系统架构设计 V1.0》 + 《部署拓扑图》'
 WHERE id = 65;

-- ---------------------------------------------------------------------------
-- 5) (可选)为 Gantt 上里程碑菱形图标更醒目,设个 owner_user_id
--     用 PM 李四(id=3)统一负责;UAT/上线归交付部吴经理(id=4)
-- ---------------------------------------------------------------------------
UPDATE milestone SET owner_user_id = 3 WHERE id IN (64, 65, 66);
UPDATE milestone SET owner_user_id = 4 WHERE id IN (67, 68);

COMMIT;

-- ---------------------------------------------------------------------------
-- 回滚(若需)
-- ---------------------------------------------------------------------------
-- BEGIN;
--   UPDATE project
--      SET plan_start_date = DATE '2025-01-15',
--          plan_end_date   = DATE '2025-06-30',
--          actual_start_date = NULL,
--          progress_pct    = 0,
--          health_id       = 3
--    WHERE id = 3;
--   UPDATE milestone SET plan_date = DATE '2026-05-17', actual_date = DATE '2026-05-19',
--                        status_id = 3, weight = 1, completed_at = NULL
--    WHERE id = 64;
--   UPDATE milestone SET plan_date = DATE '2026-05-22', actual_date = DATE '2026-05-24',
--                        status_id = 3, weight = 1, completed_at = NULL
--    WHERE id = 65;
--   UPDATE milestone SET plan_date = DATE '2026-06-01', weight = 1 WHERE id = 66;
--   UPDATE milestone SET plan_date = DATE '2026-06-21', weight = 1 WHERE id = 67;
--   UPDATE milestone SET plan_date = DATE '2026-07-21', weight = 1 WHERE id = 68;
-- COMMIT;

-- ---------------------------------------------------------------------------
-- 验证查询
-- ---------------------------------------------------------------------------
-- SELECT id, code, name, plan_start_date, plan_end_date, actual_start_date, progress_pct
--   FROM project WHERE id = 3;
-- SELECT id, name, plan_date, actual_date, status_id, weight
--   FROM milestone WHERE project_id = 3 ORDER BY sequence;
-- 调 Gantt 接口: GET /api/workload/gantt?from=2026-05-01&to=2026-09-30
