package com.hex.projectgovern.module.approval;

import com.hex.projectgovern.module.initiation.ProjectInitiation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 立项审批适配器 (InitiationService → ApprovalEngine 桥接)
 *
 * <p>本组件让 InitiationService.submitIfNew() 走 ApprovalEngine 启动流程,
 * 然后通过 ApprovalStepActivatedEvent 桥接回既有 InitiationSubmittedEvent,
 * 保证通知中心零改动。
 *
 * <p>本会话仅做 "start" 委托 (NEW → PENDING 自动触发),decide/resubmit 仍走老路径。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InitiationApprovalAdapter {

    private final ApprovalEngine approvalEngine;

    /**
     * 立项进入 NEW → 通过 ApprovalEngine 启动审批流
     *
     * @return ApprovalFlowInstance.id (用于业务方后续查询/转发)
     */
    public Long startInitiation(ProjectInitiation i) {
        // 业务 payload: 立项金额 (供 skip_when 解析, 暂未启用)
        String payload = "{\"initiationId\":" + i.getId() + ",\"amount\":"
                + (i.getContractAmount() != null ? i.getContractAmount().toPlainString() : "0") + "}";

        ApprovalFlowInstance inst = approvalEngine.start(
            "init", "STANDARD_INITIATION",
            i.getId(), i.getCode(),
            i.getApplicantId(), i.getDepartmentId(),
            payload);

        log.info("[InitiationApprovalAdapter] 立项 {} 启动审批流 instance={} → step {}",
            i.getCode(), inst.getId(), inst.getCurrentStepNo());

        return inst.getId();
    }

    /**
     * 业务方查询: 立项当前审批状态
     */
    public ApprovalFlowInstance findInstance(Long initiationId) {
        return approvalEngine.findByBiz("init", initiationId);
    }
}