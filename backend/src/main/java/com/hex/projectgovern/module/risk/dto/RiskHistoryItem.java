package com.hex.projectgovern.module.risk.dto;

import com.hex.projectgovern.module.risk.RiskHistory;

import java.time.Instant;

/**
 * 风险变更历史 DTO (P4)。
 * <p>每个事件含: 动作类型, 字段名, 旧/新值, 评论, 操作人。
 */
public record RiskHistoryItem(
        Long id,
        Long riskId,
        String action,
        String fieldName,
        String oldValue,
        String newValue,
        String comment,
        Long operatorId,
        String operatorName,
        Instant createdAt
) {
    public static RiskHistoryItem from(RiskHistory h, String operatorName) {
        return new RiskHistoryItem(
                h.getId(), h.getRiskId(), h.getAction(),
                h.getFieldName(), h.getOldValue(), h.getNewValue(),
                h.getComment(),
                h.getOperatorId(), operatorName,
                h.getCreatedAt()
        );
    }
}
