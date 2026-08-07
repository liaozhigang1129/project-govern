package com.hex.projectgovern.module.notification;

import java.time.Instant;

/**
 * 工时周报审批决定 → 通知提交人
 *
 * <p>decision:
 *  - APPROVED → 提交人收到"已批准"
 *  - REJECTED → 提交人收到"已驳回:xxx" + 红色提示
 */
public record TimesheetDecidedEvent(
        Long timesheetId,
        String title,
        String resourceCode,
        Long submitterUserId,
        String submitterName,
        Long approverUserId,
        String approverName,
        String decision,            // APPROVED / REJECTED
        String comment,             // 驳回理由(APPROVED 时为可选祝福语)
        String weekStart,
        String weekEnd,
        Instant occurredAt
) implements NotificationEvent {
    @Override public Long resourceId() { return timesheetId; }
    @Override public String actorName() { return submitterName; }
}
