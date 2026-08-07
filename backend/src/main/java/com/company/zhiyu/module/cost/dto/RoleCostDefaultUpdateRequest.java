package com.company.zhiyu.module.cost.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 财务改 6 角色档默认价 — 用 code 主键定位。
 */
public record RoleCostDefaultUpdateRequest(
        @NotBlank @Size(max = 32) String code,
        @NotNull @DecimalMin(value = "0.01", message = "rate 必须 > 0") BigDecimal rate
) {}