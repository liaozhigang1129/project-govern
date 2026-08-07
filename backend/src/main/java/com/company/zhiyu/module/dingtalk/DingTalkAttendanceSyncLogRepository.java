package com.company.zhiyu.module.dingtalk;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface DingTalkAttendanceSyncLogRepository extends JpaRepository<DingTalkAttendanceSyncLog, Long> {
    Page<DingTalkAttendanceSyncLog> findAllByOrderByStartedAtDesc(Pageable pageable);

    /**
     * V4.35: stale guard
     * 找所有 status='RUNNING' 但 started_at 早于 cutoff 的 log
     * 用于 syncNow 启动时清理"上一进程被杀/崩溃"留下的僵尸 RUNNING 行
     */
    @Query("SELECT l FROM DingTalkAttendanceSyncLog l " +
            "WHERE l.status = 'RUNNING' AND l.startedAt < :cutoff")
    List<DingTalkAttendanceSyncLog> findStaleRunning(@Param("cutoff") Instant cutoff);
}
