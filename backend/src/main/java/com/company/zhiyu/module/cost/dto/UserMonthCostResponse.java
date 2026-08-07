package com.company.zhiyu.module.cost.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * F1 验收响应: GET /api/cost/user/{userId}?month=2026-06
 *  - totalHours / totalCost: 整月聚合
 *  - items: 按 (project, milestone) 行项
 *  - rateSourceBreakdown: 费率来源分账 (方便财务核对)
 */
public record UserMonthCostResponse(
        Long userId,
        String userName,
        String month,                       // "2026-06"
        BigDecimal totalHours,
        BigDecimal totalCost,
        String primaryRoleCode,             // 该月主角色 (user.primaryRole.code)
        List<CostBreakdownItem> items,
        RateSourceBreakdown rateSourceBreakdown
) {
    public record RateSourceBreakdown(
            long userOverrideHours,
            long roleOverrideHours,
            long roleDefaultHours,
            long userDefaultHours,
            long noneHours
    ) {}
}