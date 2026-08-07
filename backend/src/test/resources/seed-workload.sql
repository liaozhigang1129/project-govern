-- P2.B 测试专用:最小 seed + 视图(H2 MERGE 替代 DELETE+INSERT 避免 PK 冲突)
-- 注:@Sql 跑在事务外,@Transactional rollback 清不掉 @Sql 已 inserted 的行
-- → 必须用 MERGE INTO 让 INSERT 幂等

MERGE INTO department (id, name, code, parent_id, sort_order, enabled, deleted, created_at, updated_at)
KEY (id) VALUES (1, 'PMO', 'PMO', NULL, 0, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO role (id, code, name, description, built_in, enabled, sort_order, created_at, updated_at) KEY (id) VALUES
    (1, 'PM', '项目经理', '', TRUE, TRUE, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 'VIEWER', '只读', '', TRUE, TRUE, 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO app_user (id, username, full_name, password_hash, email, phone, department_id, primary_role_id, job_title, default_hourly_rate, login_fail_count, must_change_password, enabled, deleted, created_at, updated_at) KEY (id) VALUES
    (2, 'pm_zhang', 'PM 张', 'hash', NULL, NULL, 1, 1, 'PM', 0, 0, FALSE, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, 'pm_li',    'PM 李', 'hash', NULL, NULL, 1, 1, 'PM', 0, 0, FALSE, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (6, 'viewer',   'viewer', 'hash', NULL, NULL, 1, 2, '',    0, 0, FALSE, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO project_type (id, code, name) KEY (id) VALUES (1, 'INTERNAL', '内部项目');
MERGE INTO project_status (id, code, name, is_terminal) KEY (id) VALUES (1, 'ACTIVE', '执行中', FALSE);
MERGE INTO health_level (id, code, name) KEY (id) VALUES (1, 'GREEN', '正常');

MERGE INTO project (id, code, name, type_id, status_id, health_id, department_id, pm_user_id, plan_start_date, plan_end_date, progress_pct, baseline_version, created_by, deleted, created_at, updated_at)
KEY (id) VALUES (1, 'P001', '示例项目', 1, 1, 1, 1, 2, '2026-01-01', '2026-12-31', 0, 0, 2, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO timesheet_week (id, user_id, week_start, week_end, status, created_at, updated_at, deleted)
KEY (id) VALUES (1, 3, '2026-06-01', '2026-06-07', 'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

MERGE INTO timesheet_entry (id, timesheet_id, work_date, project_id, hours, description, created_at, updated_at, deleted)
KEY (id) VALUES (1, 1, '2026-06-01', 1, 8.0, '周一开发', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

CREATE OR REPLACE VIEW v_active_user AS
SELECT u.id, u.username, u.full_name, u.department_id, d.name AS department_name
FROM app_user u
LEFT JOIN department d ON d.id = u.department_id
WHERE u.deleted = FALSE;

CREATE OR REPLACE VIEW v_user_weekly_load AS
SELECT u.id AS user_id, u.full_name, u.department_id,
       ts.week_start, ts.week_end, ts.status AS timesheet_status,
       COALESCE(SUM(te.hours), 0) AS total_hours,
       COUNT(DISTINCT te.project_id) AS project_count,
       COUNT(*) AS entry_count
FROM app_user u
LEFT JOIN timesheet_week ts ON ts.user_id = u.id AND ts.deleted = FALSE
LEFT JOIN timesheet_entry te ON te.timesheet_id = ts.id
WHERE u.deleted = FALSE
GROUP BY u.id, u.full_name, u.department_id, ts.week_start, ts.week_end, ts.status;
