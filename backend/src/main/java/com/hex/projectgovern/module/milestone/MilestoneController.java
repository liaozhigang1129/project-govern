package com.hex.projectgovern.module.milestone;

import com.hex.projectgovern.common.api.ApiResponse;
import com.hex.projectgovern.common.audit.AuditLog;
import com.hex.projectgovern.common.security.RequireRoles;
import com.hex.projectgovern.module.milestone.dto.MilestoneCreateRequest;
import com.hex.projectgovern.module.milestone.dto.MilestoneResponse;
import com.hex.projectgovern.module.milestone.dto.MilestoneUpdateRequest;
import com.hex.projectgovern.module.milestone.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/milestones")
@RequiredArgsConstructor
@Tag(name = "Milestones", description = "里程碑 + 加权进度 + 分析")
public class MilestoneController {

    private final MilestoneService milestoneService;
    private final MilestoneAnalysisService analysisService;
    private final com.hex.projectgovern.common.security.SecurityUtils securityUtils;

    @GetMapping("/by-project/{projectId}")
    @RequireRoles.Read
    @Operation(summary = "某项目的里程碑列表 (按 sequence 升序, JOIN FETCH status)")
    public ApiResponse<List<MilestoneResponse>> list(@PathVariable Long projectId) {
        return ApiResponse.ok(milestoneService.listByProject(projectId).stream()
                .map(MilestoneResponse::from).toList());
    }

    @PostMapping
    @RequireRoles.Operate
    @AuditLog(module = "MILESTONE", action = "CREATE")
    @Operation(summary = "新建里程碑 (默认状态 PENDING, weight 必填 1-10)")
    public ApiResponse<MilestoneResponse> create(@Valid @RequestBody MilestoneCreateRequest req) {
        return ApiResponse.ok(MilestoneResponse.from(milestoneService.createFromRequest(req)));
    }

    @PutMapping("/{id}")
    @RequireRoles.Operate
    @AuditLog(module = "MILESTONE", action = "UPDATE", extractResourceId = false)
    @Operation(summary = "局部更新里程碑 (甘特图拖拽改期)",
        description = "所有字段可选 null=不改;主要改 planDate;status 走 /status 接口")
    public ApiResponse<MilestoneResponse> update(@PathVariable Long id,
                                                 @Valid @RequestBody MilestoneUpdateRequest req) {
        return ApiResponse.ok(MilestoneResponse.from(milestoneService.updateFromRequest(id, req)));
    }

    /**
     * PATCH 改期(单一职责)— 甘特图拖拽直接打这里
     * <p>body: {"planDate": "2026-09-15"}</p>
     * <p>PUT /{id} 是多功能入口,PATCH /{id}/planDate 是高频轻量改期专用。</p>
     */
    @PatchMapping("/{id}/plan-date")
    @RequireRoles.Operate
    @AuditLog(module = "MILESTONE", action = "UPDATE_PLAN_DATE", extractResourceId = false)
    @Operation(summary = "改里程碑计划日期 (拖拽改期专用)",
        description = "body: { \"planDate\": \"YYYY-MM-DD\" };只改 planDate,其他字段不动")
    public ApiResponse<MilestoneResponse> updatePlanDate(@PathVariable Long id,
                                                          @RequestBody Map<String, String> body) {
        String planDateStr = body.get("planDate");
        if (planDateStr == null || planDateStr.isBlank()) {
            throw new com.hex.projectgovern.common.exception.BusinessException(400, "planDate 不能为空");
        }
        java.time.LocalDate planDate = java.time.LocalDate.parse(planDateStr);
        MilestoneUpdateRequest req = new MilestoneUpdateRequest(
                null, planDate, null, null, null, null, null, null);
        return ApiResponse.ok(MilestoneResponse.from(milestoneService.updateFromRequest(id, req)));
    }

    public record StatusUpdate(String status, String actualDate) {}

    @PutMapping("/{id}/status")
    @RequireRoles.Operate
    @AuditLog(module = "MILESTONE", action = "UPDATE_STATUS", extractResourceId = false)
    @Operation(summary = "更新里程碑状态",
        description = "COMPLETED 时自动填 actualDate 和 completedAt")
    public ApiResponse<MilestoneResponse> updateStatus(@PathVariable Long id, @RequestBody StatusUpdate u) {
        java.time.LocalDate ad = u.actualDate() == null ? null : java.time.LocalDate.parse(u.actualDate());
        return ApiResponse.ok(MilestoneResponse.from(milestoneService.updateStatus(id, u.status(), ad)));
    }

    @DeleteMapping("/{id}")
    @RequireRoles.Admin
    @AuditLog(module = "MILESTONE", action = "DELETE", extractResourceId = false)
    @Operation(summary = "软删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        milestoneService.softDelete(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/progress/{projectId}")
    @RequireRoles.Read
    @Operation(summary = "项目加权进度 (0-100)",
        description = "JPQL 聚合: SUM(weight WHERE COMPLETED) / SUM(weight) * 100, NULLIF+COALESCE 兜底")
    public ApiResponse<Map<String, Object>> progress(@PathVariable Long projectId) {
        int pct = milestoneService.computeWeightedProgress(projectId);
        return ApiResponse.ok(Map.of("projectId", projectId, "progressPct", pct));
    }

    // ==================== P1-里程碑分析 ====================

    @GetMapping("/analysis/distribution")
    @RequireRoles.Read
    @Operation(summary = "里程碑分析 - 主视图 (按 PHASE 桶 = 7 阶段)",
        description = "V3.1 改造: 立项/需求/设计/开发/测试/上线运维/维保;每桶带 status 计数 + 里程碑名明细")
    public ApiResponse<MilestoneAnalysisResponse> distribution(
            @RequestParam(defaultValue = "company") String scope,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long buId,
            @RequestParam(required = false) Long plId) {
        MilestoneAnalysisQuery q = new MilestoneAnalysisQuery(
                scope, period,
                from == null ? null : java.time.LocalDate.parse(from),
                to == null ? null : java.time.LocalDate.parse(to),
                buId, plId,
                null,    // 主视图 phaseId = null
                null, null, null);
        return ApiResponse.ok(analysisService.analyze(q, securityUtils.currentUserId()));
    }

    @GetMapping("/analysis/projects")
    @RequireRoles.Read
    @Operation(summary = "里程碑分析 - 下钻 (命中项目列表)",
        description = "支持 phaseId + statusCode + milestoneName 三层过滤")
    public ApiResponse<MilestoneDrillDownResponse> drillDown(
            @RequestParam(defaultValue = "company") String scope,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long buId,
            @RequestParam(required = false) Long plId,
            @RequestParam(required = false) Long phaseId,
            @RequestParam(required = false) String statusCode,
            @RequestParam(required = false) Long milestoneId,
            @RequestParam(required = false) String milestoneName) {
        MilestoneAnalysisQuery q = new MilestoneAnalysisQuery(
                scope, period,
                from == null ? null : java.time.LocalDate.parse(from),
                to == null ? null : java.time.LocalDate.parse(to),
                buId, plId,
                phaseId, statusCode, milestoneId, milestoneName);
        return ApiResponse.ok(analysisService.drillDown(q, securityUtils.currentUserId()));
    }
}
