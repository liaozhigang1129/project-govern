package com.hex.projectgovern.module.reporting;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "report_subscription")
@Getter @Setter @NoArgsConstructor
public class ReportSubscription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(unique = true, nullable = false, length = 64) private String code;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "template_id") private Long templateId;
    @Column(name = "dashboard_id") private Long dashboardId;
    @Column(name = "channel_set", nullable = false, length = 64) private String channelSet;
    @Column(nullable = false, length = 32) private String cron;
    @Column(columnDefinition = "JSON") private String recipients;
    @Column(columnDefinition = "JSON") private String params;
    @Column(nullable = false, length = 16) private String status = "ACTIVE";
    @Column(name = "last_run_at") private Instant lastRunAt;
    @Column(name = "next_run_at") private Instant nextRunAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
}
