-- P5-ML/LLM 增强: 智能预警 advisory 表加 4 列
--   ml_severity    VARCHAR(16)   ← ML 模型预测 (INFO/WARNING/CRITICAL)
--   ml_confidence  DECIMAL(4,2)  ← ML 置信度 0-1
--   ml_predicted_at DATETIME     ← 预测时间
--   llm_summary    VARCHAR(2000) ← LLM 润色后总结
ALTER TABLE milestone_ai_advisory
    ADD COLUMN ml_severity    VARCHAR(16)   COMMENT 'ML 模型预测 (INFO/WARNING/CRITICAL)'           AFTER model_version,
    ADD COLUMN ml_confidence  DECIMAL(4,2)  COMMENT 'ML 置信度 0-1'                                  AFTER ml_severity,
    ADD COLUMN ml_predicted_at DATETIME(3)  COMMENT 'ML 预测时间'                                    AFTER ml_confidence,
    ADD COLUMN llm_summary    VARCHAR(2000) COMMENT 'LLM 润色后总结 (Markdown)'                       AFTER ml_predicted_at;

ALTER TABLE milestone_ai_advisory
    ADD INDEX idx_maa_ml_severity (ml_severity);
