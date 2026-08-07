package com.company.zhiyu.module.risk;

import com.company.zhiyu.common.api.ApiResponse;
import com.company.zhiyu.common.audit.AuditLog;
import com.company.zhiyu.common.security.RequireRoles;
import com.company.zhiyu.module.risk.dto.*;
import com.company.zhiyu.module.risk.dto.RiskResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * P4 风险管理 — HTTP 入口。
 * <p>URL: /api/risks/** — 一律用复数, 跟现有 /wbs /milestones /projects 风格一致。
 */
@RestController
@RequestMapping("/risks")
@RequiredArgsConstructor
@Tag(name = "Risks", description = "项目风险登记 + 应对行动 + 5x5 风险矩阵")
public class RiskController {

    private final RiskService riskService;
    private final com.company.zhiyu.common.security.SecurityUtils securityUtils;

    // ---- 风险主表 ----

    @GetMapping("/by-project/{projectId}")
    @RequireRoles.Read
    @Operation(summary = "某项目的全部风险 (按 score 降序, 含已关闭)")
    public ApiResponse<List<RiskResponse>> listByProject(@PathVariable Long projectId) {
        return ApiResponse.ok(riskService.listByProject(projectId));
    }

    @GetMapping("/by-project/{projectId}/active")
    @RequireRoles.Read
    @Operation(summary = "某项目的活跃风险 (排除 CLOSED/ACCEPTED)")
    public ApiResponse<List<RiskResponse>> listActive(@PathVariable Long projectId) {
        return ApiResponse.ok(riskService.listActiveByProject(projectId));
    }

    @GetMapping("/{id}")
    @RequireRoles.Read
    @Operation(summary = "单个风险详情")
    public ApiResponse<RiskResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(riskService.getById(id));
    }

    @PostMapping
    @RequireRoles.Operate
    @AuditLog(module = "RISK", action = "CREATE_OR_UPDATE")
    @Operation(summary = "新建/更新风险 (id 缺失=新建)",
        description = "score / level 由 probability × impact 自动推导, 不接受客户端传入")
    public ApiResponse<RiskResponse> save(@Valid @RequestBody RiskRequest req) {
        return ApiResponse.ok(riskService.save(req, securityUtils.currentUserId()));
    }

    @DeleteMapping("/{id}")
    @RequireRoles.Operate
    @AuditLog(module = "RISK", action = "DELETE", extractResourceId = false)
    @Operation(summary = "软删除风险 (写 history)")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        riskService.softDelete(id, securityUtils.currentUserId());
        return ApiResponse.ok(null);
    }

    // ---- 应对行动 ----

    @GetMapping("/{riskId}/responses")
    @RequireRoles.Read
    @Operation(summary = "某风险的全部应对行动")
    public ApiResponse<List<RiskResponseDto.Item>> listResponses(@PathVariable Long riskId) {
        return ApiResponse.ok(riskService.listResponses(riskId));
    }

    @PostMapping("/{riskId}/responses")
    @RequireRoles.Operate
    @AuditLog(module = "RISK_RESPONSE", action = "UPSERT", extractResourceId = false)
    @Operation(summary = "新建/更新应对行动 (id 缺失=新建)")
    public ApiResponse<RiskResponseDto.Item> upsertResponse(
            @PathVariable Long riskId,
            @Valid @RequestBody RiskResponseDto.Request req) {
        return ApiResponse.ok(riskService.upsertResponse(riskId, req, securityUtils.currentUserId()));
    }

    @DeleteMapping("/responses/{responseId}")
    @RequireRoles.Operate
    @AuditLog(module = "RISK_RESPONSE", action = "DELETE", extractResourceId = false)
    @Operation(summary = "软删除应对行动")
    public ApiResponse<Void> deleteResponse(@PathVariable Long responseId) {
        riskService.deleteResponse(responseId, securityUtils.currentUserId());
        return ApiResponse.ok(null);
    }

    // ---- 历史 ----

    @GetMapping("/{riskId}/history")
    @RequireRoles.Read
    @Operation(summary = "某风险的全部变更历史 (按时间倒序)")
    public ApiResponse<List<RiskHistoryItem>> listHistory(@PathVariable Long riskId) {
        return ApiResponse.ok(riskService.listHistory(riskId));
    }

    // ---- 健康度 + 矩阵 ----

    @GetMapping("/health/by-project/{projectId}")
    @RequireRoles.Read
    @Operation(summary = "项目风险健康度 KPI (给 PMO 仪表盘用)")
    public ApiResponse<RiskHealthSummary> health(@PathVariable Long projectId) {
        return ApiResponse.ok(riskService.healthSummary(projectId));
    }

    @GetMapping("/matrix/by-project/{projectId}")
    @RequireRoles.Read
    @Operation(summary = "5x5 风险矩阵 (概率 × 影响 热力图)")
    public ApiResponse<RiskMatrix.Matrix> matrix(@PathVariable Long projectId) {
        return ApiResponse.ok(riskService.matrix(projectId));
    }
}
