package com.hex.projectgovern.module.approval;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 审批流程实例 (一个业务实体 = 一个实例)
 * 唯一键 (kind, biz_id) 防止重复创建
 */
@Entity
@Table(name = "approval_flow_instance",
       uniqueConstraints = @UniqueConstraint(columnNames = {"kind", "biz_id"}))
@Getter @Setter @NoArgsConstructor
public class ApprovalFlowInstance {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(name = "flow_def_id", nullable = false) private Long flowDefId;

    @Column(nullable = false, length = 32) private String kind;

    @Column(name = "biz_id", nullable = false) private Long bizId;

    @Column(name = "biz_code", nullable = false, length = 64) private String bizCode;

    @Column(nullable = false, length = 16)
    @Enumerated(EnumType.STRING) private ApprovalStatus status = ApprovalStatus.INITIAL;

    @Column(name = "current_step_no", nullable = false) private Integer currentStepNo = 0;

    @Column(name = "applicant_id", nullable = false) private Long applicantId;

    @Column(name = "department_id") private Long departmentId;

    /** JSON: 决策金额等供 skip_when 解析 */
    @Column(name = "biz_payload", columnDefinition = "text") private String bizPayload;

    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

    @Column(name = "finished_at") private Instant finishedAt;

    @PreUpdate
    void touchUpdatedAt() { this.updatedAt = Instant.now(); }
}