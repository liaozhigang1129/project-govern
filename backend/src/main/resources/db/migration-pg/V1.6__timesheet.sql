-- ============================================================
-- V1.6 工时填报 (周维度, PM 录入)
-- ============================================================

-- 工时周表
CREATE TABLE timesheet_week (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT  NOT NULL REFERENCES app_user(id),
    week_start      DATE    NOT NULL,
    week_end        DATE    NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    submitter_note  TEXT,
    submitted_at    TIMESTAMPTZ,
    approver_id     BIGINT  REFERENCES app_user(id),
    approved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_tsweek_user_start UNIQUE (user_id, week_start)
);
CREATE INDEX idx_tsweek_user_status ON timesheet_week(user_id, status);
CREATE INDEX idx_tsweek_range       ON timesheet_week(week_start, week_end);

-- 工时明细
CREATE TABLE timesheet_entry (
    id              BIGSERIAL PRIMARY KEY,
    timesheet_id    BIGINT  NOT NULL REFERENCES timesheet_week(id) ON DELETE CASCADE,
    work_date       DATE    NOT NULL,
    project_id      BIGINT  NOT NULL REFERENCES project(id),
    milestone_id    BIGINT  REFERENCES milestone(id),
    hours           NUMERIC(5,2) NOT NULL,
    description     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_hours_range CHECK (hours >= 0 AND hours <= 24),
    CONSTRAINT uq_tsent_dedup  UNIQUE (timesheet_id, work_date, project_id, milestone_id)
);
CREATE INDEX idx_tsent_user_date ON timesheet_entry(work_date);
CREATE INDEX idx_tsent_proj_date ON timesheet_entry(project_id, work_date);

-- ============================================================
-- P2.B 负载查询辅助视图
-- ============================================================

CREATE OR REPLACE VIEW v_active_user AS
SELECT  u.id, u.username, u.full_name, u.department_id, d.name AS department_name
FROM app_user u
LEFT JOIN department d ON d.id = u.department_id
WHERE u.deleted = FALSE;

CREATE OR REPLACE VIEW v_user_weekly_load AS
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
