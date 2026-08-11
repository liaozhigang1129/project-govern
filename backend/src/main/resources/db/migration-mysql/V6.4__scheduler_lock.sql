-- ============================================================
-- V6.4 分布式锁表 (R-006, 多实例 AlertScheduler 协调)
-- ============================================================
--
-- 业务说明:
--  - R-006 多 pod 部署时, 多个 AlertScheduler 同时跑 scan() → 重复告警
--  - 解决方案: scheduler_lock 表存 (lock_name → holder_until)
--    * INSERT ... ON DUPLICATE KEY UPDATE 抢锁
--    * holder_until < NOW() 视为过期, 可抢
--    * 否则视为别人占用, 跳过本轮
--
-- 行级锁粒度: 每 (lock_name) 1 行, 业务各自取名
-- 过期时间: holder_until - NOW() ≥ 4 分钟 (lockAtMostFor 兜底)
--
-- 字段:
--   lock_name      VARCHAR(64) PRIMARY KEY
--   holder_id      VARCHAR(64)  抢占者标识 (pod name + uuid)
--   holder_until   TIMESTAMP    持有截止时间
--   locked_at      TIMESTAMP    抢占时间 (审计)
--   updated_at     TIMESTAMP    最后刷新时间 (监控)
--
-- 用途:
--   1. AlertScheduler.scheduledScan (每 5 分钟, 锁 4 分钟兜底)
--   2. 未来其他调度任务 (ReconciliationService 月度结算等)

CREATE TABLE scheduler_lock (
    lock_name      VARCHAR(64)  NOT NULL PRIMARY KEY,
    holder_id      VARCHAR(64)  NOT NULL,
    holder_until   TIMESTAMP    NOT NULL,
    locked_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='分布式锁 (R-006 多实例调度协调)';

-- 索引 (按过期时间监控用)
CREATE INDEX idx_sl_holder_until ON scheduler_lock(holder_until);