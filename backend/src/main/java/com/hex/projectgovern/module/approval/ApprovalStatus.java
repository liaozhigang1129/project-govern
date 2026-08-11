package com.hex.projectgovern.module.approval;

/**
 * 审批流程实例状态 (单实例级别)
 * - INITIAL: 已创建但未启动 (业务实体 NEW 状态)
 * - PENDING: 至少一个审批节点等待决策
 * - APPROVED: 全部 required 节点通过 (终态)
 * - REJECTED: 任一 required 节点拒绝 (终态)
 * - SUPPLEMENT: 申请人需补充材料 (回到申请人,可重新提交)
 * - CANCELLED: 业务实体撤回/作废 (终态)
 */
public enum ApprovalStatus {
    INITIAL, PENDING, APPROVED, REJECTED, SUPPLEMENT, CANCELLED;

    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED || this == CANCELLED;
    }
}