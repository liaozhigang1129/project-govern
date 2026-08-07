package com.hex.projectgovern.module.initiation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

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
