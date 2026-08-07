package com.company.pmo.module.cost.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * F1 验收 — 单日成本: GET /api/cost/user/{userId}?date=2026-06-15
 *  - hours: 当日总工时
 *  - cost: 当日 × rate
 *  - 字段名同 UserMonthCostResponse, 但 items 是当日的明细行
 */
public record UserDayCostResponse(
        Long userId,
        String userName,
        LocalDate date,
        BigDecimal hours,
        BigDecimal cost,
        BigDecimal rate,
        String rateSource,
        String primaryRoleCode,
        java.util.List<CostBreakdownItem> items
) {}