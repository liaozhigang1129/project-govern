-- V4.31 风险表软删除列补齐 (MySQL 版)
-- 背景: V4.26 建 risk_bucket/risk_signal/risk_template 时漏掉了 deleted 列
--        但实体 RiskBucket 继承 SoftDeletableEntity (含 deleted:boolean)
--        启动时 RiskRuleCache 报 Unknown column 'rb1_0.deleted', fallback 0 条规则
-- 修复: 三表统一添加 deleted TINYINT(1) NOT NULL DEFAULT 0
--       与 PG 版 V4.31__risk_soft_delete_columns.sql 同步
-- 安全: IF NOT EXISTS 在 MySQL 8+ 支持; 旧版本可去掉 IF NOT EXISTS 直接 ALTER

-- risk_bucket
ALTER TABLE risk_bucket
    ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0
    COMMENT '软删除标记 (V4.31 补齐, 与 SoftDeletableEntity 对齐)';

-- risk_signal
ALTER TABLE risk_signal
    ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0
    COMMENT '软删除标记 (V4.31 补齐, 与 SoftDeletableEntity 对齐)';

-- risk_template
ALTER TABLE risk_template
    ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0
    COMMENT '软删除标记 (V4.31 补齐, 与 SoftDeletableEntity 对齐)';

-- V4.31 end
