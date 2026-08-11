package com.hex.projectgovern.module.approval;

/**
 * 审批动作 (单步决策)
 * - APPROVED: 通过 (推进到下一步或终态)
 * - REJECTED: 拒绝 (整个流程终态)
 * - SUPPLEMENT: 要求补充 (回到申请人,实例状态置 SUPPLEMENT)
 * - STARTED: 流程创建 (自动,审计)
 * - TIMEOUT: 超时自动跳过或升级 (自动,审计)
 * - SKIPPED: skip_when 条件命中跳过 (自动,审计)
 */
public enum ApprovalDecision {
    APPROVED, REJECTED, SUPPLEMENT, STARTED, TIMEOUT, SKIPPED;

    public boolean isManual() {
        return this == APPROVED || this == REJECTED || this == SUPPLEMENT;
    }
}