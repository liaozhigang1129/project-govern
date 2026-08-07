package com.company.zhiyu.module.org.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * V4.12: 批量启停请求
 */
public record BulkEnabledRequest(
        @NotEmpty @Size(max = 500) List<Long> ids,
        @NotNull Boolean enabled
) {}