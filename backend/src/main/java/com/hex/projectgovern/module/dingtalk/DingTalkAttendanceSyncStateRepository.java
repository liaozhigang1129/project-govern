package com.hex.projectgovern.module.dingtalk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DingTalkAttendanceSyncStateRepository extends JpaRepository<DingTalkAttendanceSyncState, Long> {
    Optional<DingTalkAttendanceSyncState> findBySyncKey(String syncKey);
}
