-- P5-PM 反馈闭环: 反馈表 + advisory 3 列
CREATE TABLE milestone_ai_feedback (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    advisory_id     BIGINT       NOT NULL,
    feedback_type   VARCHAR(16)  NOT NULL COMMENT 'ACCEPTED|REJECTED|MISLEAD|EXPIRED',
    reason_code     VARCHAR(32)  COMMENT 'NOISY_RULE|DATA_ERROR|MODEL_BIAS|UPGRADED|OTHER',
    comment         VARCHAR(500),
    feedback_by     BIGINT       NOT NULL,
    feedback_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    model_version   VARCHAR(32)  NOT NULL,
    ip_address      VARCHAR(45),
    deleted         TINYINT      NOT NULL DEFAULT 0,
    created_at      DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_mafb_advisory (advisory_id),
    INDEX idx_mafb_type (feedback_type),
    INDEX idx_mafb_at (feedback_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='P5-PM 反馈闭环';

ALTER TABLE milestone_ai_advisory
    ADD COLUMN feedback_type VARCHAR(16) COMMENT 'ACCEPTED|REJECTED|MISLEAD|EXPIRED' AFTER reject_reason,
    ADD COLUMN feedback_at   DATETIME(3) COMMENT 'PM 反馈时间'  AFTER feedback_type,
    ADD COLUMN feedback_note VARCHAR(500) COMMENT 'PM 反馈备注' AFTER feedback_at;
