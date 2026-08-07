package com.company.zhiyu.module.timesheet;

import com.company.zhiyu.common.api.ApiResponse;
import com.company.zhiyu.common.audit.AuditLog;
import com.company.zhiyu.common.security.SecurityUtils;
import com.company.zhiyu.module.timesheet.TimesheetAutoFillDtos.AutoFillRequest;
import com.company.zhiyu.module.timesheet.TimesheetAutoFillDtos.AutoFillResult;
import com.company.zhiyu.module.timesheet.TimesheetAutoFillDtos.BatchAutoFillRequest;
import com.company.zhiyu.module.timesheet.TimesheetAutoFillDtos.BatchAutoFillResult;
import com.company.zhiyu.module.timesheet.dto.TimesheetDtos.*;
import com.company.zhiyu.module.timesheet.dto.TimesheetResponses.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Timesheet", description = "工时周报 — PM 录入/提交,PMO+EXEC 审批")
@RestController
@RequestMapping("/timesheets")
@RequiredArgsConstructor
public class TimesheetController {

    private final TimesheetService service;
    private final SecurityUtils securityUtils;
    private final TimesheetReminderJob reminderJob;
    private final TimesheetAutoFillService autoFillService;

    /** 分页(自己可见自己;PMO_ADMIN/EXEC 查全员;DEPT_LEAD 查本部门 — 简化为:传 userId 即按 userId 过滤) */
    @GetMapping
    @Operation(summary = "分页查询工时周报(可按 userId/status/时间区间过滤)")
    public ApiResponse<Page<Summary>> list(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) TimesheetStatus status,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        // 普通角色只看自己
        Long current = securityUtils.currentUserId();
        if (!securityUtils.hasAnyRole("PMO_ADMIN", "EXEC")) {
            userId = current;
        }
        if (size < 1)  size = 1;
        if (size > 100) size = 100;
        return ApiResponse.ok(service.search(userId, status, from, to, PageRequest.of(page, size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "工时周报详情(含 entries)")
    public ApiResponse<Detail> get(@PathVariable Long id) {
        return ApiResponse.ok(service.getById(id));
    }

    @PostMapping
    @AuditLog(module = "TIMESHEET", action = "CREATE", extractResourceId = false)
    @Operation(summary = "新建空周报(若已存在返回原值)")
    public ApiResponse<Detail> create(@RequestBody CreateRequest req) {
        return ApiResponse.ok(service.createOrGet(req));
    }

    @PutMapping("/{id}/entries")
    @AuditLog(module = "TIMESHEET", action = "UPDATE_ENTRIES", extractResourceId = false)
    @Operation(summary = "批量 upsert 每日明细(DRAFT 期可改)")
    public ApiResponse<Detail> upsertEntries(@PathVariable Long id, @RequestBody EntriesRequest req) {
        return ApiResponse.ok(service.upsertEntries(id, req));
    }

    @PostMapping("/{id}/submit")
    @AuditLog(module = "TIMESHEET", action = "SUBMIT", extractResourceId = false)
    @Operation(summary = "提交周报(DRAFT → SUBMITTED)")
    public ApiResponse<Detail> submit(@PathVariable Long id, @RequestBody(required = false) SubmitRequest req) {
        Long uid = securityUtils.currentUserId();
        return ApiResponse.ok(service.submit(id, uid, req == null ? new SubmitRequest() : req));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('PMO_ADMIN','EXEC')")
    @AuditLog(module = "TIMESHEET", action = "APPROVE", extractResourceId = false)
    @Operation(summary = "审批(SUBMITTED → APPROVED,限 PMO_ADMIN/EXEC)")
    public ApiResponse<Detail> approve(@PathVariable Long id, @RequestBody(required = false) ApproveRequest req) {
        Long uid = securityUtils.currentUserId();
        return ApiResponse.ok(service.approve(id, uid));
    }

    /** P3 工时审批独立入口:驳回(SUBMITTED → DRAFT,要求 comment) */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('PMO_ADMIN','EXEC')")
    @AuditLog(module = "TIMESHEET", action = "REJECT", extractResourceId = false)
    @Operation(summary = "驳回(SUBMITTED → DRAFT,需 comment 至少 5 字,限 PMO_ADMIN/EXEC)")
    public ApiResponse<Detail> reject(@PathVariable Long id, @RequestBody ApproveRequest req) {
        Long uid = securityUtils.currentUserId();
        return ApiResponse.ok(service.reject(id, uid, req));
    }

    /** P3-V2 工时审批独立入口:批量批准(去重 + 收件人合并通知) */
    @PostMapping("/batch-approve")
    @PreAuthorize("hasAnyRole('PMO_ADMIN','EXEC')")
    @AuditLog(module = "TIMESHEET", action = "BATCH_APPROVE", extractResourceId = false)
    @Operation(summary = "批量批准(SUBMITTED → APPROVED,逐个事务,失败不影响其他;收件人去重后只发 1 条批量通知)")
    public ApiResponse<java.util.Map<String, Object>> batchApprove(@RequestBody BatchApproveRequest req) {
        Long uid = securityUtils.currentUserId();
        TimesheetService.BatchApproveResult r = service.batchApprove(req.getIds(), uid);
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("approved", r.approved());
        data.put("requested", r.requested());
        data.put("successCount", r.successCount());
        return ApiResponse.ok(data);
    }

    @DeleteMapping("/{id}")
    @AuditLog(module = "TIMESHEET", action = "DELETE", extractResourceId = false)
    @Operation(summary = "软删除周报(仅 DRAFT 期可删)")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Long uid = securityUtils.currentUserId();
        service.softDelete(id, uid);
        return ApiResponse.ok(null);
    }

    /**
     * P3-V2 手动触发催办(调试 / 演示用)
     *  - POST /api/timesheets/_reminder?round=WED  → 立即跑一次周三催办
     *  - 限 PMO_ADMIN / ADMIN(V2 可放宽)
     */
    @PostMapping("/_reminder")
    @PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN')")
    @Operation(summary = "手动触发工时催办(开发/调试)")
    public ApiResponse<String> triggerReminder(@org.springframework.web.bind.annotation.RequestParam(defaultValue = "MANUAL") String round) {
        reminderJob.runOnce(round);
        return ApiResponse.ok("催办已触发,round=" + round);
    }

    // ============================================================
    //  V4.34 工时自动填报
    // ============================================================

    /**
     * 单用户单周自动填报
     *  - POST /timesheets/_auto-fill
     *  - body: { userId, weekStart, dryRun?, overwrite? }
     *  - 普通用户: userId 自动覆盖为当前用户 (防越权)
     *  - PMO_ADMIN/EXEC: 可指定 userId
     */
    @PostMapping("/_auto-fill")
    @AuditLog(module = "TIMESHEET", action = "AUTO_FILL", extractResourceId = false)
    @Operation(summary = "工时自动填报 — 按考勤+请假+项目优先级为单用户填充单周明细")
    public ApiResponse<AutoFillResult> autoFill(@RequestBody AutoFillRequest req) {
        // 普通用户只能填自己
        Long current = securityUtils.currentUserId();
        if (!securityUtils.hasAnyRole("PMO_ADMIN", "EXEC")) {
            req.setUserId(current);
        }
        if (req.getUserId() == null) {
            throw new com.company.zhiyu.common.exception.BusinessException(400, "userId 必填");
        }
        return ApiResponse.ok(autoFillService.autoFill(req));
    }

    /**
     * 批量自动填报 (PMO_ADMIN 范围)
     *  - POST /timesheets/_auto-fill-batch
     *  - body: { weekStart, userIds?, dryRun?, overwrite? }
     *  - 限 PMO_ADMIN / EXEC
     */
    @PostMapping("/_auto-fill-batch")
    @PreAuthorize("hasAnyRole('PMO_ADMIN','EXEC')")
    @AuditLog(module = "TIMESHEET", action = "AUTO_FILL_BATCH", extractResourceId = false)
    @Operation(summary = "批量工时自动填报 (PMO_ADMIN/EXEC 限) — 范围跑一周全员")
    public ApiResponse<BatchAutoFillResult> autoFillBatch(@RequestBody BatchAutoFillRequest req) {
        return ApiResponse.ok(autoFillService.autoFillBatch(req));
    }
}
