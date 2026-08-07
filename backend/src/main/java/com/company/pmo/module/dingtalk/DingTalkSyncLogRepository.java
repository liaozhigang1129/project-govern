package com.company.pmo.module.dingtalk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DingTalkSyncLogRepository extends JpaRepository<DingTalkSyncLog, Long> {
    /** 按 started_at 倒序, 取最近 N 条 */
    List<DingTalkSyncLog> findTop50ByOrderByStartedAtDesc();
}
