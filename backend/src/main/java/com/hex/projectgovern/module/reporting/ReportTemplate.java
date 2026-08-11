package com.hex.projectgovern.module.reporting;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "report_template")
@Getter @Setter @NoArgsConstructor
public class ReportTemplate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(unique = true, nullable = false, length = 64) private String code;
    @Column(nullable = false, length = 32) private String category;
    @Column(nullable = false, length = 128) private String name;
    @Column(name = "dataset_id") private Long datasetId;
    @Column(nullable = false, length = 16) private String format = "TABLE";
    @Column(name = "default_filters", columnDefinition = "JSON") private String defaultFilters;
    @Column(columnDefinition = "JSON") private String layout;
    @Column(name = "schedule_cron", length = 32) private String scheduleCron;
    @Column(nullable = false, length = 16) private String status = "DRAFT";
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
}
