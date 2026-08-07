package com.hex.projectgovern.module.opportunityfunnel;

import com.hex.projectgovern.common.api.ApiResponse;
import com.hex.projectgovern.common.security.RequireRoles;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** P6 商机漏斗大盘 REST */
@RestController
@RequestMapping("/opportunity-funnel")
@RequiredArgsConstructor
@RequireRoles.Read
@Tag(name = "OpportunityFunnel", description = "商机配置大盘")
public class OpportunityFunnelController {

    private final OpportunityFunnelService service;

    @GetMapping("/kpis")
    public ApiResponse<Map<String, Object>> kpis() {
        return ApiResponse.ok(service.kpis());
    }

    @GetMapping("/funnel")
    public ApiResponse<List<Map<String, Object>>> funnel() {
        return ApiResponse.ok(service.funnel());
    }

    @GetMapping("/conversion-rates")
    public ApiResponse<List<Map<String, Object>>> conversionRates() {
        return ApiResponse.ok(service.conversionRates());
    }

    @GetMapping("/monthly-trend")
    public ApiResponse<List<Map<String, Object>>> monthlyTrend() {
        return ApiResponse.ok(service.monthlyTrend());
    }

    @GetMapping("/sales-rank")
    public ApiResponse<List<Map<String, Object>>> salesRank() {
        return ApiResponse.ok(service.salesRank());
    }

    @GetMapping("/amount-by-bu-pl")
    public ApiResponse<List<Map<String, Object>>> amountByBuPl() {
        return ApiResponse.ok(service.amountByBuPl());
    }
}
