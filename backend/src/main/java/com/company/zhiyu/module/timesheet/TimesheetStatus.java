package com.company.zhiyu.module.timesheet;

/**
 * 工时周状态机:
 * <pre>
 *   DRAFT      — PM 录入中
 *   SUBMITTED  — 已提交,等待 PMO/EXEC 审批
 *   APPROVED   — 已审批(终态)
 * </pre>
 * 不可降级。
 */
public enum TimesheetStatus {
    DRAFT, SUBMITTED, APPROVED;

    public boolean canTransitionTo(TimesheetStatus next) {
        if (this == next) return true;
        return switch (this) {
            case DRAFT     -> next == SUBMITTED;
            case SUBMITTED -> next == APPROVED;
            case APPROVED  -> false;
        };
    }
}
