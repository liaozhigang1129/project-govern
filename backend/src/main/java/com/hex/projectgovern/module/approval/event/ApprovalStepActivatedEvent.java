package com.hex.projectgovern.module.approval.event;

import java.time.Instant;

/**
 * 审批流程 step 激活事件 (引擎推进到下一审批 step 时发)
 * 通知中心监听 → 邮件/IM 通知当前审批人
 */
public record ApprovalStepActivatedEvent(
        Long instanceId,
        String kind,
        Long bizId,
        String bizCode,
        Long applicantId,
        Integer stepNo,
        String stepName,
        String roleCode,
        Long approverUserId,    // null = 当前无可解析审批人
        Instant occurredAt
) {}