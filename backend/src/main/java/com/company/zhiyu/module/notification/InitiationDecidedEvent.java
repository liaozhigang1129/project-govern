package com.company.zhiyu.module.notification;

import java.time.Instant;

/**
 * 审批人做出决定(APPROVED/REJECTED/SUPPLEMENT)→ 通知申请人 + 下一审批人
 */
public record InitiationDecidedEvent(
        Long initiationId,
        String initiationCode,
        String title,
        Long applicantUserId,
        String applicantName,
        String applicantEmail,
        Long approverUserId,
        String approverName,
        String decision,           // APPROVED / REJECTED / SUPPLEMENT
        String nextStepCode,
        String nextStepName,
        Long nextStepUserId,       // null=无下一级/终态
        String comment,
        Instant occurredAt
) implements NotificationEvent {
    @Override public Long resourceId() { return initiationId; }
    @Override public String resourceCode() { return initiationCode; }
    @Override public String actorName() { return applicantName; }
}
