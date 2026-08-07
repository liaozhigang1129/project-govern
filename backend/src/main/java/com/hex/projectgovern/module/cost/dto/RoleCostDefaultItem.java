package com.hex.projectgovern.module.cost.dto;

import com.hex.projectgovern.module.cost.RoleCostDefault;

import java.math.BigDecimal;

/**
 * 角色档默认价 — 财务可在 /admin/hourly-rates 顶部直接编辑
 */
public record RoleCostDefaultItem(
        String code,
        String name,
        BigDecimal rate,
        int sortOrder
) {
    public static RoleCostDefaultItem from(RoleCostDefault r) {
        return new RoleCostDefaultItem(r.getCode(), r.getName(), r.getRate(), r.getSortOrder());
    }
}