-- ============================================================
-- V4.28 钉钉请休假同步 (MySQL 版,与 PG 版 V4.28 对齐)
-- 对应实体: DingTalkLeave / DingTalkLeaveSyncState / DingTalkLeaveSyncLog
-- ============================================================

-- 请休假记录表
CREATE TABLE IF NOT EXISTS dingtalk_leave (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    -- 钉钉原始字段
    leave_id        VARCHAR(64)  NOT NULL UNIQUE,         -- 钉钉 leave_id (用于增量同步)
    userid          VARCHAR(64)  NOT NULL,                -- 钉钉 userid
    leave_type      VARCHAR(32)  NOT NULL,                -- 请假类型: 倒休/事假/病假/年假等
    start_time      DATETIME(6)  NOT NULL,                -- 开始时间
    end_time        DATETIME(6)  NOT NULL,                -- 结束时间
    duration        DECIMAL(8,2) NOT NULL DEFAULT 0,      -- 时长(小时)
    duration_unit   VARCHAR(16)  NOT NULL DEFAULT 'HOUR', -- 时长单位
    reason          TEXT,                                 -- 请假原因
    status          VARCHAR(16)  NOT NULL DEFAULT 'NORMAL', -- 状态: NORMAL/REJECT/REVOKE
    approver_userid VARCHAR(64),                         -- 审批人 userid
    -- PMO 业务字段
    user_id         BIGINT,                              -- 关联 PMO 用户
    user_name       VARCHAR(64),                         -- 冗余姓名(历史快照)
    department_id   BIGINT,                              -- 冗余部门
    -- 同步字段
    dingtalk_updated_at DATETIME(6),                      -- 钉钉端最后更新时间(增量同步)
    synced_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6), -- 本次同步时间
    -- 通用字段
    deleted         TINYINT(1)   NOT NULL DEFAULT 0,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_dtl_leave_id        (leave_id),
    INDEX idx_dtl_userid          (userid),
    INDEX idx_dtl_user_id         (user_id),
    INDEX idx_dtl_dept_id         (department_id),
    INDEX idx_dtl_start_time      (start_time),
    INDEX idx_dtl_dingtalk_updated (dingtalk_updated_at),
    INDEX idx_dtl_status          (status),
    INDEX idx_dtl_deleted         (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='钉钉请休假记录 (P5 同步)';

-- 同步元数据表(记录上次同步时间等,支持增量)
CREATE TABLE IF NOT EXISTS dingtalk_leave_sync_state (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sync_key        VARCHAR(64)  NOT NULL UNIQUE,         -- 唯一键: 'global' 或 userid
    last_sync_time  DATETIME(6)  NOT NULL,                -- 上次同步时间
    last_total      INT          NOT NULL DEFAULT 0,      -- 上次拉取的总记录数
    last_created    INT          NOT NULL DEFAULT 0,      -- 新增数
    last_updated    INT          NOT NULL DEFAULT 0,      -- 更新数
    last_deleted    INT          NOT NULL DEFAULT 0,      -- 删除数
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='钉钉请休假同步元数据 (用于增量同步)';

-- 同步日志表
CREATE TABLE IF NOT EXISTS dingtalk_leave_sync_log (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    started_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    finished_at     DATETIME(6),
    trigger_type    VARCHAR(16)  NOT NULL,                 -- MANUAL/SCHEDULED
    triggered_by    VARCHAR(64)  NOT NULL,                 -- 触发人
    status          VARCHAR(16)  NOT NULL DEFAULT 'RUNNING', -- RUNNING/SUCCESS/FAILED/PARTIAL
    sync_mode       VARCHAR(16)  NOT NULL DEFAULT 'INCREMENTAL', -- FULL/INCREMENTAL
    last_sync_time  DATETIME(6),                          -- 同步起点(增量)
    fetched         INT          NOT NULL DEFAULT 0,      -- 拉取数量
    created_count   INT          NOT NULL DEFAULT 0,      -- 新增
    updated_count   INT          NOT NULL DEFAULT 0,      -- 更新
    deleted_count   INT          NOT NULL DEFAULT 0,      -- 删除/失效
    skipped_count   INT          NOT NULL DEFAULT 0,      -- 跳过
    error_message   TEXT,
    error_detail    TEXT,
    INDEX idx_dtlsl_started (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='钉钉请休假同步日志';

-- 同步状态行
INSERT INTO dingtalk_leave_sync_state (sync_key, last_sync_time, last_total, last_created, last_updated, last_deleted)
VALUES ('global', DATE_SUB(NOW(), INTERVAL 30 DAY), 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE sync_key = sync_key;
