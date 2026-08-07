package com.company.pmo.module.initiation;

import com.company.pmo.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 立项阶段风险应对(及成本)。Step 5 用,独立于项目级 risk 模块,允许"先有立项风险"再演化为项目风险。
 * <p>对齐 V3.0 {@code initiation_risk_response} 表。
 */
@Entity
@Table(name = "initiation_risk_response")
@Getter @Setter @NoArgsConstructor
public class InitiationRiskResponse extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "initiation_id", nullable = false)
    private Long initiationId;

    /** 可空:从 Step 2 AI 草稿导入时已先有 risk_id,手填的可以空 */
    @Column(name = "risk_id")
    private Long riskId;

    @Column(name = "risk_title", nullable = false, length = 256)
    private String riskTitle;

    @Column(name = "risk_level", nullable = false, length = 16)
    private String riskLevel = "MEDIUM";   // LOW / MEDIUM / HIGH / CRITICAL

    @Column(name = "response_action", nullable = false, columnDefinition = "text")
    private String responseAction;

    @Column(name = "response_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal responseCost = BigDecimal.ZERO;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "PLANNED";     // PLANNED / IN_PROGRESS / DONE / CANCELLED

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by")
    private Long createdBy;
}
