package com.company.pmo.module.resourcepipeline;

import com.company.pmo.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** P6 资源管道事件: 资源调度审计 */
@Entity
@Table(name = "resource_pipeline_event")
@Getter @Setter @NoArgsConstructor
public class ResourcePipelineEvent extends SoftDeletableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "from_status", length = 16) private String fromStatus;
    @Column(name = "to_status", nullable = false, length = 16) private String toStatus;
    @Column(name = "allocation_pct", nullable = false, precision = 5, scale = 2) private BigDecimal allocationPct;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date") private LocalDate endDate;
    @Column(name = "decided_by", nullable = false) private Long decidedBy;
    @Column(length = 256) private String reason;
}
