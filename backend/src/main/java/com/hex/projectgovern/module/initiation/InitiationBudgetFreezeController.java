package com.hex.projectgovern.module.initiation;

import com.hex.projectgovern.common.api.ApiResponse;
import com.hex.projectgovern.common.audit.AuditLog;
import com.hex.projectgovern.common.security.RequireRoles;
import com.hex.projectgovern.common.exception.BusinessException;
import com.hex.projectgovern.module.org.AppUser;
import com.hex.projectgovern.module.org.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "Initiations / 预算毛利", description = "Step 6 立项预算冻结 + 毛利计算")
@RestController
@RequestMapping("/initiations/{id}/budget-freeze")
@RequiredArgsConstructor
public class InitiationBudgetFreezeController {

    private final InitiationBudgetFreezeService service;
    private final UserRepository userRepository;

    @PostMapping
    @RequireRoles.Operate
    @AuditLog(module = "INITIATION", action = "BUDGET_FREEZE", extractResourceId = false)
    @Operation(summary = "冻结预算 + 算毛利",
        description = "入参:{otherCost, contractAmountOverride?}; 实际算 resource/risk 来自其他表")
    public ApiResponse<Map<String, Object>> freeze(
            @PathVariable Long id,
            @RequestBody InitiationBudgetFreezeService.FreezeRequest req,
            @AuthenticationPrincipal UserDetails ud) {
        AppUser user = userRepository.findByUsernameAndDeletedFalse(ud.getUsername())
                .orElseThrow(() -> new BusinessException(401, "User not found"));
        InitiationBudgetFreeze f = service.freeze(id, user.getId(), req);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", f.getId());
        resp.put("initiationId", f.getInitiationId());
        resp.put("frozenBy", f.getFrozenBy());
        resp.put("frozenAt", f.getFrozenAt());
        resp.put("contractAmount", f.getContractAmount());
        resp.put("resourceCost", f.getResourceCost());
        resp.put("riskCost", f.getRiskCost());
        resp.put("otherCost", f.getOtherCost());
        resp.put("totalCost", f.getTotalCost());
        resp.put("margin", f.getMargin());
        resp.put("marginPct", f.getMarginPct());
        resp.put("snapshot", service.parseSnapshot(f));
        return ApiResponse.ok(resp);
    }

    @GetMapping("/latest")
    @RequireRoles.Read
    @Operation(summary = "取最近一次冻结(当前 active)")
    public ApiResponse<Map<String, Object>> latest(@PathVariable Long id) {
        InitiationBudgetFreeze f = service.latest(id);
        if (f == null) return ApiResponse.ok(Map.of());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", f.getId());
        resp.put("initiationId", f.getInitiationId());
        resp.put("frozenBy", f.getFrozenBy());
        resp.put("frozenAt", f.getFrozenAt());
        resp.put("contractAmount", f.getContractAmount());
        resp.put("resourceCost", f.getResourceCost());
        resp.put("riskCost", f.getRiskCost());
        resp.put("otherCost", f.getOtherCost());
        resp.put("totalCost", f.getTotalCost());
        resp.put("margin", f.getMargin());
        resp.put("marginPct", f.getMarginPct());
        resp.put("snapshot", service.parseSnapshot(f));
        return ApiResponse.ok(resp);
    }
}
