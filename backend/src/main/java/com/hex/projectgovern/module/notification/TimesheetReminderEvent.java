package com.hex.projectgovern.module.notification;

import java.time.Instant;
import java.util.List;

/**
 * 工时催办事件
 *
 * <p>对那些"本周还没提交"的用户发的提醒,由 TimesheetReminderJob 定时扫描后发出
 *
 * <p>category = TIMESHEET_REMINDER
 * <p>resourceType = TIMESHEET_REMINDER
 * <p>收件人:每个未提交工时的用户本人(去重)
 */
public record TimesheetReminderEvent(
        String title,                          // "工时催办:本周周报尚未提交"
        String resourceCode,                   // "TS-REMINDER-2026-06-08"
        List<Long> targetUserIds,             // 被催办的用户 ID 列表
        List<String> targetUserNames,         // 展示用
        String weekStart,                      // 被催的周
        String weekEnd,
        String round,                          // "WED" 周三预警 | "FRI" 周五强制 | 后续可加 MON(周一提醒开始)
        int userCount,                        // 通知了多少人
        Instant occurredAt
) implements NotificationEvent {
    @Override public Long resourceId() { return (long) weekStart.hashCode(); }
    @Override public String actorName() { return "系统催办"; }
}
