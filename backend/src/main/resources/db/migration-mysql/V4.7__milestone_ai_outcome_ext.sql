-- P5-ML: outcome 表加 3 列 (PM 反馈写入需要)
ALTER TABLE milestone_ai_outcome
    ADD COLUMN features_json  TEXT         COMMENT '18 维特征 JSON 快照' AFTER outcome_reason,
    ADD COLUMN model_version  VARCHAR(32)  COMMENT '模型版本'          AFTER features_json,
    ADD COLUMN updated_at     DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER created_at;

CREATE INDEX idx_maot_model ON milestone_ai_outcome(model_version);
