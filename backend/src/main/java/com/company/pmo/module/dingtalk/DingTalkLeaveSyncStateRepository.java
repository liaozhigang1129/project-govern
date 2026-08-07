package com.company.pmo.module.dingtalk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DingTalkLeaveSyncStateRepository extends JpaRepository<DingTalkLeaveSyncState, Long> {
    Optional<DingTalkLeaveSyncState> findBySyncKey(String syncKey);
}
