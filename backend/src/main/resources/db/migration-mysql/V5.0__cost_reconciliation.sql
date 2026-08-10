-- ============================================================
-- V5.0 财务-成本 3-way match 对账 (MySQL 版)
-- 与 PostgreSQL 版 V5.0__cost_reconciliation.sql 同构
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
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id      BIGINT        NOT NULL,

    -- 4 个关联维度 (任一可空)
    contract_id     BIGINT        NULL,
    invoice_id      BIGINT        NULL,
    payment_id      BIGINT        NULL,
    cost_item_id    BIGINT        NULL,

    -- 对账期间 (YYYY-MM)
    period          CHAR(7)       NOT NULL,

    -- 4 个金额
    contract_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
    invoice_amount  DECIMAL(14,2) NOT NULL DEFAULT 0,
    payment_amount  DECIMAL(14,2) NOT NULL DEFAULT 0,
    cost_amount     DECIMAL(14,2) NOT NULL DEFAULT 0,

    -- 差异
    diff_amount     DECIMAL(14,2) NOT NULL DEFAULT 0,
    diff_reason     TEXT          NULL,

    -- 状态
    match_status    VARCHAR(16)   NOT NULL DEFAULT 'PENDING',

    -- 时间戳
    reconciled_at   DATETIME(6)   NULL,
    reconciled_by   BIGINT        NULL,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    UNIQUE KEY uk_cost_recon (project_id, contract_id, invoice_id, payment_id, cost_item_id, period),
    KEY idx_cost_recon_project_status (project_id, match_status),
    KEY idx_cost_recon_reconciled_at  (reconciled_at),
    KEY idx_cost_recon_project_period (project_id, period),
    KEY idx_cost_recon_status_diff    (match_status, diff_amount),

    CONSTRAINT fk_cost_recon_project  FOREIGN KEY (project_id)  REFERENCES project(id)  ON DELETE CASCADE,
    CONSTRAINT fk_cost_recon_contract FOREIGN KEY (contract_id) REFERENCES contract(id) ON DELETE SET NULL,
    CONSTRAINT fk_cost_recon_invoice  FOREIGN KEY (invoice_id)  REFERENCES invoice(id)  ON DELETE SET NULL,
    CONSTRAINT fk_cost_recon_payment  FOREIGN KEY (payment_id)  REFERENCES payment(id)  ON DELETE SET NULL,
    CONSTRAINT fk_cost_recon_costitem FOREIGN KEY (cost_item_id) REFERENCES cost_item(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='财务-成本 3-way match 对账快照 (V5.0)';
