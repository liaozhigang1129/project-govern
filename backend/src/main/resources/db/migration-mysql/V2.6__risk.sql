-- ============================================================
-- V2.6 P4 风险管理 (MySQL 版)
-- 字段/约束/索引 跟 PG 版 (V2.6__risk.sql) 对齐
-- 差异:
--   - BIGSERIAL → BIGINT NOT NULL AUTO_INCREMENT
--   - TIMESTAMPTZ → DATETIME(6)
--   - DATE / TEXT / VARCHAR 同义
--   - PG 触发器 fn_set_updated_at → 删 (JPA @PreUpdate 维护)
--   - PG CHECK + 视图 → 改用 ENUM 风格 + app 层校验 (JPA DTO 已有)
-- ============================================================

-- ============================================================
-- ① risk —— 风险主表
-- ============================================================
CREATE TABLE IF NOT EXISTS risk (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id            BIGINT       NOT NULL,

    code                  VARCHAR(32)  NOT NULL,
    title                 VARCHAR(256) NOT NULL,
    description           TEXT,

    category              VARCHAR(16)  NOT NULL,
    probability           INT          NOT NULL,
    impact                INT          NOT NULL,
    score                 INT          NOT NULL,
    level                 VARCHAR(16)  NOT NULL,
    status                VARCHAR(16)  NOT NULL DEFAULT 'OPEN',

    owner_user_id         BIGINT       NULL,

    mitigation            TEXT,
    contingency           TEXT,
    response_strategy     VARCHAR(16)  NULL,

    identified_date       DATE         NOT NULL,
    target_close_date     DATE         NULL,
    actual_close_date     DATE         NULL,

    related_wbs_task_id   BIGINT       NULL,
    related_milestone_id  BIGINT       NULL,

    created_by            BIGINT       NULL,
    created_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted               TINYINT(1)   NOT NULL DEFAULT 0,

    UNIQUE KEY uk_risk_project_code (project_id, code),
    KEY idx_risk_project   (project_id),
    KEY idx_risk_status    (status),
    KEY idx_risk_level     (level),
    KEY idx_risk_owner     (owner_user_id),
    KEY idx_risk_category  (category),
    KEY idx_risk_deleted   (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目风险登记册(Risk Register)';


-- ============================================================
-- ② risk_response —— 应对行动
-- ============================================================
CREATE TABLE IF NOT EXISTS risk_response (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    risk_id         BIGINT       NOT NULL,

    action          VARCHAR(256) NOT NULL,
    owner_user_id   BIGINT       NULL,
    due_date        DATE         NULL,
    completed_at    DATETIME(6)  NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PLANNED',
    note            TEXT,

    created_by      BIGINT       NULL,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted         TINYINT(1)   NOT NULL DEFAULT 0,

    KEY idx_risk_resp_risk   (risk_id),
    KEY idx_risk_resp_owner  (owner_user_id),
    KEY idx_risk_resp_status (status),
    KEY idx_risk_resp_del    (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风险应对行动(每个 risk 可挂多条)';


-- ============================================================
-- ③ risk_history —— 风险变更历史
-- ============================================================
CREATE TABLE IF NOT EXISTS risk_history (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    risk_id         BIGINT       NOT NULL,

    action          VARCHAR(32)  NOT NULL,
    field_name      VARCHAR(64)  NULL,
    old_value       TEXT         NULL,
    new_value       TEXT         NULL,
    comment         TEXT         NULL,

    operator_id     BIGINT       NULL,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    KEY idx_risk_hist_risk    (risk_id),
    KEY idx_risk_hist_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风险变更历史(审计追踪)';
