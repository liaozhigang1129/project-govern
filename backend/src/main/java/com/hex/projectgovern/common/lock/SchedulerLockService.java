package com.hex.projectgovern.common.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 分布式锁 (R-006, V6.4+).
 *
 * <p>基于 MySQL/PG scheduler_lock 表 (V6.4 迁移创建):
 * <pre>
 *   CREATE TABLE scheduler_lock (
 *       lock_name VARCHAR(64) PRIMARY KEY,
 *       holder_id VARCHAR(64) NOT NULL,
 *       holder_until TIMESTAMP NOT NULL,
 *       locked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 *       updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
 *   );
 * </pre>
 *
 * <p>使用:
 * <pre>
 *   if (!lockService.tryLock("alertScheduler", Duration.ofMinutes(4))) {
 *       log.info("locked by another pod, skip");
 *       return;
 *   }
 *   try {
 *       scan();
 *   } finally {
 *       lockService.release("alertScheduler");
 *   }
 * </pre>
 *
 * <p>策略:
 * <ul>
 *   <li>{@code tryLock(name, ttl)}: INSERT ... ON DUPLICATE KEY UPDATE 抢锁
 *     <ul>
 *       <li>行不存在 → 创建, 自己持有</li>
 *       <li>行存在 + holder_until < NOW() → 抢占, 自己持有</li>
 *       <li>行存在 + holder_until >= NOW() + holder_id == 自己 → 已持有, 续期</li>
 *       <li>其他情况 → 抢锁失败</li>
 *     </ul>
 *   </li>
 *   <li>{@code release(name)}: 仅当 holder_id == 自己才删除 (避免误删)</li>
 * </ul>
 *
 * <p>PG 兼容: 用 {@code ON CONFLICT (lock_name) DO UPDATE SET ... WHERE ...}
 *
 * <p>注: v5 计划切到 Redis SETNX (Redisson), 此 JDBC 实现是 v4 占位方案。
 *      Dev/CI 无 Redis 时也能用, 单实例不增加部署复杂度。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SchedulerLockService {

    private final JdbcTemplate jdbc;

    /** Pod 唯一标识 (启动时生成一次, 用于审计 holder_id) */
    @Value("${app.pod-id:local}")
    private String podId;

    private String holderUuid;

    private String currentHolderId() {
        if (holderUuid == null) {
            holderUuid = podId + "-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return holderUuid;
    }

    /**
     * 尝试抢锁 (含过期抢占 + 自己续期)。
     *
     * @return true = 锁持有成功 (含续期); false = 别人持有
     */
    public boolean tryLock(String lockName, Duration ttl) {
        String holderId = currentHolderId();
        Instant now = Instant.now();
        Instant holderUntil = now.plus(ttl);

        // MySQL 8: INSERT ... ON DUPLICATE KEY UPDATE
        // 兼容 PG: 用 ON CONFLICT (lock_name) DO UPDATE SET ... WHERE ...
        String sql = dml(lockName);
        Object[] args = new Object[]{lockName, holderId,
            java.sql.Timestamp.from(holderUntil),
            java.sql.Timestamp.from(now),
            java.sql.Timestamp.from(now)};

        int updated;
        try {
            updated = jdbc.update(sql, args);
        } catch (Exception e) {
            log.warn("[SchedulerLock] tryLock failed lock={} err={}", lockName, e.getMessage());
            return false;
        }
        // updated: 1=insert 新行, 2=update 已存在行 (MySQL 语义)
        // PG 的 ON CONFLICT DO UPDATE 返回 1
        return updated >= 1;
    }

    /**
     * 释放锁 (仅当 holder_id == 自己)。
     */
    public void release(String lockName) {
        String holderId = currentHolderId();
        String sql = "DELETE FROM scheduler_lock WHERE lock_name = ? AND holder_id = ?";
        int rows = jdbc.update(sql, lockName, holderId);
        if (rows == 0) {
            log.debug("[SchedulerLock] release skipped (not holder) lock={}", lockName);
        } else {
            log.info("[SchedulerLock] released lock={}", lockName);
        }
    }

    private String dml(String lockName) {
        // 探测数据库类型 (H2 用兼容模式)
        String dbName = jdbc.execute(
            (org.springframework.jdbc.core.ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
        boolean isPg = dbName != null && dbName.toLowerCase().contains("postgres");

        if (isPg) {
            return "INSERT INTO scheduler_lock (lock_name, holder_id, holder_until, locked_at, updated_at) " +
                   "VALUES (?, ?, ?, ?, ?) " +
                   "ON CONFLICT (lock_name) DO UPDATE SET " +
                   "  holder_id = EXCLUDED.holder_id, " +
                   "  holder_until = EXCLUDED.holder_until, " +
                   "  updated_at = EXCLUDED.updated_at " +
                   "WHERE scheduler_lock.holder_until < EXCLUDED.holder_until " +
                   "  OR scheduler_lock.holder_id = EXCLUDED.holder_id";
        }
        // MySQL / H2
        return "INSERT INTO scheduler_lock (lock_name, holder_id, holder_until, locked_at, updated_at) " +
               "VALUES (?, ?, ?, ?, ?) " +
               "ON DUPLICATE KEY UPDATE " +
               "  holder_id = IF(holder_until < VALUES(holder_until) OR holder_id = VALUES(holder_id), VALUES(holder_id), holder_id), " +
               "  holder_until = IF(holder_until < VALUES(holder_until) OR holder_id = VALUES(holder_id), VALUES(holder_until), holder_until), " +
               "  updated_at = IF(holder_until < VALUES(holder_until) OR holder_id = VALUES(holder_id), VALUES(updated_at), updated_at)";
    }
}