package com.company.zhiyu.module.initiation;

import com.company.zhiyu.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 立项阶段的人力资源派遣计划(Step 4)。
 * <p>对齐 V3.0 {@code initiation_resource_plan} 表。
 * <p>userId / roleCode 二选一:userId 表示具体人,roleCode 表示按角色批量派遣(例:前端工程师 × 2 人)。
 * <p>costAmount = planHours × hourlyRate × (allocationPct/100),由前端 UI 在保存时计算并写入。
 */
@Entity
@Table(name = "initiation_resource_plan")
@Getter @Setter @NoArgsConstructor
public class InitiationResourcePlan extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "initiation_id", nullable = false)
    private Long initiationId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "role_code", length = 32)
    private String roleCode;

    @Column(name = "allocation_pct", nullable = false)
    private Integer allocationPct = 100;

    @Column(name = "plan_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal planHours = BigDecimal.ZERO;

    @Column(name = "hourly_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal hourlyRate = BigDecimal.ZERO;

    @Column(name = "cost_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal costAmount = BigDecimal.ZERO;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by")
    private Long createdBy;
}
