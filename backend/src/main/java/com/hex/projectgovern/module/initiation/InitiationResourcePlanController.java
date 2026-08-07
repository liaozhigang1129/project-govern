package com.hex.projectgovern.module.initiation;

import com.hex.projectgovern.common.api.ApiResponse;
import com.hex.projectgovern.common.audit.AuditLog;
import com.hex.projectgovern.common.security.RequireRoles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Tag(name = "Initiations / 资源计划", description = "Step 4 人力资源派遣计划")
@RestController
@RequestMapping("/initiations/{id}/resource-plans")
@RequiredArgsConstructor
public class InitiationResourcePlanController {

    private final InitiationResourcePlanService service;

    @GetMapping
    @RequireRoles.Read
    @Operation(summary = "某立项的资源计划列表")
    public ApiResponse<List<InitiationResourcePlan>> list(@PathVariable Long id) {
        return ApiResponse.ok(service.list(id));
    }

    @GetMapping("/total-cost")
    @RequireRoles.Read
    @Operation(summary = "汇总成本(供 Step 6 毛利计算用)")
    public ApiResponse<BigDecimal> totalCost(@PathVariable Long id) {
        return ApiResponse.ok(service.totalCost(id));
    }

    @PostMapping
    @RequireRoles.Operate
    @AuditLog(module = "INITIATION", action = "RESOURCE_PLAN_SAVE")
    @Operation(summary = "新增/更新资源计划(根据 id 是否为空)")
    public ApiResponse<InitiationResourcePlan> save(@PathVariable Long id,
                                                     @RequestBody InitiationResourcePlan p) {
        p.setInitiationId(id);
        return ApiResponse.ok(service.save(p));
    }

    @DeleteMapping("")
    @RequireRoles.Operate
    @AuditLog(module = "INITIATION", action = "RESOURCE_PLAN_DELETE_ALL", extractResourceId = false)
    @Operation(summary = "删除某立项的全部资源计划(前端 Step 4 全量覆盖用)")
    public ApiResponse<Map<String, Object>> deleteAll(@PathVariable Long id) {
        int n = service.deleteAllByInitiation(id);
        return ApiResponse.ok(Map.of("deleted", n));
    }

    @DeleteMapping("/{planId}")
    @RequireRoles.Operate
    @AuditLog(module = "INITIATION", action = "RESOURCE_PLAN_DELETE", extractResourceId = false)
    @Operation(summary = "软删除单条资源计划")
    public ApiResponse<Void> delete(@PathVariable Long id, @PathVariable Long planId) {
        service.softDelete(planId);
        return ApiResponse.ok(null);
    }
}