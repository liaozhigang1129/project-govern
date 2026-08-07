-- P6 商机漏斗: 销售线索 → 合同
-- 阶段: LEAD → QUALIFIED → PROPOSAL → NEGOTIATION → WON/LOST

CREATE TABLE IF NOT EXISTS opportunity (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    code            VARCHAR(32) NOT NULL UNIQUE,
    name            VARCHAR(256) NOT NULL,
    customer_name   VARCHAR(128) NOT NULL,
    customer_contact VARCHAR(64),
    bu_id           BIGINT,
    pl_id           BIGINT,
    owner_user_id   BIGINT NOT NULL,
    stage           VARCHAR(16) NOT NULL DEFAULT 'LEAD',
    amount          DECIMAL(14,2) NOT NULL DEFAULT 0,
    cost_estimate   DECIMAL(14,2),
    probability     DECIMAL(4,2) NOT NULL DEFAULT 0.10,
    expected_close  DATE,
    actual_close    DATE,
    source          VARCHAR(32),
    lead_date       DATE NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    remark          VARCHAR(500),
    created_by      BIGINT NOT NULL,
    deleted         TINYINT(1) NOT NULL DEFAULT 0,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_opp_stage (stage),
    INDEX idx_opp_owner (owner_user_id),
    INDEX idx_opp_bu (bu_id),
    INDEX idx_opp_status (status),
    INDEX idx_opp_close (expected_close)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='P6 商机表';

CREATE TABLE IF NOT EXISTS opportunity_stage_history (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    opportunity_id  BIGINT NOT NULL,
    from_stage      VARCHAR(16),
    to_stage        VARCHAR(16) NOT NULL,
    amount          DECIMAL(14,2),
    probability     DECIMAL(4,2),
    changed_by      BIGINT NOT NULL,
    note            VARCHAR(256),
    deleted         TINYINT(1) NOT NULL DEFAULT 0,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_osh_opp (opportunity_id),
    INDEX idx_osh_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='P6 商机阶段历史';
