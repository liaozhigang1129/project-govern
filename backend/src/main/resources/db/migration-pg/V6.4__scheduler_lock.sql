-- ============================================================
-- V6.4 分布式锁表 (R-006, 多实例 AlertScheduler 协调) — PG 版
-- ============================================================

CREATE TABLE scheduler_lock (
    lock_name      VARCHAR(64)  NOT NULL PRIMARY KEY,
    holder_id      VARCHAR(64)  NOT NULL,
    holder_until   TIMESTAMPTZ  NOT NULL,
    locked_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE scheduler_lock IS '分布式锁 (R-006 多实例调度协调)';

CREATE INDEX idx_sl_holder_until ON scheduler_lock(holder_until);