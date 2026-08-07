-- ============================================================
-- V2.4 兜底: 确保 P2.B 负载查询视图存在
-- ============================================================
-- 背景:
--   V1.6__timesheet.sql 已 CREATE OR REPLACE VIEW,但生产上发现视图
--   偶发性缺失(怀疑手工 DROP / 工具误操作)。重启 Spring 后
--   /api/workload/users 直接 500: relation "v_active_user" does not exist
-- 处置:
--   本补丁用 DO $$ 块,跑前探测,缺则按 V1.6 同构 DDL 重建;
--   已有则什么都不做(避免无效重定义导致依赖失效)。
--   幂等、安全、向前兼容。

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_views
        WHERE schemaname = 'public' AND viewname = 'v_active_user'
    ) THEN
        EXECUTE $v$
            CREATE VIEW v_active_user AS
            SELECT  u.id, u.username, u.full_name, u.department_id, d.name AS department_name
            FROM app_user u
            LEFT JOIN department d ON d.id = u.department_id
            WHERE u.deleted = FALSE
        $v$;
        RAISE NOTICE 'V2.4: v_active_user 已重建';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_views
        WHERE schemaname = 'public' AND viewname = 'v_user_weekly_load'
    ) THEN
        EXECUTE $v$
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
            GROUP BY u.id, u.full_name, u.department_id, ts.week_start, ts.week_end, ts.status
        $v$;
        RAISE NOTICE 'V2.4: v_user_weekly_load 已重建';
    END IF;
END
$$;
