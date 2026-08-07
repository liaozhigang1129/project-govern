-- ============================================================
-- V4.9 工时 → 成本引擎
-- 设计:
--   cost_calculation   成本快照表(按项目×期间×员工)
--     - 小时费率在工时审批通过时快照锁定,防止事后改费率导致历史成本漂移
--     - unique (project_id, period, user_id) 保证幂等
--   rate_override_log  费率变更审计(谁在何时把某员工在项目里的费率改成多少)
-- ============================================================

-- ① 成本快照表
CREATE TABLE IF NOT EXISTS cost_calculation (
    id                  BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id          BIGINT        NOT NULL,
    period              CHAR(7)       NOT NULL,                  -- 'YYYY-MM'
    user_id             BIGINT        NOT NULL,
    role_id             BIGINT        NULL,
    department_id       BIGINT        NULL,
    hours               DECIMAL(10,2) NOT NULL DEFAULT 0,
    hourly_rate         DECIMAL(10,2) NOT NULL DEFAULT 0,        -- 快照时的费率
    cost                DECIMAL(14,2) NOT NULL DEFAULT 0,        -- hours * rate
    currency            CHAR(3)       NOT NULL DEFAULT 'CNY',
    rate_source         VARCHAR(32)   NOT NULL DEFAULT 'USER',    -- PROJECT / USER / ROLE / DEFAULT
    timesheet_ids       TEXT          NULL,                       -- 关联的工时条目 ID 列表(JSON 数组字符串)
    calculated_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_cost_period_user (project_id, period, user_id),
    KEY idx_cost_project (project_id),
    KEY idx_cost_period  (period),
    KEY idx_cost_user    (user_id),
    KEY idx_cost_dept    (department_id),
    KEY idx_cost_role    (role_id),
    CONSTRAINT fk_cost_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目人工成本快照';

-- ② 费率变更审计
CREATE TABLE IF NOT EXISTS rate_override_log (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id      BIGINT        NOT NULL,
    user_id         BIGINT        NOT NULL,
    old_rate        DECIMAL(10,2) NULL,
    new_rate        DECIMAL(10,2) NOT NULL,
    reason          VARCHAR(256)  NULL,
    operator_id     BIGINT        NOT NULL,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_rate_log_project_user (project_id, user_id),
    KEY idx_rate_log_operator (operator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目级费率覆盖变更日志';

-- ③ seed: 给 PMO_ADMIN 角色绑定 COST_USER_MONTH 已有菜单(若不存在)
-- 这里不写菜单 seed,V4.8 已经处理