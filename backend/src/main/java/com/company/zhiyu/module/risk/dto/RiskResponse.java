package com.company.zhiyu.module.risk.dto;

import com.company.zhiyu.module.risk.Risk;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 风险响应 DTO (P4) — 注意名字跟 Entity {@code RiskResponse} 冲突, 故放 dto 包下。
 */
public record RiskResponse(
        Long id,
        Long projectId,
        String code,
        String title,
        String description,
        String category,
        Integer probability,
        Integer impact,
        Integer score,
        String level,
        String status,
        Long ownerUserId,
        String ownerName,
        String mitigation,
        String contingency,
        String responseStrategy,
        LocalDate identifiedDate,
        LocalDate targetCloseDate,
        LocalDate actualCloseDate,
        Long relatedWbsTaskId,
        String relatedWbsTaskName,
        Long relatedMilestoneId,
        String relatedMilestoneName,
        Long createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    public static RiskResponse from(Risk r) {
        return from(r, null, null, null);
    }

    public static RiskResponse from(Risk r,
                                     String ownerName,
                                     String relatedWbsTaskName,
                                     String relatedMilestoneName) {
        return new RiskResponse(
                r.getId(), r.getProjectId(), r.getCode(), r.getTitle(),
                r.getDescription(), r.getCategory(),
                r.getProbability(), r.getImpact(), r.getScore(), r.getLevel(),
                r.getStatus(), r.getOwnerUserId(), ownerName,
                r.getMitigation(), r.getContingency(), r.getResponseStrategy(),
                r.getIdentifiedDate(), r.getTargetCloseDate(), r.getActualCloseDate(),
                r.getRelatedWbsTaskId(), relatedWbsTaskName,
                r.getRelatedMilestoneId(), relatedMilestoneName,
                r.getCreatedBy(), r.getCreatedAt(), r.getUpdatedAt()
        );
    }
}
