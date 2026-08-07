-- V4.31 风险表软删除列补齐 (PG 版)
-- 背景: V4.26 建 risk_bucket/risk_signal/risk_template 时漏掉了 deleted 列
--        但实体 RiskBucket 继承 SoftDeletableEntity (含 deleted:boolean)
--        启动时 RiskRuleCache 报 Unknown column 'rb1_0.deleted', fallback 0 条规则
-- 修复: 三表统一添加 deleted BOOLEAN NOT NULL DEFAULT FALSE
--       与 MySQL 版 V4.31__risk_soft_delete_columns.sql 同步

-- risk_bucket
ALTER TABLE risk_bucket
    ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;

-- risk_signal
ALTER TABLE risk_signal
    ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;

-- risk_template
ALTER TABLE risk_template
    ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;

-- V4.31 end
