-- ============================================================
-- V4.30 钉钉考勤(每日考勤结果)同步 (MySQL 版)
-- 对应实体: DingTalkAttendance / DingTalkAttendanceSyncState / DingTalkAttendanceSyncLog
-- 数据源: 钉钉新版 attendance API
--   /v1.0/attendance/records/query
--   字段: userId, workDate, checkType, source, timeResult, locationMethod, locationResult
-- ============================================================

-- 考勤记录表
CREATE TABLE IF NOT EXISTS dingtalk_attendance (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    -- 钉钉原始字段
    record_id       VARCHAR(96)  NOT NULL UNIQUE,         -- 钉钉 recordId (打卡/考勤结果唯一 ID)
    userid          VARCHAR(64)  NOT NULL,                -- 钉钉 userid
    work_date       DATE         NOT NULL,                -- 工作日 YYYY-MM-DD
    check_type      VARCHAR(16)  NOT NULL DEFAULT '',     -- OnDuty(上班) / OffDuty(下班)
    source          VARCHAR(16)  NOT NULL DEFAULT '',     -- USER(手动)/SYSTEM/BT(蓝牙)/FACE/人脸等
    time_result     VARCHAR(16)  NOT NULL DEFAULT '',     -- Normal/Tardy(迟到)/Early(早退)/SeriousTardy/NotSigned(缺卡)
    location_method VARCHAR(16)  NOT NULL DEFAULT '',
    location_result VARCHAR(16)  NOT NULL DEFAULT '',     -- Normal/Outside(外勤)/Invalid
    -- 工时字段
    plan_time       DATETIME(6)  NULL,                    -- 计划打卡时间
    actual_time     DATETIME(6)  NULL,                    -- 实际打卡时间
    base_check_time DATETIME(6)  NULL,                    -- 班次基准时间
    -- 关联 PMO 业务
    user_id         BIGINT       NULL,                    -- 关联 PMO 用户 ID
    user_name       VARCHAR(64)  NULL,                    -- 冗余姓名(历史快照)
    department_id   BIGINT       NULL,                    -- 冗余部门
    -- 同步字段
    dingtalk_updated_at DATETIME(6) NULL,
    synced_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    -- 通用字段
    deleted         TINYINT(1)   NOT NULL DEFAULT 0,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_dta_record_id        (record_id),
    INDEX idx_dta_userid           (userid),
    INDEX idx_dta_user_id          (user_id),
    INDEX idx_dta_dept_id          (department_id),
    INDEX idx_dta_work_date        (work_date),
    INDEX idx_dta_dingtalk_updated (dingtalk_updated_at),
    INDEX idx_dta_deleted          (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='钉钉考勤记录(每日打卡/结果) V4.30';

-- 同步元数据
CREATE TABLE IF NOT EXISTS dingtalk_attendance_sync_state (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sync_key        VARCHAR(64)  NOT NULL UNIQUE,
    last_sync_time  DATETIME(6)  NOT NULL,
    last_total      INT          NOT NULL DEFAULT 0,
    last_created    INT          NOT NULL DEFAULT 0,
    last_updated    INT          NOT NULL DEFAULT 0,
    last_deleted    INT          NOT NULL DEFAULT 0,
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='钉钉考勤同步元数据 V4.30';

-- 同步日志
CREATE TABLE IF NOT EXISTS dingtalk_attendance_sync_log (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    started_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    finished_at     DATETIME(6)  NULL,
    trigger_type    VARCHAR(16)  NOT NULL,                 -- MANUAL/SCHEDULED
    triggered_by    VARCHAR(64)  NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'RUNNING',
    sync_mode       VARCHAR(16)  NOT NULL DEFAULT 'INCREMENTAL',
    range_from      DATETIME(6)  NULL,                    -- 同步范围开始
    range_to        DATETIME(6)  NULL,                    -- 同步范围结束
    last_sync_time  DATETIME(6)  NULL,
    fetched         INT          NOT NULL DEFAULT 0,
    created_count   INT          NOT NULL DEFAULT 0,
    updated_count   INT          NOT NULL DEFAULT 0,
    deleted_count   INT          NOT NULL DEFAULT 0,
    skipped_count   INT          NOT NULL DEFAULT 0,
    error_message   TEXT         NULL,
    error_detail    LONGTEXT     NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='钉钉考勤同步日志 V4.30';

-- 默认考勤同步周期配置: 每周日 03:00 跑最近 2 周
INSERT INTO system_config (config_key, config_value, value_type, config_group, default_value, sort_order, description, updated_at) VALUES
  ('integration.dingtalk.attendance_window_days', '14', 'NUMBER', 'integration', '14', 210, '考勤同步时间范围(天),默认最近 2 周', now()),
  ('integration.dingtalk.attendance_cron',         '0 0 3 ? * SUN', 'STRING', 'integration', '0 0 3 ? * SUN', 211, '考勤定时同步 cron 表达式(默认每周日 03:00)', now())
ON DUPLICATE KEY UPDATE config_key = config_key;
