-- ============================================================
-- V4.1: 多维成本核算视图 (F2) - 修订版
-- 修复: timesheet_entry 无 user_id 字段,需 JOIN timesheet_week 取 user_id
-- 依赖: app_user / department / project / milestone / milestone_phase
--       / timesheet_entry / timesheet_week / role_cost_default / role
-- ============================================================

-- 一次性建一个函数, 复用单行时薪解析
CREATE OR REPLACE FUNCTION fn_resolve_hourly_rate(
    p_user_id BIGINT,
    p_month   DATE
) RETURNS TABLE(rate NUMERIC(10,2), source TEXT) AS $$
DECLARE
    v_rate     NUMERIC(10,2);
    v_user_role_code VARCHAR(32);
    v_user_default  NUMERIC(10,2);
BEGIN
    -- 1) hourly_rate_v4 命中
    SELECT hr.rate INTO v_rate
    FROM hourly_rate_v4 hr
    WHERE hr.user_id = p_user_id
      AND hr.effective_month <= p_month
      AND (hr.end_month IS NULL OR hr.end_month >= p_month)
    ORDER BY hr.effective_month DESC
    LIMIT 1;
    IF FOUND THEN
        rate := v_rate; source := 'USER_OVERRIDE'; RETURN NEXT; RETURN;
    END IF;

    -- 2) 角色档默认 (从 app_user.primary_role_id → role.code 拿)
    SELECT r.code, u.default_hourly_rate
      INTO v_user_role_code, v_user_default
    FROM app_user u
    LEFT JOIN role r ON r.id = u.primary_role_id
   WHERE u.id = p_user_id;

    IF v_user_role_code IS NOT NULL THEN
        SELECT rcd.rate INTO v_rate
        FROM role_cost_default rcd
        WHERE rcd.code = v_user_role_code;
        IF FOUND AND v_rate IS NOT NULL THEN
            rate := v_rate; source := 'ROLE_COST_DEFAULT'; RETURN NEXT; RETURN;
        END IF;
    END IF;

    -- 3) app_user.default_hourly_rate 兜底
    IF v_user_default IS NOT NULL AND v_user_default > 0 THEN
        rate := v_user_default; source := 'USER_DEFAULT'; RETURN NEXT; RETURN;
    END IF;

    -- 4) 都没有
    rate := 0; source := 'NONE'; RETURN NEXT;
END;
$$ LANGUAGE plpgsql STABLE;

COMMENT ON FUNCTION fn_resolve_hourly_rate IS 'P0-F2 时薪解析: hourly_rate_v4 > role_cost_default > user_default > 0';

-- ============================================================
-- v_project_cost: 项目×月成本
-- ============================================================
CREATE OR REPLACE VIEW v_project_cost AS
SELECT
    te.project_id,
    p.code       AS project_code,
    p.name       AS project_name,
    p.budget_estimate,
    TO_CHAR(te.work_date, 'YYYY-MM') AS year_month,
    SUM(te.hours) AS total_hours,
    SUM(te.hours * r.rate) AS total_cost,
    COUNT(DISTINCT tw.user_id) AS headcount
FROM timesheet_entry te
JOIN timesheet_week tw ON tw.id = te.timesheet_id AND tw.deleted = FALSE
JOIN project p        ON p.id = te.project_id AND p.deleted = FALSE
JOIN app_user u       ON u.id = tw.user_id    AND u.deleted = FALSE
CROSS JOIN LATERAL fn_resolve_hourly_rate(tw.user_id, te.work_date) r
WHERE te.deleted = FALSE
GROUP BY te.project_id, p.code, p.name, p.budget_estimate, TO_CHAR(te.work_date, 'YYYY-MM');

COMMENT ON VIEW v_project_cost IS 'F2 项目×月成本: hours + cost(三级fallback时薪) + 预算对比';

-- ============================================================
-- v_phase_cost: 阶段×项目成本
-- ============================================================
CREATE OR REPLACE VIEW v_phase_cost AS
SELECT
    mp.id        AS phase_id,
    mp.code      AS phase_code,
    mp.name      AS phase_name,
    mp.sort_order,
    te.project_id,
    p.code       AS project_code,
    p.name       AS project_name,
    TO_CHAR(te.work_date, 'YYYY-MM') AS year_month,
    SUM(te.hours) AS total_hours,
    SUM(te.hours * r.rate) AS total_cost
FROM timesheet_entry te
JOIN timesheet_week tw ON tw.id = te.timesheet_id AND tw.deleted = FALSE
JOIN project p         ON p.id = te.project_id AND p.deleted = FALSE
JOIN app_user u        ON u.id = tw.user_id    AND u.deleted = FALSE
CROSS JOIN LATERAL fn_resolve_hourly_rate(tw.user_id, te.work_date) r
LEFT JOIN milestone m ON m.id = te.milestone_id AND m.deleted = FALSE
LEFT JOIN milestone_phase mp ON mp.id = m.phase_id
WHERE te.deleted = FALSE
GROUP BY mp.id, mp.code, mp.name, mp.sort_order,
         te.project_id, p.code, p.name, TO_CHAR(te.work_date, 'YYYY-MM');

COMMENT ON VIEW v_phase_cost IS 'F2 阶段×项目成本: 7阶段+无阶段 维度切分';

-- ============================================================
-- v_dept_cost: 部门×月成本
-- ============================================================
CREATE OR REPLACE VIEW v_dept_cost AS
SELECT
    d.id         AS department_id,
    d.code       AS dept_code,
    d.name       AS dept_name,
    TO_CHAR(te.work_date, 'YYYY-MM') AS year_month,
    SUM(te.hours) AS total_hours,
    SUM(te.hours * r.rate) AS total_cost,
    COUNT(DISTINCT tw.user_id) AS headcount
FROM timesheet_entry te
JOIN timesheet_week tw ON tw.id = te.timesheet_id AND tw.deleted = FALSE
JOIN app_user u ON u.id = tw.user_id AND u.deleted = FALSE
JOIN department d ON d.id = u.department_id AND d.deleted = FALSE
CROSS JOIN LATERAL fn_resolve_hourly_rate(tw.user_id, te.work_date) r
WHERE te.deleted = FALSE
GROUP BY d.id, d.code, d.name, TO_CHAR(te.work_date, 'YYYY-MM');

COMMENT ON VIEW v_dept_cost IS 'F2 部门×月成本: 头部成本结构 + 人均产能';

-- ============================================================
-- 授权
-- ============================================================
GRANT SELECT ON v_project_cost TO project_govern;
GRANT SELECT ON v_phase_cost   TO project_govern;
GRANT SELECT ON v_dept_cost    TO project_govern;
