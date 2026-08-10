-- ============================================================
-- V5.0 财务-成本 3-way match 对账 (PostgreSQL 版)
-- 与 MySQL 版 V5.0__cost_reconciliation.sql 同构
--
-- 设计:
--   cost_reconciliation: 一条记录 = (project, contract?, invoice?, payment?, cost_item?)
--                        按 project_id 聚合, 每条对账 (matched|partial|mismatch|pending) 一行
--                        diff_amount = |签约额 - 开票额 - 实付额 - 入账额| 残差
--   match_status:
--     MATCHED   签约 = 开票 = 实付 = 入账 (容差 ¥0.01)
--     PARTIAL   部分维度缺失/存在但金额不等,无重大差异
--     MISMATCH  金额差异 > 阈值 (默认 ¥100), 需告警
--     PENDING   维度不足,待补齐 (例如合同尚未签订)
--   幂等键: (project_id, contract_id, invoice_id, payment_id, cost_item_id, period)
--           period = YYYY-MM (基于 cost_item.date / invoice.invoice_date 取月份)
-- ============================================================

CREATE TABLE IF NOT EXISTS cost_reconciliation (
    id              BIGSERIAL    PRIMARY KEY,
    project_id      BIGINT       NOT NULL REFERENCES project(id) ON DELETE CASCADE,

    -- 4 个关联维度 (任一可空)
    contract_id     BIGINT       NULL REFERENCES contract(id) ON DELETE SET NULL,
    invoice_id      BIGINT       NULL REFERENCES invoice(id)  ON DELETE SET NULL,
    payment_id      BIGINT       NULL REFERENCES payment(id)  ON DELETE SET NULL,
    cost_item_id    BIGINT       NULL REFERENCES cost_item(id) ON DELETE SET NULL,

    -- 对账期间 (YYYY-MM)
    period          VARCHAR(7)   NOT NULL,

    -- 4 个金额 (合同额 / 开票价税合计 / 实付额 / 入账成本)
    contract_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    invoice_amount  NUMERIC(14,2) NOT NULL DEFAULT 0,
    payment_amount  NUMERIC(14,2) NOT NULL DEFAULT 0,
    cost_amount     NUMERIC(14,2) NOT NULL DEFAULT 0,

    -- 差异
    diff_amount     NUMERIC(14,2) NOT NULL DEFAULT 0,            -- 绝对差 (max - min)
    diff_reason     TEXT          NULL,

    -- 状态
    match_status    VARCHAR(16)   NOT NULL DEFAULT 'PENDING'
                    CHECK (match_status IN ('MATCHED','PARTIAL','MISMATCH','PENDING')),

    -- 时间戳
    reconciled_at   TIMESTAMPTZ   NULL,
    reconciled_by   BIGINT        NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    -- 幂等键 (同 project 同 period 同 4 维组合只一条)
    CONSTRAINT uk_cost_recon UNIQUE (project_id, contract_id, invoice_id, payment_id, cost_item_id, period)
);

CREATE INDEX IF NOT EXISTS idx_cost_recon_project_status ON cost_reconciliation(project_id, match_status);
CREATE INDEX IF NOT EXISTS idx_cost_recon_reconciled_at  ON cost_reconciliation(reconciled_at DESC);
CREATE INDEX IF NOT EXISTS idx_cost_recon_project_period ON cost_reconciliation(project_id, period);
CREATE INDEX IF NOT EXISTS idx_cost_recon_status_diff    ON cost_reconciliation(match_status, diff_amount);

COMMENT ON TABLE cost_reconciliation IS '财务-成本 3-way match 对账快照 (V5.0)';
COMMENT ON COLUMN cost_reconciliation.match_status IS 'MATCHED|PARTIAL|MISMATCH|PENDING';
COMMENT ON COLUMN cost_reconciliation.diff_amount  IS '4 维最大最小差绝对值, > ¥100 触发告警';
