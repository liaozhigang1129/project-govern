package com.hex.projectgovern.module.milestone;

import com.hex.projectgovern.common.entity.SoftDeletableEntity;
import com.hex.projectgovern.module.dict.MilestoneStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.Instant;

/**
 * 里程碑实体 (P1-V3.1: 引入 phase_id 阶段)
 *
 *  - sequence: 在 (project_id, phase_id) 内递增, 不再全局唯一
 *  - phase: 7 阶段 (立项/需求/设计/开发/测试/上线运维/维保)
 *  - 唯一约束: (project_id, phase_id, sequence)
 */
@Entity
@Table(name = "milestone",
       uniqueConstraints = @UniqueConstraint(name = "uq_milestone_project_phase",
                                             columnNames = {"project_id", "phase_id", "sequence"}))
@Getter @Setter @NoArgsConstructor
public class Milestone extends SoftDeletableEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(nullable = false, length = 128) private String name;

    /** (project_id, phase_id) 内递增, 从 1 开始 */
    @Column(nullable = false) private int sequence;

    /** P1-V3.1: 7 阶段外键 (initiation/requirement/design/...) */
    @Column(name = "phase_id", nullable = false) private Long phaseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phase_id", insertable = false, updatable = false)
    private MilestonePhase phase;

    @Column(name = "plan_date", nullable = false) private LocalDate planDate;
    @Column(name = "actual_date") private LocalDate actualDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    private MilestoneStatus status;

    @Column(nullable = false) private int weight = 1;
    @Column(name = "owner_user_id") private Long ownerUserId;
    @Column(columnDefinition = "text") private String deliverable;
    @Column(columnDefinition = "text") private String remark;
    @Column(name = "completed_at") private Instant completedAt;
}
