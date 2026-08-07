package com.company.pmo.module.timesheet;

/**
 * 批量审批流程标记(线程级)。
 *
 * <p>作用:在 batchApprove() 内部调 approve() 时,让 approve() 知道"自己正在被批量流程调用",
 * 不要再发单条 TimesheetDecidedEvent(否则提交人会收到"已批准"+"批量已批准"双通知)。
 *
 * <p>用 try/finally 包住,确保异常路径也清理。
 */
public final class BatchApproveContext {
    private static final ThreadLocal<Boolean> IN_BATCH = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private BatchApproveContext() {}

    public static void enter() { IN_BATCH.set(Boolean.TRUE); }
    public static void exit()  { IN_BATCH.remove(); }
    public static boolean isInBatch() { return Boolean.TRUE.equals(IN_BATCH.get()); }
}
