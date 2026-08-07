package com.company.zhiyu.module.dingtalk;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "dingtalk_attendance_sync_log")
@Getter
@Setter
public class DingTalkAttendanceSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "trigger_type", nullable = false, length = 16)
    private String triggerType;

    @Column(name = "triggered_by", nullable = false, length = 64)
    private String triggeredBy;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "RUNNING";

    @Column(name = "sync_mode", nullable = false, length = 16)
    private String syncMode = "INCREMENTAL";

    @Column(name = "range_from")
    private Instant rangeFrom;

    @Column(name = "range_to")
    private Instant rangeTo;

    @Column(name = "last_sync_time")
    private Instant lastSyncTime;

    @Column(name = "fetched", nullable = false)
    private Integer fetched = 0;

    @Column(name = "created_count", nullable = false)
    private Integer createdCount = 0;

    @Column(name = "updated_count", nullable = false)
    private Integer updatedCount = 0;

    @Column(name = "deleted_count", nullable = false)
    private Integer deletedCount = 0;

    @Column(name = "skipped_count", nullable = false)
    private Integer skippedCount = 0;

    @Lob
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Lob
    @Column(name = "error_detail", columnDefinition = "TEXT")
    private String errorDetail;
}
