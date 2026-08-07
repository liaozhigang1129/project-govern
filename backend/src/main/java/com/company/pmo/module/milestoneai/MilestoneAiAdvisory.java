package com.company.pmo.module.milestoneai;

import com.company.pmo.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** 里程碑 AI 智能分析 — 建议主表 (V4.1) */
@Entity
@Table(name = "milestone_ai_advisory", indexes = { @Index(name = "idx_maa_project", columnList = "project_id"), @Index(name = "idx_maa_milestone", columnList = "milestone_id"), @Index(name = "idx_maa_severity", columnList = "severity"), @Index(name = "idx_maa_status", columnList = "status"), @Index(name = "idx_maa_fingerprint", columnList = "fingerprint") })
@Getter @Setter @NoArgsConstructor
public class MilestoneAiAdvisory extends SoftDeletableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "milestone_id", nullable = false) private Long milestoneId;
    @Column(name = "phase_id") private Long phaseId;
    @Column(name = "phase_code", length = 32) private String phaseCode;
    @Column(name = "phase_name", length = 64) private String phaseName;
    @Column(name = "milestone_name", nullable = false, length = 128) private String milestoneName;
    @Column(name = "milestone_plan_date") private LocalDate milestonePlanDate;
    @Column(name = "milestone_status_code", length = 16) private String milestoneStatusCode;
    @Column(nullable = false, length = 16) private String severity;
    @Column(nullable = false, precision = 4, scale = 2) private BigDecimal score;
    @Column(nullable = false, precision = 4, scale = 2) private BigDecimal confidence;
    @Column(name = "signal_overdue", nullable = false, precision = 4, scale = 2) private BigDecimal signalOverdue;
    @Column(name = "signal_spi", nullable = false, precision = 4, scale = 2) private BigDecimal signalSpi;
    @Column(name = "signal_phase_lag", nullable = false, precision = 4, scale = 2) private BigDecimal signalPhaseLag;
    @Column(name = "signal_velocity", nullable = false, precision = 4, scale = 2) private BigDecimal signalVelocity;
    @Column(name = "signal_historical", nullable = false, precision = 4, scale = 2) private BigDecimal signalHistorical;
    @Column(name = "reasons_json", nullable = false, columnDefinition = "json") private String reasonsJson;
    @Column(name = "suggestions_json", nullable = false, columnDefinition = "json") private String suggestionsJson;
    @Column(nullable = false, length = 16) private String category;
    @Column(name = "suggested_probability", nullable = false) private Integer suggestedProbability;
    @Column(name = "suggested_impact", nullable = false) private Integer suggestedImpact;
    @Column(nullable = false, length = 16) private String status = "PENDING";
    @Column(name = "model_version", nullable = false, length = 32) private String modelVersion = "rule-engine-v1.0";
    @Column(name = "ml_severity", length = 16) private String mlSeverity;
    @Column(name = "ml_confidence", precision = 4, scale = 2) private java.math.BigDecimal mlConfidence;
    @Column(name = "ml_predicted_at") private Instant mlPredictedAt;
    @Column(name = "llm_summary", length = 2000) private String llmSummary;
    @Column(name = "decided_at", nullable = false) private Instant decidedAt = Instant.now();
    @Column(name = "applied_at") private Instant appliedAt;
    @Column(name = "applied_by") private Long appliedBy;
    @Column(name = "applied_risk_id") private Long appliedRiskId;
    @Column(name = "rejected_at") private Instant rejectedAt;
    @Column(name = "rejected_by") private Long rejectedBy;
    @Column(name = "reject_reason", length = 256) private String rejectReason;

    @Column(name = "feedback_type", length = 16)
    private String feedbackType;
    @Column(name = "feedback_at")
    private Instant feedbackAt;
    @Column(name = "feedback_note", length = 500)
    private String feedbackNote;
    @Column(length = 64) private String fingerprint;
}
