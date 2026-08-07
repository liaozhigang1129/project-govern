package com.company.pmo.module.milestoneai;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** 5 维信号明细行 (V4.1) */
@Entity
@Table(name = "milestone_ai_signal", indexes = { @Index(name = "idx_mas_advisory", columnList = "advisory_id"), @Index(name = "idx_mas_type", columnList = "signal_type") })
@Getter @Setter @NoArgsConstructor
public class MilestoneAiSignal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "advisory_id", nullable = false) private Long advisoryId;
    @Column(name = "signal_type", nullable = false, length = 32) private String signalType;
    @Column(nullable = false, precision = 4, scale = 2) private BigDecimal intensity;
    @Column(nullable = false, precision = 4, scale = 2) private BigDecimal weight;
    @Column(nullable = false, precision = 4, scale = 2) private BigDecimal score;
    @Column(nullable = false, length = 512) private String description;
    @Column(nullable = false) private boolean missing;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
}
