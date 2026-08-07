-- ============================================================
-- V4.5: F4 修复 milestone_ai_feedback 表缺失
-- 原因: MilestoneAiFeedback 实体 (sibling) 期望这张表
-- 幂等 (IF NOT EXISTS): 可重复跑
-- ============================================================
CREATE TABLE IF NOT EXISTS milestone_ai_feedback (
    id BIGSERIAL PRIMARY KEY,
    advisory_id     BIGINT       NOT NULL,
    feedback_type   VARCHAR(16)  NOT NULL,
    reason_code     VARCHAR(32),
    comment         VARCHAR(500),
    feedback_by     BIGINT       NOT NULL,
    feedback_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    model_version   VARCHAR(32)  NOT NULL,
    ip_address      VARCHAR(45),
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  DEFAULT now(),
    updated_at      TIMESTAMPTZ  DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_milestone_ai_feedback_advisory
    ON milestone_ai_feedback(advisory_id);
CREATE INDEX IF NOT EXISTS idx_milestone_ai_feedback_type
    ON milestone_ai_feedback(feedback_type);
