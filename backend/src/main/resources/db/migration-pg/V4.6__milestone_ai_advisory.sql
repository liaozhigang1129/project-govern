-- ============================================================
-- V4.4: F4 修复 sibling 模块 milestone_ai_advisory 表缺失
-- 原因: milestoneai 实体 (Sibling 写的健康顾问模块) 期望这张表
--       但 sibling 还没创建 Flyway migration 导致 Hibernate 启动报 schema validation
-- 幂等 (IF NOT EXISTS): 可重复跑
-- ============================================================
CREATE TABLE IF NOT EXISTS milestone_ai_advisory (
    id BIGSERIAL PRIMARY KEY,

    -- 关联
    project_id      BIGINT      NOT NULL,
    milestone_id    BIGINT      NOT NULL,
    phase_id        BIGINT,
    phase_code      VARCHAR(32),
    phase_name      VARCHAR(64),
    milestone_name  VARCHAR(128) NOT NULL,

    -- 计划状态
    milestone_plan_date    DATE,
    milestone_status_code  VARCHAR(16),

    -- 预警信号
    severity        VARCHAR(16) NOT NULL,
    score           NUMERIC(4,2) NOT NULL,
    confidence      NUMERIC(4,2) NOT NULL,
    signal_overdue      NUMERIC(4,2) NOT NULL DEFAULT 0,
    signal_spi          NUMERIC(4,2) NOT NULL DEFAULT 0,
    signal_phase_lag    NUMERIC(4,2) NOT NULL DEFAULT 0,
    signal_velocity     NUMERIC(4,2) NOT NULL DEFAULT 0,
    signal_historical   NUMERIC(4,2) NOT NULL DEFAULT 0,

    -- 解释 (JSON 字符串)
    reasons_json        JSON        NOT NULL DEFAULT '[]'::json,
    suggestions_json    JSON        NOT NULL DEFAULT '[]'::json,

    -- 建议
    category         VARCHAR(16)  NOT NULL,
    suggested_probability  INTEGER  NOT NULL,
    suggested_impact       INTEGER  NOT NULL,

    -- 状态机
    status            VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    model_version     VARCHAR(32)  NOT NULL DEFAULT 'rule-engine-v1.0',

    -- ML 增强
    ml_severity       VARCHAR(16),
    ml_confidence     NUMERIC(4,2),
    ml_predicted_at   TIMESTAMPTZ,

    -- LLM 总结
    llm_summary       VARCHAR(2000),

    -- 审计
    decided_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    applied_at        TIMESTAMPTZ,
    applied_by        BIGINT,

    -- SoftDeletableEntity 字段
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted           BOOLEAN      NOT NULL DEFAULT false,
    deleted_at        TIMESTAMPTZ,
    deleted_by        BIGINT
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_milestone_ai_advisory_project_id
    ON milestone_ai_advisory(project_id);
CREATE INDEX IF NOT EXISTS idx_milestone_ai_advisory_milestone_id
    ON milestone_ai_advisory(milestone_id);
CREATE INDEX IF NOT EXISTS idx_milestone_ai_advisory_status
    ON milestone_ai_advisory(status);
CREATE INDEX IF NOT EXISTS idx_milestone_ai_advisory_severity
    ON milestone_ai_advisory(severity);
CREATE INDEX IF NOT EXISTS idx_milestone_ai_advisory_decided_at
    ON milestone_ai_advisory(decided_at);
CREATE INDEX IF NOT EXISTS idx_milestone_ai_advisory_project_milestone
    ON milestone_ai_advisory(project_id, milestone_id);

-- 1 sibling 
ALTER TABLE milestone_ai_advisory ADD COLUMN IF NOT EXISTS applied_risk_id BIGINT;

-- Sibling 
ALTER TABLE milestone_ai_advisory ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMPTZ;
ALTER TABLE milestone_ai_advisory ADD COLUMN IF NOT EXISTS rejected_by BIGINT;
ALTER TABLE milestone_ai_advisory ADD COLUMN IF NOT EXISTS reject_reason VARCHAR(256);
ALTER TABLE milestone_ai_advisory ADD COLUMN IF NOT EXISTS feedback_type VARCHAR(16);
ALTER TABLE milestone_ai_advisory ADD COLUMN IF NOT EXISTS feedback_at TIMESTAMPTZ;
ALTER TABLE milestone_ai_advisory ADD COLUMN IF NOT EXISTS feedback_note VARCHAR(500);
ALTER TABLE milestone_ai_advisory ADD COLUMN IF NOT EXISTS fingerprint VARCHAR(64);
