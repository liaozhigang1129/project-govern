-- ============================================================
-- V4.2 finance F3 close-loop P1
-- 4 tables contract invoice payment cost_item
-- 3-way match: contract/invoice/payment
-- Project cost aggregation via cost_item
-- Idempotent via IF NOT EXISTS
-- ============================================================
CREATE TABLE IF NOT EXISTS contract (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(256) NOT NULL,
    vendor_id       BIGINT,
    vendor_name     VARCHAR(128),
    project_id      BIGINT,
    amount          NUMERIC(14,2) NOT NULL DEFAULT 0,
    status          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    sign_date       DATE,
    start_date      DATE,
    end_date        DATE,
    owner_user_id   BIGINT,
    remark          TEXT,
    deleted         BOOLEAN      NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_contract_code UNIQUE (code)
);
CREATE INDEX IF NOT EXISTS idx_contract_status  ON contract(status);
CREATE INDEX IF NOT EXISTS idx_contract_project ON contract(project_id);
CREATE INDEX IF NOT EXISTS idx_contract_owner   ON contract(owner_user_id);
COMMENT ON TABLE contract IS 'F3 contract ledger';

CREATE TABLE IF NOT EXISTS invoice (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(64)  NOT NULL,
    contract_id         BIGINT,
    vendor_id           BIGINT,
    vendor_name         VARCHAR(128),
    invoice_date        DATE         NOT NULL,
    amount              NUMERIC(14,2) NOT NULL DEFAULT 0,
    tax_amount          NUMERIC(14,2),
    total_amount        NUMERIC(14,2) NOT NULL DEFAULT 0,
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    match_strategy      VARCHAR(16),
    matched_at          DATE,
    matched_by_user_id  BIGINT,
    file_url            VARCHAR(512),
    remark              TEXT,
    deleted             BOOLEAN      NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_invoice_code UNIQUE (code)
);
CREATE INDEX IF NOT EXISTS idx_invoice_status   ON invoice(status);
CREATE INDEX IF NOT EXISTS idx_invoice_contract ON invoice(contract_id);
CREATE INDEX IF NOT EXISTS idx_invoice_date     ON invoice(invoice_date);
COMMENT ON TABLE invoice IS 'F3 invoice pool';

CREATE TABLE IF NOT EXISTS payment (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(64)  NOT NULL,
    invoice_id          BIGINT       NOT NULL,
    payment_date        DATE         NOT NULL,
    amount              NUMERIC(14,2) NOT NULL DEFAULT 0,
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    bank_ref            VARCHAR(128),
    approver_user_id    BIGINT,
    remark              TEXT,
    deleted             BOOLEAN      NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_code UNIQUE (code)
);
CREATE INDEX IF NOT EXISTS idx_payment_invoice ON payment(invoice_id);
CREATE INDEX IF NOT EXISTS idx_payment_status  ON payment(status);
COMMENT ON TABLE payment IS 'F3 payment ledger';

CREATE TABLE IF NOT EXISTS cost_item (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT       NOT NULL,
    type            VARCHAR(16)  NOT NULL,
    amount          NUMERIC(14,2) NOT NULL DEFAULT 0,
    date            DATE         NOT NULL,
    source          VARCHAR(16)  NOT NULL DEFAULT 'MANUAL',
    contract_id     BIGINT,
    invoice_id      BIGINT,
    payment_id      BIGINT,
    user_id         BIGINT,
    remark          TEXT,
    deleted         BOOLEAN      NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_cost_item_project_date ON cost_item(project_id, date);
CREATE INDEX IF NOT EXISTS idx_cost_item_contract     ON cost_item(contract_id);
CREATE INDEX IF NOT EXISTS idx_cost_item_invoice      ON cost_item(invoice_id);
CREATE INDEX IF NOT EXISTS idx_cost_item_payment      ON cost_item(payment_id);
CREATE INDEX IF NOT EXISTS idx_cost_item_type         ON cost_item(type);
COMMENT ON TABLE cost_item IS 'F3 project-level cost aggregation';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_invoice_contract_code') THEN
        ALTER TABLE invoice ADD CONSTRAINT uq_invoice_contract_code UNIQUE (contract_id, code);
    END IF;
END$$;
