package com.company.zhiyu.module.cost.dto;

import com.company.zhiyu.module.cost.HourlyRate;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * HourlyRate 写入请求 — POST 新建 / PUT 更新共用。
 * userId=null 时 = 角色档默认价;非空时 = 单人 override。
 */
public record HourlyRateUpsertRequest(
        @Size(max = 32) String roleCode,                       // 可选;若 userId=null 必须填
        Long userId,                                            // 可选;null=角色档
        @NotNull @DecimalMin(value = "0.01", message = "时薪必须 > 0") BigDecimal rate,
        @NotNull YearMonth effectiveMonth,
        YearMonth endMonth,                                     // 可选;null=仍生效
        @Size(max = 256) String remark
) {
    /** 主链/校验, Service 入口用 */
    public void validate() {
        if (roleCode == null || roleCode.isBlank()) {
            throw new IllegalArgumentException("roleCode 必填");
        }
        if (rate == null || rate.signum() <= 0) {
            throw new IllegalArgumentException("rate 必须 > 0");
        }
        if (effectiveMonth == null) {
            throw new IllegalArgumentException("effectiveMonth 必填");
        }
        if (endMonth != null && endMonth.isBefore(effectiveMonth)) {
            throw new IllegalArgumentException("endMonth 必须 >= effectiveMonth");
        }
    }

    public HourlyRate toEntity(HourlyRate h) {
        h.setRoleCode(roleCode.trim().toUpperCase());
        h.setRate(rate);
        h.setEffectiveMonth(effectiveMonth.atDay(1));
        h.setEndMonth(endMonth == null ? null : endMonth.atEndOfMonth());
        h.setRemark(remark);
        return h;
    }
}