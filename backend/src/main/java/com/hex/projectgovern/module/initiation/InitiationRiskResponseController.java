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

@Tag(name = "Initiations / 风险应对", description = "Step 5 立项阶段风险及应对成本")
@RestController
@RequestMapping("/initiations/{id}/risks")
@RequiredArgsConstructor
public class InitiationRiskResponseController {

    private final InitiationRiskResponseService service;

    @GetMapping
    @RequireRoles.Read
    @Operation(summary = "某立项的风险应对列表")
    public ApiResponse<List<InitiationRiskResponse>> list(@PathVariable Long id) {
        return ApiResponse.ok(service.list(id));
    }

    @GetMapping("/total-cost")
    @RequireRoles.Read
    @Operation(summary = "应对总成本(供 Step 6 毛利计算用)")
    public ApiResponse<BigDecimal> totalCost(@PathVariable Long id) {
        return ApiResponse.ok(service.totalCost(id));
    }

    @PostMapping
    @RequireRoles.Operate
    @AuditLog(module = "INITIATION", action = "RISK_RESPONSE_SAVE")
    @Operation(summary = "新增/更新风险应对(根据 id 是否为空)")
    public ApiResponse<InitiationRiskResponse> save(@PathVariable Long id,
                                                     @RequestBody InitiationRiskResponse r) {
        r.setInitiationId(id);
        return ApiResponse.ok(service.save(r));
    }

    @DeleteMapping
    @RequireRoles.Operate
    @AuditLog(module = "INITIATION", action = "RISK_RESPONSE_DELETE_ALL", extractResourceId = false)
    @Operation(summary = "删除某立项的全部风险应对(前端 Step 5 全量覆盖用)")
    public ApiResponse<Map<String, Object>> deleteAll(@PathVariable Long id) {
        int n = service.deleteAllByInitiation(id);
        return ApiResponse.ok(Map.of("deleted", n));
    }

    @DeleteMapping("/{riskResponseId}")
    @RequireRoles.Operate
    @AuditLog(module = "INITIATION", action = "RISK_RESPONSE_DELETE", extractResourceId = false)
    @Operation(summary = "软删除风险应对")
    public ApiResponse<Void> delete(@PathVariable Long id, @PathVariable Long riskResponseId) {
        service.softDelete(riskResponseId);
        return ApiResponse.ok(null);
    }
}
