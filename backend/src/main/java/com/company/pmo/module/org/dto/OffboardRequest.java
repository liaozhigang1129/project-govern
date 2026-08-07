package com.company.pmo.module.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record OffboardRequest(
        @NotNull Long transferToUserId,
        @NotBlank
        @Pattern(regexp = "OPT_OUT|RESIGN|TRANSFER",
                message = "reason 必须是 OPT_OUT / RESIGN / TRANSFER")
        String reason
) {}
