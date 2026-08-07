package com.company.pmo.module.cost.dto;

import java.math.BigDecimal;

/**
 * 单条工时 × 时薪的小计 (CostEngineService 内部分解 / 响应行项共用)
 */
public record CostBreakdownItem(
        Long projectId,
        String projectCode,
        String projectName,
        Long milestoneId,
        BigDecimal hours,
        BigDecimal rate,         // 当时实际生效的费率
        String rateSource,       // USER_OVERRIDE / ROLE_DEFAULT / ROLE_COST_DEFAULT / USER_DEFAULT / NONE
        BigDecimal cost          // hours × rate
) {}