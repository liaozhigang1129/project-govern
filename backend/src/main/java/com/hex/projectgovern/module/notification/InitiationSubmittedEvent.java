package com.hex.projectgovern.module.notification;

import java.time.Instant;

/**
 * 申请人提交立项 → 通知部门负责人
 */
public record InitiationSubmittedEvent(
        Long initiationId,
        String initiationCode,
        String title,
        Long applicantUserId,
        String applicantName,
        String applicantEmail,
        Long applicantDepartmentId,
        Instant occurredAt
) implements NotificationEvent {
    @Override public Long resourceId() { return initiationId; }
    @Override public String resourceCode() { return initiationCode; }
    @Override public String actorName() { return applicantName; }
}
