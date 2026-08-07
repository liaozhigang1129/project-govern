DROP VIEW IF EXISTS v_project_cost;
DROP VIEW IF EXISTS v_phase_cost;
DROP VIEW IF EXISTS v_dept_cost;

-- ① v_project_cost — 项目×月成本
-- 设计:每个 timesheet_entry 展开成一行的视图, rate 在外层算 (MySQL 不支持 LATERAL 嵌套子查询做 per-row 性能 OK)
-- 性能权衡:先聚合后乘 rate,而不是 entry 级展开 (GROUP BY 层级更深)
CREATE VIEW v_project_cost AS
SELECT
    te.project_id,
    p.code         AS project_code,
    p.name         AS project_name,
    p.budget_estimate,
    DATE_FORMAT(te.work_date, '%Y-%m') AS `year_month`,
    SUM(te.hours) AS total_hours,
    SUM(te.hours * COALESCE(hr.rate, rcd.rate, u.default_hourly_rate, 0)) AS total_cost,
    COUNT(DISTINCT tw.user_id) AS headcount
FROM timesheet_entry te
JOIN timesheet_week tw ON tw.id = te.timesheet_id AND tw.deleted = 0
JOIN project p         ON p.id  = te.project_id   AND p.deleted  = 0
JOIN app_user u        ON u.id  = tw.user_id      AND u.deleted  = 0
LEFT JOIN role rol     ON rol.id = u.primary_role_id
LEFT JOIN role_cost_default rcd ON rcd.code COLLATE utf8mb4_unicode_ci = rol.code COLLATE utf8mb4_unicode_ci
-- 每个 (user_id, year_month) 取当月生效的 user override rate
LEFT JOIN hourly_rate hr ON hr.user_id = tw.user_id
    AND hr.effective_month <= DATE_FORMAT(te.work_date, '%Y-%m-01')
    AND (hr.end_month IS NULL OR hr.end_month >= DATE_FORMAT(te.work_date, '%Y-%m-01'))
WHERE te.deleted = 0
GROUP BY te.project_id, p.code, p.name, p.budget_estimate, `year_month`;

-- ② v_phase_cost — 阶段×项目成本
CREATE VIEW v_phase_cost AS
SELECT
    mp.id          AS phase_id,
    mp.code        AS phase_code,
    mp.name        AS phase_name,
    mp.sort_order,
    te.project_id,
    p.code         AS project_code,
    p.name         AS project_name,
    DATE_FORMAT(te.work_date, '%Y-%m') AS `year_month`,
    SUM(te.hours) AS total_hours,
    SUM(te.hours * COALESCE(hr.rate, rcd.rate, u.default_hourly_rate, 0)) AS total_cost
FROM timesheet_entry te
JOIN timesheet_week tw ON tw.id = te.timesheet_id AND tw.deleted = 0
JOIN project p         ON p.id  = te.project_id   AND p.deleted  = 0
JOIN app_user u        ON u.id  = tw.user_id      AND u.deleted  = 0
LEFT JOIN milestone m  ON m.id = te.milestone_id  AND m.deleted  = 0
LEFT JOIN milestone_phase mp ON mp.id = m.phase_id
LEFT JOIN role rol     ON rol.id = u.primary_role_id
LEFT JOIN role_cost_default rcd ON rcd.code COLLATE utf8mb4_unicode_ci = rol.code COLLATE utf8mb4_unicode_ci
LEFT JOIN hourly_rate hr ON hr.user_id = tw.user_id
    AND hr.effective_month <= DATE_FORMAT(te.work_date, '%Y-%m-01')
    AND (hr.end_month IS NULL OR hr.end_month >= DATE_FORMAT(te.work_date, '%Y-%m-01'))
WHERE te.deleted = 0
GROUP BY mp.id, mp.code, mp.name, mp.sort_order, te.project_id, p.code, p.name, `year_month`;

-- ③ v_dept_cost — ���门×月成本
CREATE VIEW v_dept_cost AS
SELECT
    d.id           AS department_id,
    d.code         AS dept_code,
    d.name         AS dept_name,
    DATE_FORMAT(te.work_date, '%Y-%m') AS `year_month`,
    SUM(te.hours) AS total_hours,
    SUM(te.hours * COALESCE(hr.rate, rcd.rate, u.default_hourly_rate, 0)) AS total_cost,
    COUNT(DISTINCT tw.user_id) AS headcount
FROM timesheet_entry te
JOIN timesheet_week tw ON tw.id = te.timesheet_id AND tw.deleted = 0
JOIN app_user u        ON u.id  = tw.user_id      AND u.deleted  = 0
JOIN department d      ON d.id  = u.department_id AND d.deleted  = 0
LEFT JOIN role rol     ON rol.id = u.primary_role_id
LEFT JOIN role_cost_default rcd ON rcd.code COLLATE utf8mb4_unicode_ci = rol.code COLLATE utf8mb4_unicode_ci
LEFT JOIN hourly_rate hr ON hr.user_id = tw.user_id
    AND hr.effective_month <= DATE_FORMAT(te.work_date, '%Y-%m-01')
    AND (hr.end_month IS NULL OR hr.end_month >= DATE_FORMAT(te.work_date, '%Y-%m-01'))
WHERE te.deleted = 0
GROUP BY d.id, d.code, d.name, `year_month`;
