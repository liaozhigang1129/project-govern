package com.hex.projectgovern.module.initiation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 立项审批动作审计记录 (临时兼容表).
 *
 * <p>WP-M7-05 起, 立项审批全部委托 {@link com.hex.projectgovern.module.approval.DefaultApprovalEngine},
 * 审计动作由 {@link com.hex.projectgovern.module.approval.ApprovalFlowAction} 单一事实源记录。
 * ApprovalRecord 表保留以便查询历史数据, 新代码不应再写入。
 *
 * <p>迁移路径:
 * <ul>
 *   <li>旧查询: {@code ApprovalRecordRepository.findByInitiationIdOrderByDecidedAtAsc(initiationId)}</li>
 *   <li>迁移到: ApprovalFlowActionRepository.findByTargetIdAndTargetTypeOrderByDecidedAtAsc(initiationId, "INITIATION")</li>
 * </ul>
 *
 * @deprecated since WP-M7-05; 数据保留到 v5 数据迁移时清理
 */
@Deprecated
@Entity
@Table(name = "approval_record")
@Getter @Setter @NoArgsConstructor
public class ApprovalRecord {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(name = "initiation_id", nullable = false) private Long initiationId;
    @Column(name = "step_id", nullable = false) private Long stepId;
    @Column(name = "approver_id", nullable = false) private Long approverId;
    @Column(nullable = false, length = 16) private String decision;
    @Column(columnDefinition = "text") private String comment;
    @Column(name = "decided_at", nullable = false) private Instant decidedAt = Instant.now();
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();

    /**
     * 代审时的原审批人(主审批人 disabled/deleted 时,backup 代审记录此字段供审计追溯)。
     * 平时 null(本人审批)。
     */
    @Column(name = "on_behalf_of_user_id")
    private Long onBehalfOfUserId;
}
