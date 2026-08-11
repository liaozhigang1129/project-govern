package com.hex.projectgovern.module.approval;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 审批流程步骤 (role_code 决定审批人解析)
 */
@Entity
@Table(name = "approval_flow_step",
       uniqueConstraints = @UniqueConstraint(columnNames = {"flow_def_id", "step_no"}))
@Getter @Setter @NoArgsConstructor
public class ApprovalFlowStep {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(name = "flow_def_id", nullable = false) private Long flowDefId;

    @Column(name = "step_no", nullable = false) private Integer stepNo;

    @Column(name = "role_code", nullable = false, length = 64) private String roleCode;

    @Column(nullable = false, length = 128) private String name;

    @Column(nullable = false) private Boolean required = true;

    /** true=无审批人时自动通过(兜底) */
    @Column(name = "auto_approve_when", nullable = false) private Boolean autoApproveWhen = false;

    @Column(name = "skip_when", length = 256) private String skipWhen;

    @Column(name = "timeout_hours") private Integer timeoutHours;
}