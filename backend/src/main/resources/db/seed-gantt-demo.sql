-- 补全甘特图演示数据(给现有未设日期的项目填上计划起止 + 进度)
-- 目的:让 /api/gantt 渲染更真实,UI 不空
-- 仅在 dev/staging 执行(生产前 DELETE FROM milestone WHERE project_id IN (SELECT id FROM project WHERE code LIKE 'P-AUTO-%');)

-- 1) 给项目设置 plan/actual 起止日期(已立项的最近 6 个)
UPDATE project
SET
    plan_start_date = COALESCE(plan_start_date, CURRENT_DATE - INTERVAL '30 days'),
    plan_end_date   = COALESCE(plan_end_date,   CURRENT_DATE + INTERVAL '60 days'),
    actual_start_date = COALESCE(actual_start_date, CURRENT_DATE - INTERVAL '25 days'),
    progress_pct   = COALESCE(progress_pct, 35)
WHERE deleted = false
  AND status_id IN (SELECT id FROM project_status WHERE code IN ('ACTIVE', 'PENDING'))
  AND plan_start_date IS NULL
  AND id <= 6;

-- 2) 给每个项目加 5 个里程碑(确保甘特图右侧里程碑三角渲染)
--    状态映射:DONE→COMPLETED(3)/ IN_PROGRESS→IN_PROGRESS(2)/ PENDING→PENDING(1)
INSERT INTO milestone (project_id, name, plan_date, actual_date, status_id, sequence, deleted, created_at, updated_at)
SELECT
    p.id,
    m.name,
    m.plan,
    m.actual,
    CASE m.status
        WHEN 'DONE'        THEN (SELECT id FROM milestone_status WHERE code = 'COMPLETED')
        WHEN 'IN_PROGRESS' THEN (SELECT id FROM milestone_status WHERE code = 'IN_PROGRESS')
        WHEN 'PENDING'     THEN (SELECT id FROM milestone_status WHERE code = 'PENDING')
    END,
    m.seq,
    false,
    NOW(),
    NOW()
FROM project p
CROSS JOIN (VALUES
    ('需求评审',   CURRENT_DATE - INTERVAL '20 days', CURRENT_DATE - INTERVAL '18 days', 'DONE',         1),
    ('架构设计',   CURRENT_DATE - INTERVAL '15 days', CURRENT_DATE - INTERVAL '13 days', 'DONE',         2),
    ('开发完成',   CURRENT_DATE - INTERVAL '5 days',  NULL,                              'IN_PROGRESS',  3),
    ('UAT 测试',   CURRENT_DATE + INTERVAL '15 days', NULL,                              'PENDING',      4),
    ('正式上线',   CURRENT_DATE + INTERVAL '45 days', NULL,                              'PENDING',      5)
) AS m(name, plan, actual, status, seq)
WHERE p.deleted = false
  AND p.id <= 6
  AND NOT EXISTS (
      SELECT 1 FROM milestone ms
      WHERE ms.project_id = p.id AND ms.sequence = m.seq AND ms.deleted = false
  );
