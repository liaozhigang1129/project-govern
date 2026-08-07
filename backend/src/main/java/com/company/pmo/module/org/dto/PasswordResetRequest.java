package com.company.pmo.module.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank @Size(min = 10, max = 64) String newPassword,
        Boolean mustChangeOnNextLogin,    // 默认 true
        Boolean notifyByEmail             // 默认 true
) {}
