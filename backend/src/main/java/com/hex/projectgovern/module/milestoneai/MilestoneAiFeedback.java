package com.hex.projectgovern.module.milestoneai;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** P5-PM 反馈闭环 (审计 + 增量学习源) */
@Entity
@Table(name = "milestone_ai_feedback")
@Getter @Setter @NoArgsConstructor
public class MilestoneAiFeedback {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "advisory_id", nullable = false)
    private Long advisoryId;

    /** 反馈类型: ACCEPTED=采纳, REJECTED=拒绝, MISLEAD=误报, EXPIRED=过期 */
    @Column(name = "feedback_type", nullable = false, length = 16)
    private String feedbackType;

    /** 拒绝原因 code: NOISY_RULE / DATA_ERROR / MODEL_BIAS / UPGRADED / OTHER */
    @Column(name = "reason_code", length = 32)
    private String reasonCode;

    @Column(length = 500)
    private String comment;

    @Column(name = "feedback_by", nullable = false)
    private Long feedbackBy;

    @Column(name = "feedback_at", nullable = false)
    private Instant feedbackAt = Instant.now();

    @Column(name = "model_version", nullable = false, length = 32)
    private String modelVersion;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(nullable = false)
    private Byte deleted = 0;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();
}
