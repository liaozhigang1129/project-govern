package com.company.pmo.module.dingtalk;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "dingtalk_leave_sync_state")
@Getter
@Setter
public class DingTalkLeaveSyncState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sync_key", nullable = false, unique = true, length = 64)
    private String syncKey;

    @Column(name = "last_sync_time", nullable = false)
    private Instant lastSyncTime;

    @Column(name = "last_total", nullable = false)
    private Integer lastTotal = 0;

    @Column(name = "last_created", nullable = false)
    private Integer lastCreated = 0;

    @Column(name = "last_updated", nullable = false)
    private Integer lastUpdated = 0;

    @Column(name = "last_deleted", nullable = false)
    private Integer lastDeleted = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
