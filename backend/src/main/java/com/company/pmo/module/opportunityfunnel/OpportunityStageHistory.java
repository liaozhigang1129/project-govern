package com.company.pmo.module.opportunityfunnel;

import com.company.pmo.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** P6 商机阶段历史 */
@Entity
@Table(name = "opportunity_stage_history")
@Getter @Setter @NoArgsConstructor
public class OpportunityStageHistory extends SoftDeletableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "opportunity_id", nullable = false) private Long opportunityId;
    @Column(name = "from_stage", length = 16) private String fromStage;
    @Column(name = "to_stage", nullable = false, length = 16) private String toStage;
    @Column(precision = 14, scale = 2) private BigDecimal amount;
    @Column(precision = 4, scale = 2) private BigDecimal probability;
    @Column(name = "changed_by", nullable = false) private Long changedBy;
    @Column(length = 256) private String note;
}
