-- ============================================================
-- V2.4 兜底: 确保 P2.B 负载查询视图存在 (MySQL)
-- ============================================================
-- 背景同 PG 版:V1.6 视图偶发性缺失,本脚本探测后按需重建。

DROP VIEW IF EXISTS v_active_user;
CREATE VIEW v_active_user AS
SELECT  u.id, u.username, u.full_name, u.department_id, d.name AS department_name
FROM app_user u
LEFT JOIN department d ON d.id = u.department_id
WHERE u.deleted = FALSE;

DROP VIEW IF EXISTS v_user_weekly_load;
CREATE VIEW v_user_weekly_load AS
SELECT  u.id                              AS user_id,
        u.full_name,
        u.department_id,
        ts.week_start,
        ts.week_end,
        ts.status                         AS timesheet_status,
        COALESCE(SUM(te.hours), 0)        AS total_hours,
        COUNT(DISTINCT te.project_id)     AS project_count,
        COUNT(*)                          AS entry_count
FROM app_user u
LEFT JOIN timesheet_week ts  ON ts.user_id = u.id AND ts.deleted = FALSE
LEFT JOIN timesheet_entry te ON te.timesheet_id = ts.id
WHERE u.deleted = FALSE
GROUP BY u.id, u.full_name, u.department_id, ts.week_start, ts.week_end, ts.status;
