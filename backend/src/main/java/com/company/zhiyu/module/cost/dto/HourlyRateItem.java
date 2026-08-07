package com.company.zhiyu.module.cost.dto;

import com.company.zhiyu.module.cost.HourlyRate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * HourlyRate 列表 / 详情响应。
 * userId/userName 冗余,避免前端二次查询。
 */
public record HourlyRateItem(
        Long id,
        String roleCode,
        Long userId,
        String userName,
        BigDecimal rate,
        YearMonth effectiveMonth,
        YearMonth endMonth,
        String remark,
        Long createdBy,
        LocalDate createdAt,
        LocalDate updatedAt
) {
    public static HourlyRateItem from(HourlyRate h) {
        Long uid = h.getUser() == null ? null : h.getUser().getId();
        String uname = h.getUser() == null ? null : h.getUser().getFullName();
        return new HourlyRateItem(
                h.getId(),
                h.getRoleCode(),
                uid,
                uname,
                h.getRate(),
                YearMonth.from(h.getEffectiveMonth()),
                h.getEndMonth() == null ? null : YearMonth.from(h.getEndMonth()),
                h.getRemark(),
                h.getCreatedBy(),
                h.getCreatedAt() == null ? null : h.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                h.getUpdatedAt() == null ? null : h.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        );
    }
}