-- ============================================================
-- V1.6 工时填报 (周维度, PM 录入) — MySQL 方言
-- ============================================================

CREATE TABLE timesheet_week (
    id              BIGINT  PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT  NOT NULL,
    week_start      DATE    NOT NULL,
    week_end        DATE    NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    submitter_note  TEXT,
    submitted_at    DATETIME(3),
    approver_id     BIGINT,
    approved_at     DATETIME(3),
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uq_tsweek_user_start (user_id, week_start),
    KEY idx_tsweek_user_status (user_id, status),
    KEY idx_tsweek_range (week_start, week_end),
    CONSTRAINT fk_tsweek_user     FOREIGN KEY (user_id)     REFERENCES app_user(id),
    CONSTRAINT fk_tsweek_approver FOREIGN KEY (approver_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工时周表';

CREATE TABLE timesheet_entry (
    id              BIGINT  PRIMARY KEY AUTO_INCREMENT,
    timesheet_id    BIGINT  NOT NULL,
    work_date       DATE    NOT NULL,
    project_id      BIGINT  NOT NULL,
    milestone_id    BIGINT,
    hours           DECIMAL(5,2) NOT NULL,
    description     TEXT,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uq_tsent_dedup (timesheet_id, work_date, project_id, milestone_id),
    KEY idx_tsent_user_date (work_date),
    KEY idx_tsent_proj_date (project_id, work_date),
    CONSTRAINT ck_hours_range CHECK (hours >= 0 AND hours <= 24),
    CONSTRAINT fk_tsent_ts    FOREIGN KEY (timesheet_id) REFERENCES timesheet_week(id) ON DELETE CASCADE,
    CONSTRAINT fk_tsent_proj  FOREIGN KEY (project_id)   REFERENCES project(id),
    CONSTRAINT fk_tsent_ms    FOREIGN KEY (milestone_id) REFERENCES milestone(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工时明细';

-- ============================================================
-- P2.B 负载查询辅助视图 (MySQL)
-- ============================================================

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
