package com.company.zhiyu.module.dingtalk;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DingTalkLeaveSyncLogRepository extends JpaRepository<DingTalkLeaveSyncLog, Long> {
    Page<DingTalkLeaveSyncLog> findAllByOrderByStartedAtDesc(Pageable pageable);
}
