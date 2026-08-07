-- ============================================================
-- V4.28 钉钉请休假同步 (端到端: 表 + 增量同步支持)
-- ============================================================

-- 请休假记录表
CREATE TABLE IF NOT EXISTS dingtalk_leave (
    id              BIGSERIAL    PRIMARY KEY,
    -- 钉钉原始字段
    leave_id        VARCHAR(64)  NOT NULL UNIQUE,        -- 钉钉 leave_id (用于增量同步)
    userid          VARCHAR(64)  NOT NULL,                -- 钉钉 userid
    leave_type      VARCHAR(32)  NOT NULL,                -- 请假类型: 倒休/事假/病假/年假等
    start_time      TIMESTAMPTZ  NOT NULL,                -- 开始时间
    end_time        TIMESTAMPTZ  NOT NULL,                -- 结束时间
    duration        NUMERIC(8,2) NOT NULL DEFAULT 0,      -- 时长(小时)
    duration_unit   VARCHAR(16)  NOT NULL DEFAULT 'HOUR', -- 时长单位
    reason          TEXT,                                  -- 请假原因
    status          VARCHAR(16)  NOT NULL DEFAULT 'NORMAL', -- 状态: NORMAL/REJECT/REVOKE
    approver_userid VARCHAR(64),                          -- 审批人 userid
    -- PMO 业务字段
    user_id         BIGINT       REFERENCES app_user(id), -- 关联 PMO 用户
    user_name       VARCHAR(64),                          -- 冗余姓名(历史快照)
    department_id   BIGINT       REFERENCES department(id), -- 冗余部门
    -- 同步字段
    dingtalk_updated_at TIMESTAMPTZ,                      -- 钉钉端最后更新时间(增量同步)
    synced_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),  -- 本次同步时间
    -- 通用字段
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dtl_leave_id    ON dingtalk_leave(leave_id);
CREATE INDEX IF NOT EXISTS idx_dtl_userid      ON dingtalk_leave(userid);
CREATE INDEX IF NOT EXISTS idx_dtl_user_id     ON dingtalk_leave(user_id);
CREATE INDEX IF NOT EXISTS idx_dtl_dept_id     ON dingtalk_leave(department_id);
CREATE INDEX IF NOT EXISTS idx_dtl_start_time  ON dingtalk_leave(start_time);
CREATE INDEX IF NOT EXISTS idx_dtl_dingtalk_updated ON dingtalk_leave(dingtalk_updated_at);
CREATE INDEX IF NOT EXISTS idx_dtl_status      ON dingtalk_leave(status);
CREATE INDEX IF NOT EXISTS idx_dtl_deleted     ON dingtalk_leave(deleted) WHERE deleted = FALSE;

-- 同步元数据表(记录上次同步时间等,支持增量)
CREATE TABLE IF NOT EXISTS dingtalk_leave_sync_state (
    id              BIGSERIAL    PRIMARY KEY,
    sync_key        VARCHAR(64)  NOT NULL UNIQUE,        -- 唯一键: 'global' 或 userid
    last_sync_time  TIMESTAMPTZ  NOT NULL,               -- 上次同步时间
    last_total      INTEGER      NOT NULL DEFAULT 0,     -- 上次拉取的总记录数
    last_created    INTEGER      NOT NULL DEFAULT 0,     -- 新增数
    last_updated    INTEGER      NOT NULL DEFAULT 0,     -- 更新数
    last_deleted    INTEGER      NOT NULL DEFAULT 0,     -- 删除数
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 同步日志表
CREATE TABLE IF NOT EXISTS dingtalk_leave_sync_log (
    id              BIGSERIAL    PRIMARY KEY,
    started_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    finished_at     TIMESTAMPTZ,
    trigger_type    VARCHAR(16)  NOT NULL,                -- MANUAL/SCHEDULED
    triggered_by    VARCHAR(64)  NOT NULL,                -- 触发人
    status          VARCHAR(16)  NOT NULL DEFAULT 'RUNNING', -- RUNNING/SUCCESS/FAILED/PARTIAL
    sync_mode       VARCHAR(16)  NOT NULL DEFAULT 'INCREMENTAL', -- FULL/INCREMENTAL
    last_sync_time  TIMESTAMPTZ,                          -- 同步起点(增量)
    fetched         INTEGER      NOT NULL DEFAULT 0,      -- 拉取数量
    created_count   INTEGER      NOT NULL DEFAULT 0,      -- 新增
    updated_count   INTEGER      NOT NULL DEFAULT 0,      -- 更新
    deleted_count   INTEGER      NOT NULL DEFAULT 0,      -- 删除/失效
    skipped_count   INTEGER      NOT NULL DEFAULT 0,      -- 跳过
    error_message   TEXT,
    error_detail    TEXT
);

CREATE INDEX IF NOT EXISTS idx_dtlsl_started ON dingtalk_leave_sync_log(started_at DESC);

-- 同步状态行
INSERT INTO dingtalk_leave_sync_state (sync_key, last_sync_time, last_total, last_created, last_updated, last_deleted)
VALUES ('global', NOW() - INTERVAL '30 days', 0, 0, 0, 0)
ON CONFLICT (sync_key) DO NOTHING;

-- 注释
COMMENT ON TABLE dingtalk_leave IS '钉钉请休假记录 (P5 同步)';
COMMENT ON TABLE dingtalk_leave_sync_state IS '钉钉请休假同步元数据 (用于增量同步)';
COMMENT ON TABLE dingtalk_leave_sync_log IS '钉钉请休假同步日志';
