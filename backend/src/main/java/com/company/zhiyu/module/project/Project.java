package com.company.zhiyu.module.project;

import com.company.zhiyu.common.entity.SoftDeletableEntity;
import com.company.zhiyu.module.dict.HealthLevel;
import com.company.zhiyu.module.dict.ProjectStatus;
import com.company.zhiyu.module.dict.ProjectType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;

@Entity
@Table(name = "project")
@Getter @Setter @NoArgsConstructor
public class Project extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "type_id", nullable = false)
    private ProjectType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    private ProjectStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "health_id")
    private HealthLevel health;

    @Column(length = 128)
    private String customer;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "pm_user_id")
    private Long pmUserId;

    @Column(name = "sponsor_user_id")
    private Long sponsorUserId;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String background;

    @Column(columnDefinition = "text")
    private String goals;

    @Column(columnDefinition = "text")
    private String scope;

    @Column(name = "plan_start_date")
    private LocalDate planStartDate;

    @Column(name = "plan_end_date")
    private LocalDate planEndDate;

    @Column(name = "actual_start_date")
    private LocalDate actualStartDate;

    @Column(name = "actual_end_date")
    private LocalDate actualEndDate;

    @Column(name = "plan_workdays")
    private Integer planWorkdays;

    @Column(name = "progress_pct", nullable = false)
    private int progressPct = 0;

    @Column(name = "budget_estimate", precision = 14, scale = 2)
    private BigDecimal budgetEstimate;

    // ==================== EVM (P3 挣值分析冗余字段) ====================
    @Column(name = "bac", precision = 14, scale = 2)
    private BigDecimal bac;

    @Column(name = "evm_cpi", precision = 6, scale = 3)
    private BigDecimal evmCpi;

    @Column(name = "evm_spi", precision = 6, scale = 3)
    private BigDecimal evmSpi;

    @Column(name = "evm_eac", precision = 14, scale = 2)
    private BigDecimal evmEac;

    @Column(name = "evm_etc", precision = 14, scale = 2)
    private BigDecimal evmEtc;

    @Column(name = "evm_vac", precision = 14, scale = 2)
    private BigDecimal evmVac;

    @Column(name = "evm_updated_at")
    private Instant evmUpdatedAt;

    @Column(name = "baseline_version", nullable = false)
    private int baselineVersion = 0;

    @Column(name = "baseline_frozen_at")
    private Instant baselineFrozenAt;

    @Column(name = "baseline_frozen_by")
    private Long baselineFrozenBy;

    // V4.17 立项基础信息补全
    @Column(name = "project_level_code", length = 32) private String projectLevelCode;
    @Column(name = "expected_gross_margin_pct", precision = 5, scale = 2) private BigDecimal expectedGrossMarginPct;
    @Column(name = "planned_launch_date") private LocalDate plannedLaunchDate;
    @Column(name = "related_product_id")
    private Long relatedProductId;

    @Column(name = "bu_id")
    private Long buId;

    @Column(name = "pl_id")
    private Long plId;

    @Column(name = "created_by")
    private Long createdBy;
}
