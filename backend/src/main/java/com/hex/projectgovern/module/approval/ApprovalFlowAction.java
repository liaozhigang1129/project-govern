package com.hex.projectgovern.module.approval;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 审批动作历史 (等效于立项审批 approval_record,但更通用)
 * STARTED=流程创建时记录 (追溯)
 * TIMEOUT=审批超时触发
 * SKIPPED=step.skip_when 条件命中跳过
 */
@Entity
@Table(name = "approval_flow_action")
@Getter @Setter @NoArgsConstructor
public class ApprovalFlowAction {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(name = "instance_id", nullable = false) private Long instanceId;

    @Column(name = "step_no", nullable = false) private Integer stepNo;

    @Column(name = "approver_id") private Long approverId;

    @Column(name = "on_behalf_of_user_id") private Long onBehalfOfUserId;

    @Column(nullable = false, length = 16)
    @Enumerated(EnumType.STRING) private ApprovalDecision decision;

    @Column(columnDefinition = "text") private String comment;

    @Column(name = "decided_at", nullable = false) private Instant decidedAt = Instant.now();
}