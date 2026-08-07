-- ============================================================
-- V2.7 P4 风险模块补丁 (MySQL 版)
--   目的: risk_history.action CHECK 加 DELETED 枚举
--   注意: MySQL 8.0.16+ 才支持 CHECK 强制, 之前会忽略但语法合法
--   V2.6 (MySQL) 没建过 CHECK (依赖 app 层校验), 这里保持一致
-- ============================================================

-- MySQL 没建过 CHECK, 单纯是 enum 字符串. 改字段类型用 MODIFY 把 DELETED 加进去.
-- 但 VARCHAR 不需要显式枚举, 校验由 Service 层把控.
-- 这里只加一个 index 给历史查询用 (action 类型过滤, 给后台审计页面可能用)
ALTER TABLE risk_history ADD INDEX idx_risk_hist_action (action);
