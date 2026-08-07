-- ============================================================
-- V4.9 工时 → 成本引擎 (PostgreSQL 版)
-- 与 MySQL 版 V4.9__cost_engine.sql 同构
-- ============================================================

CREATE TABLE IF NOT EXISTS cost_calculation (
    id                  BIGSERIAL     PRIMARY KEY,
    project_id          BIGINT        NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    period              VARCHAR(7)    NOT NULL,
    user_id             BIGINT        NOT NULL,
    role_id             BIGINT        NULL,
    department_id       BIGINT        NULL,
    hours               NUMERIC(10,2) NOT NULL DEFAULT 0,
    hourly_rate         NUMERIC(10,2) NOT NULL DEFAULT 0,
    cost                NUMERIC(14,2) NOT NULL DEFAULT 0,
    currency            CHAR(3)       NOT NULL DEFAULT 'CNY',
    rate_source         VARCHAR(32)   NOT NULL DEFAULT 'USER',
    timesheet_ids       TEXT          NULL,
    calculated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_cost_period_user UNIQUE (project_id, period, user_id)
);
CREATE INDEX IF NOT EXISTS idx_cost_project ON cost_calculation(project_id);
CREATE INDEX IF NOT EXISTS idx_cost_period  ON cost_calculation(period);
CREATE INDEX IF NOT EXISTS idx_cost_user    ON cost_calculation(user_id);
CREATE INDEX IF NOT EXISTS idx_cost_dept    ON cost_calculation(department_id);
CREATE INDEX IF NOT EXISTS idx_cost_role    ON cost_calculation(role_id);

CREATE TABLE IF NOT EXISTS rate_override_log (
    id              BIGSERIAL     PRIMARY KEY,
    project_id      BIGINT        NOT NULL,
    user_id         BIGINT        NOT NULL,
    old_rate        NUMERIC(10,2) NULL,
    new_rate        NUMERIC(10,2) NOT NULL,
    reason          VARCHAR(256)  NULL,
    operator_id     BIGINT        NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_rate_log_project_user ON rate_override_log(project_id, user_id);
CREATE INDEX IF NOT EXISTS idx_rate_log_operator ON rate_override_log(operator_id);