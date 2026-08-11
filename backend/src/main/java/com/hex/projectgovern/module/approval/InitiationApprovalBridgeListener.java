package com.hex.projectgovern.module.approval;

import com.hex.projectgovern.module.approval.event.ApprovalStepActivatedEvent;
import com.hex.projectgovern.module.notification.InitiationSubmittedEvent;
import com.hex.projectgovern.module.notification.TimesheetSubmittedEvent;
import com.hex.projectgovern.module.org.AppUser;
import com.hex.projectgovern.module.org.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 桥接监听器: ApprovalStepActivatedEvent (通用引擎事件) → 既有通知事件
 *
 * <p>kind → 通知事件映射:
 * <ul>
 *   <li>"init"       → InitiationSubmittedEvent
 *   <li>"timesheet"  → TimesheetSubmittedEvent
 *   <li>"risk"       → RiskEscalatedEvent (预留)
 * </ul>
 *
 * <p>保证:
 * <ul>
 *   <li>通知中心零改动 (NotificationCenter 监听既有事件不变)
 *   <li>审批流启动后,自动通知当前 step 的审批人
 *   <li>支持 ApprovalStepActivatedEvent ↔ 业务事件的双向链接
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InitiationApprovalBridgeListener {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    public void onApprovalStepActivated(ApprovalStepActivatedEvent ev) {
        switch (ev.kind()) {
            case "init" -> forwardToInitiation(ev);
            case "timesheet" -> forwardToTimesheet(ev);
            default -> log.debug("[ApprovalBridge] 跳过非桥接 kind: {}", ev.kind());
        }
    }

    private void forwardToInitiation(ApprovalStepActivatedEvent ev) {
        log.info("[ApprovalBridge] 立项 instance={} step {} 激活 → InitiationSubmittedEvent",
            ev.instanceId(), ev.stepNo());

        AppUser applicant = userRepository.findById(ev.applicantId()).orElse(null);

        InitiationSubmittedEvent bridgeEvent = new InitiationSubmittedEvent(
            ev.bizId(),
            ev.bizCode(),
            null,  // title 由通知中心从 DB 查
            applicant == null ? null : applicant.getId(),
            applicant == null ? "Unknown" : applicant.getFullName(),
            applicant == null ? null : applicant.getEmail(),
            null,  // departmentId 从 Initiation 查
            Instant.now()
        );

        eventPublisher.publishEvent(bridgeEvent);
    }

    private void forwardToTimesheet(ApprovalStepActivatedEvent ev) {
        log.info("[ApprovalBridge] 工时 instance={} step {} 激活 → TimesheetSubmittedEvent",
            ev.instanceId(), ev.stepNo());

        AppUser submitter = userRepository.findById(ev.applicantId()).orElse(null);

        // bizCode 格式: T-{userId}-{weekStart} (与 TimesheetService.buildResourceCode 一致)
        TimesheetSubmittedEvent bridgeEvent = new TimesheetSubmittedEvent(
            ev.bizId(),
            "工时周报待审批: " + ev.bizCode(),
            ev.bizCode(),
            ev.applicantId(),
            submitter == null ? "Unknown" : submitter.getFullName(),
            null, null, null, null, null,
            Instant.now()
        );

        eventPublisher.publishEvent(bridgeEvent);
    }
}