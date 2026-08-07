package com.company.zhiyu.module.notification;

import java.time.Instant;

/**
 * 申请人补料重提 → 通知当前审批人重新审
 */
public record InitiationResubmittedEvent(
        Long initiationId,
        String initiationCode,
        String title,
        Long applicantUserId,
        String applicantName,
        String applicantEmail,
        String currentStepCode,
        String currentStepName,
        Long currentStepUserId,
        Instant occurredAt
) implements NotificationEvent {
    @Override public Long resourceId() { return initiationId; }
    @Override public String resourceCode() { return initiationCode; }
    @Override public String actorName() { return applicantName; }
}
