-- ============================================================
-- V4.30 钉钉考勤(每日打卡)同步 (PG 版, 与 MySQL V4.30 对齐)
-- 数据源: 钉钉新版 attendance API
-- ============================================================

CREATE TABLE IF NOT EXISTS dingtalk_attendance (
    id              BIGSERIAL    PRIMARY KEY,
    record_id       VARCHAR(96)  NOT NULL UNIQUE,
    userid          VARCHAR(64)  NOT NULL,
    work_date       DATE         NOT NULL,
    check_type      VARCHAR(16)  NOT NULL DEFAULT '',
    source          VARCHAR(16)  NOT NULL DEFAULT '',
    time_result     VARCHAR(16)  NOT NULL DEFAULT '',
    location_method VARCHAR(16)  NOT NULL DEFAULT '',
    location_result VARCHAR(16)  NOT NULL DEFAULT '',
    plan_time       TIMESTAMP    NULL,
    actual_time     TIMESTAMP    NULL,
    base_check_time TIMESTAMP    NULL,
    user_id         BIGINT       NULL,
    user_name       VARCHAR(64)  NULL,
    department_id   BIGINT       NULL,
    dingtalk_updated_at TIMESTAMP NULL,
    synced_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_dta_userid          ON dingtalk_attendance(userid);
CREATE INDEX IF NOT EXISTS idx_dta_user_id         ON dingtalk_attendance(user_id);
CREATE INDEX IF NOT EXISTS idx_dta_dept_id         ON dingtalk_attendance(department_id);
CREATE INDEX IF NOT EXISTS idx_dta_work_date       ON dingtalk_attendance(work_date);
CREATE INDEX IF NOT EXISTS idx_dta_dingtalk_updated ON dingtalk_attendance(dingtalk_updated_at);
CREATE INDEX IF NOT EXISTS idx_dta_deleted         ON dingtalk_attendance(deleted);

CREATE TABLE IF NOT EXISTS dingtalk_attendance_sync_state (
    id              BIGSERIAL    PRIMARY KEY,
    sync_key        VARCHAR(64)  NOT NULL UNIQUE,
    last_sync_time  TIMESTAMP    NOT NULL,
    last_total      INT          NOT NULL DEFAULT 0,
    last_created    INT          NOT NULL DEFAULT 0,
    last_updated    INT          NOT NULL DEFAULT 0,
    last_deleted    INT          NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dingtalk_attendance_sync_log (
    id              BIGSERIAL    PRIMARY KEY,
    started_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at     TIMESTAMP    NULL,
    trigger_type    VARCHAR(16)  NOT NULL,
    triggered_by    VARCHAR(64)  NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'RUNNING',
    sync_mode       VARCHAR(16)  NOT NULL DEFAULT 'INCREMENTAL',
    range_from      TIMESTAMP    NULL,
    range_to        TIMESTAMP    NULL,
    last_sync_time  TIMESTAMP    NULL,
    fetched         INT          NOT NULL DEFAULT 0,
    created_count   INT          NOT NULL DEFAULT 0,
    updated_count   INT          NOT NULL DEFAULT 0,
    deleted_count   INT          NOT NULL DEFAULT 0,
    skipped_count   INT          NOT NULL DEFAULT 0,
    error_message   TEXT         NULL,
    error_detail    TEXT         NULL
);

INSERT INTO system_config (config_key, config_value, value_type, config_group, default_value, sort_order, description, updated_at) VALUES
  ('integration.dingtalk.attendance_window_days', '14', 'NUMBER', 'integration', '14', 210, '考勤同步时间范围(天),默认最近 2 周', now()),
  ('integration.dingtalk.attendance_cron',         '0 0 3 ? * SUN', 'STRING', 'integration', '0 0 3 ? * SUN', 211, '考勤定时同步 cron 表达式(默认每周日 03:00)', now())
ON CONFLICT (config_key) DO NOTHING;
