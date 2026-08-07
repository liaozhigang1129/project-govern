package com.company.zhiyu.module.notification;

import java.time.Instant;
import java.util.List;

/**
 * 工时周报批量审批通过事件
 *
 * <p>一个审批人一次批量操作 N 条周报 → 1 条事件 / 1 条 UNREAD / 1 次 IM
 * <p>vs. 调 N 次 approve():每个提交人 1 条 UNREAD + 1 次 IM(各自去重,这是预期)
 * <p>vs. 单条操作给"操作人"再发一条:冗余 —— 已通过收件人去重避免
 *
 * <p>收件人策略:每个被批周报的提交人(去重)
 *
 * <p>resourceType = "TIMESHEET_BATCH"
 */
public record TimesheetBatchApprovedEvent(
        Long batchId,                          // 形如 "{minId}-{maxId}-{count}" (展示用,无业务含义)
        String title,                          // "工时批量审批:已批准 3 份"
        String resourceCode,                   // "TS-BATCH-3-2026-06-08"
        List<Long> submitterUserIds,           // 去重后的提交人(每个各发 1 条 UNREAD)
        List<Long> timesheetIds,               // 成功批的工时 ID 列表(展示用)
        String approverUserId,
        String approverName,
        String weekStart,                      // 共享的周(批量通常同周,但不强求)
        String weekEnd,
        int approvedCount,                     // 成功批的条数
        int requestedCount,                    // 提交的条数(可能因为状态不对被跳过)
        Instant occurredAt
) implements NotificationEvent {
    @Override public Long resourceId() { return batchId == null ? 0L : (long) batchId.hashCode(); }
    @Override public String actorName() { return approverName; }
}
