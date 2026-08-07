-- ============================================================
-- V2.7 P4 风险模块补丁
--   目的: 给 risk_history.action CHECK 加 DELETED 枚举值
--   背景: V2.6 Service 用 STATUS_CHANGED 写"软删"语义冲突, V2.7 拆出来
--   数据: 不动旧数据, 仅扩 CHECK
-- ============================================================

ALTER TABLE risk_history DROP CONSTRAINT IF EXISTS ck_risk_hist_action;
ALTER TABLE risk_history ADD CONSTRAINT ck_risk_hist_action CHECK (action IN (
    'CREATED','STATUS_CHANGED','SCORE_CHANGED','OWNER_CHANGED',
    'LEVEL_CHANGED','COMMENTED','RESPONSE_ADDED','RESPONSE_DONE',
    'DELETED'
));
COMMENT ON CONSTRAINT ck_risk_hist_action ON risk_history IS
    '风险历史 action 枚举: CREATED/STATUS_CHANGED/SCORE_CHANGED/OWNER_CHANGED/LEVEL_CHANGED/COMMENTED/RESPONSE_ADDED/RESPONSE_DONE/DELETED';
