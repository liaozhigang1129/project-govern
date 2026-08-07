package com.company.zhiyu.module.notification;

import java.time.Instant;

/**
 * 工时周报提交 → 通知所有 PMO_ADMIN/EXEC 审批人
 *
 * <p>收件人策略:
 *  - 提交人(自己)不通知
 *  - 全员 PMO_ADMIN/EXEC(目前简化;V2 可改"本部门 PMO")
 *
 * <p>资源定位:resourceType = "TIMESHEET"
 */
public record TimesheetSubmittedEvent(
        Long timesheetId,
        String title,
        String resourceCode,        // 形如 "TS-2025-06-09-1"  (weekStart-userId)
        Long submitterUserId,
        String submitterName,
        String weekStart,           // ISO date, 显示用
        String weekEnd,             // ISO date
        Double totalHours,          // 提交时合计(允许空)
        Integer projectCount,
        Integer entryCount,
        Instant occurredAt
) implements NotificationEvent {
    @Override public Long resourceId() { return timesheetId; }
    @Override public String actorName() { return submitterName; }
}
