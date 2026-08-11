package com.hex.projectgovern.module.reporting;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "dataset")
@Getter @Setter @NoArgsConstructor
public class Dataset {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(unique = true, nullable = false, length = 64) private String code;
    @Column(nullable = false, length = 128) private String name;
    @Column(nullable = false, length = 32) private String domain;
    @Column(name = "source_table", length = 64) private String sourceTable;
    @Column(name = "sql_template", columnDefinition = "TEXT") private String sqlTemplate;
    @Column(name = "refresh_policy", nullable = false, length = 16) private String refreshPolicy = "MANUAL";
    @Column(name = "last_refresh_at") private Instant lastRefreshAt;
    @Column(nullable = false, length = 16) private String status = "DRAFT";
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
}
