-- ============================================================
-- V4.2 finance F3 close-loop P1 (MySQL 版)
-- 4 tables contract invoice payment cost_item
-- 与 PG 版同构, 差异:
--   - TIMESTAMPTZ → DATETIME(6)
--   - BOOLEAN → TINYINT(1)
--   - NUMERIC → DECIMAL
--   - 去掉 IF NOT EXISTS, CREATE TABLE 本身就是 idempotent via IF NOT EXISTS (支持)
--   - CREATE INDEX IF NOT EXISTS → 用存储过程
--   - DO $$ → 存储过程
-- ============================================================

CREATE TABLE IF NOT EXISTS contract (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(256) NOT NULL,
    vendor_id       BIGINT,
    vendor_name     VARCHAR(128),
    project_id      BIGINT,
    amount          DECIMAL(14,2) NOT NULL DEFAULT 0,
    status          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    sign_date       DATE,
    start_date      DATE,
    end_date        DATE,
    owner_user_id   BIGINT,
    remark          TEXT,
    deleted         TINYINT(1)   NOT NULL DEFAULT 0,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_contract_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F3 contract ledger';

CREATE INDEX idx_contract_status  ON contract(status);
CREATE INDEX idx_contract_project ON contract(project_id);
CREATE INDEX idx_contract_owner   ON contract(owner_user_id);

CREATE TABLE IF NOT EXISTS invoice (
    id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code                VARCHAR(64)  NOT NULL,
    contract_id         BIGINT,
    vendor_id           BIGINT,
    vendor_name         VARCHAR(128),
    invoice_date        DATE         NOT NULL,
    amount              DECIMAL(14,2) NOT NULL DEFAULT 0,
    tax_amount          DECIMAL(14,2),
    total_amount        DECIMAL(14,2) NOT NULL DEFAULT 0,
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    match_strategy      VARCHAR(16),
    matched_at          DATE,
    matched_by_user_id  BIGINT,
    file_url            VARCHAR(512),
    remark              TEXT,
    deleted             TINYINT(1)   NOT NULL DEFAULT 0,
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_invoice_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F3 invoice pool';

CREATE INDEX idx_invoice_status   ON invoice(status);
CREATE INDEX idx_invoice_contract ON invoice(contract_id);
CREATE INDEX idx_invoice_date     ON invoice(invoice_date);

CREATE TABLE IF NOT EXISTS payment (
    id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code                VARCHAR(64)  NOT NULL,
    invoice_id          BIGINT       NOT NULL,
    payment_date        DATE         NOT NULL,
    amount              DECIMAL(14,2) NOT NULL DEFAULT 0,
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    bank_ref            VARCHAR(128),
    approver_user_id    BIGINT,
    remark              TEXT,
    deleted             TINYINT(1)   NOT NULL DEFAULT 0,
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_payment_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F3 payment ledger';

CREATE INDEX idx_payment_invoice ON payment(invoice_id);
CREATE INDEX idx_payment_status  ON payment(status);

CREATE TABLE IF NOT EXISTS cost_item (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id      BIGINT       NOT NULL,
    type            VARCHAR(16)  NOT NULL,
    amount          DECIMAL(14,2) NOT NULL DEFAULT 0,
    date            DATE         NOT NULL,
    source          VARCHAR(16)  NOT NULL DEFAULT 'MANUAL',
    contract_id     BIGINT,
    invoice_id      BIGINT,
    payment_id      BIGINT,
    user_id         BIGINT,
    remark          TEXT,
    deleted         TINYINT(1)   NOT NULL DEFAULT 0,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F3 project-level cost aggregation';

CREATE INDEX idx_cost_item_project_date ON cost_item(project_id, date);
CREATE INDEX idx_cost_item_contract     ON cost_item(contract_id);
CREATE INDEX idx_cost_item_invoice      ON cost_item(invoice_id);
CREATE INDEX idx_cost_item_payment      ON cost_item(payment_id);
CREATE INDEX idx_cost_item_type         ON cost_item(type);

-- invoice 联合唯一 (contract_id, code)
SET @db = DATABASE();
SET @con = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=@db AND TABLE_NAME='invoice' AND CONSTRAINT_NAME='uq_invoice_contract_code');
SET @stmt = IF(@con=0, 'ALTER TABLE invoice ADD CONSTRAINT uq_invoice_contract_code UNIQUE (contract_id, code)', 'SELECT 1');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;
