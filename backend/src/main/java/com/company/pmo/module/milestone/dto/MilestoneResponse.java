package com.company.pmo.module.milestone.dto;

import com.company.pmo.module.dict.MilestoneStatus;
import com.company.pmo.module.milestone.Milestone;
import com.company.pmo.module.milestone.MilestonePhase;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 里程碑详情 — 嵌套 status + phase (V3.1 阶段字典)
 *
 * 前端可同时拿到:
 *  - status.code / status.terminal (PENDING/IN_PROGRESS/COMPLETED/DELAYED)
 *  - phase.code / phase.name      (立项/需求/设计/开发/测试/上线运维/维保)
 *  - weight (1-10)
 */
@Schema(description = "里程碑详情")
public record MilestoneResponse(
        Long id,
        Long projectId,
        String name,
        Integer sequence,
        Long phaseId,
        DictRef status,
        DictRef phase,
        LocalDate planDate,
        LocalDate actualDate,
        Integer weight,
        Long ownerUserId,
        String deliverable,
        String remark,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {
    @Schema(description = "字典小引用")
    public record DictRef(Long id, String code, String name, Boolean terminal) {
        public static DictRef from(MilestoneStatus s) {
            return s == null ? null
                    : new DictRef(s.getId(), s.getCode(), s.getName(), s.isTerminal());
        }
        public static DictRef fromPhase(MilestonePhase p) {
            return p == null ? null
                    : new DictRef(p.getId(), p.getCode(), p.getName(), null);
        }
    }

    public static MilestoneResponse from(Milestone m) {
        return new MilestoneResponse(
                m.getId(), m.getProjectId(), m.getName(), m.getSequence(), m.getPhaseId(),
                DictRef.from(m.getStatus()),
                DictRef.fromPhase(m.getPhase()),
                m.getPlanDate(), m.getActualDate(),
                m.getWeight(), m.getOwnerUserId(), m.getDeliverable(), m.getRemark(),
                m.getCompletedAt(), m.getCreatedAt(), m.getUpdatedAt()
        );
    }
}
