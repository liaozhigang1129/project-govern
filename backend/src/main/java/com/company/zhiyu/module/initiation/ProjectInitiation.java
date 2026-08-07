package com.company.zhiyu.module.initiation;

import com.company.zhiyu.common.entity.SoftDeletableEntity;
import com.company.zhiyu.module.dict.InitiationStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;

@Entity
@Table(name = "project_initiation")
@Getter @Setter @NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ProjectInitiation extends SoftDeletableEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 32) private String code;
    @Column(name = "project_id") private Long projectId;
    @Column(nullable = false, length = 256) private String title;
    @Column(name = "applicant_id", nullable = false) private Long applicantId;
    @Column(name = "department_id") private Long departmentId;
    // V4.17: 项目经理 (与申请人可不同, 立项后正式 PM; applicant 通常是 SR, pm 可能是另指派)
    @Column(name = "pm_user_id") private Long pmUserId;
    // V4.17: 项目类型 / 项目级别 / 预估毛利率 / 计划上线时间
    @Column(name = "project_type_code", length = 32) private String projectTypeCode;
    @Column(name = "project_level_code", length = 32) private String projectLevelCode;
    @Column(name = "expected_gross_margin_pct", precision = 5, scale = 2) private BigDecimal expectedGrossMarginPct;
    @Column(name = "planned_launch_date") private LocalDate plannedLaunchDate;

    // V4.18: background/goals/scope 改为可选,避免创建立项时因未填导致 INSERT 失败
    @Column(nullable = true, columnDefinition = "text") private String background;
    @Column(nullable = true, columnDefinition = "text") private String goals;
    @Column(nullable = true, columnDefinition = "text") private String scope;
    @Column(name = "plan_workdays") private Integer planWorkdays;
    @Column(name = "budget_estimate", precision = 14, scale = 2) private BigDecimal budgetEstimate;
    @Column(name = "planned_start") private LocalDate plannedStart;
    @Column(name = "planned_end") private LocalDate plannedEnd;
    @Column(name = "initial_risks", columnDefinition = "text") private String initialRisks;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    private InitiationStatus status;

    @Column(name = "current_step", length = 32) private String currentStep;
    @Column(name = "submitted_at") private Instant submittedAt;
    @Column(name = "closed_at") private Instant closedAt;

    // ---- V3.0 立项全流程增强字段 ----
    @Column(name = "sow_required", nullable = false) private boolean sowRequired = true;
    @Column(name = "sow_received", nullable = false) private boolean sowReceived = false;
    @Column(name = "sow_paste_text", columnDefinition = "text") private String sowPasteText;
    @Column(name = "contract_amount", precision = 14, scale = 2) private BigDecimal contractAmount;
    @Column(name = "contract_currency", length = 8) private String contractCurrency = "CNY";
    @Column(name = "client_name", length = 256) private String clientName;
    @Column(name = "client_contact_name", length = 128) private String clientContactName;
    @Column(name = "client_contact_phone", length = 32) private String clientContactPhone;
    @Column(name = "plan_work_weeks") private Integer planWorkWeeks;
    @Column(name = "created_by") private Long createdBy;
}
