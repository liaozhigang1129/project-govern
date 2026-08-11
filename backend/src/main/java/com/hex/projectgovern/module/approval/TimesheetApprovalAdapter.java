package com.hex.projectgovern.module.approval;

import com.hex.projectgovern.module.timesheet.TimesheetWeek;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 工时审批适配器 (TimesheetService → ApprovalEngine 桥接)
 *
 * <p>工时审批: 单级 PMO_ADMIN (kind="timesheet", flowCode="STANDARD_TIMESHEET")
 * <ul>
 *   <li>submit → 引擎.start (NEW → PENDING)
 *   <li>approve → 引擎.decide (APPROVED → 终态)
 *   <li>reject → 引擎.decide (REJECTED → 终态)
 *   <li>resubmit → 不支持(工时 SUPPLEMENT=REJECTED 后回到 DRAFT,不走引擎)
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimesheetApprovalAdapter {

    private final ApprovalEngine approvalEngine;

    /**
     * 工时 submit → 启动审批流
     */
    public Long startTimesheet(TimesheetWeek t) {
        String payload = "{\"timesheetId\":" + t.getId()
            + ",\"weekStart\":\"" + t.getWeekStart() + "\""
            + ",\"weekEnd\":\"" + t.getWeekEnd() + "\""
            + "}";

        ApprovalFlowInstance inst = approvalEngine.start(
            "timesheet", "STANDARD_TIMESHEET",
            t.getId(), buildResourceCode(t),
            t.getUserId(), null, payload);

        log.info("[TimesheetApprovalAdapter] 工时 {} 启动审批流 instance={} → step {}",
            t.getId(), inst.getId(), inst.getCurrentStepNo());
        return inst.getId();
    }

    /**
     * 工时 approve → 引擎 APPROVED (单级即终态)
     */
    public ApprovalFlowInstance approveTimesheet(Long instanceId, Long approverId, String comment) {
        ApprovalFlowInstance inst = approvalEngine.decide(
            instanceId, approverId, ApprovalDecision.APPROVED, comment);
        log.info("[TimesheetApprovalAdapter] 工时 instance={} 批准 → status={}",
            instanceId, inst.getStatus());
        return inst;
    }

    /**
     * 工时 reject → 引擎 REJECTED (终态,提交人需修改后重新 submit)
     */
    public ApprovalFlowInstance rejectTimesheet(Long instanceId, Long approverId, String reason) {
        ApprovalFlowInstance inst = approvalEngine.decide(
            instanceId, approverId, ApprovalDecision.REJECTED, reason);
        log.info("[TimesheetApprovalAdapter] 工时 instance={} 驳回 → status={}",
            instanceId, inst.getStatus());
        return inst;
    }

    /**
     * 业务方查询: 工时当前审批状态
     */
    public ApprovalFlowInstance findInstance(Long timesheetId) {
        return approvalEngine.findByBiz("timesheet", timesheetId);
    }

    private String buildResourceCode(TimesheetWeek t) {
        // 与 TimesheetService.buildResourceCode 保持一致: T-{userId}-{weekStart}
        return "T-" + t.getUserId() + "-" + t.getWeekStart().toString();
    }
}