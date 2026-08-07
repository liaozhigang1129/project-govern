package com.hex.projectgovern.module.risk;

import com.hex.projectgovern.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 风险应对行动(每个 risk 可挂多条)。
 * <p>对齐 V2.6 {@code risk_response} 表。
 * <p>设计: 措施独立成行, 每条有自己的 owner / due / 状态,
 *       解决"一个风险需要多个动作"的问题(PMBOK 7 推荐做法)。
 */
@Entity
@Table(name = "risk_response")
@Getter @Setter @NoArgsConstructor
public class RiskResponse extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "risk_id", nullable = false)
    private Long riskId;

    @Column(nullable = false, length = 256)
    private String action;        // 应对动作

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(nullable = false, length = 16)
    private String status = "PLANNED";  // PLANNED / IN_PROGRESS / DONE / CANCELLED

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "created_by")
    private Long createdBy;
}
