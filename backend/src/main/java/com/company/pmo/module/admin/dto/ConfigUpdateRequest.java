package com.company.pmo.module.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfigUpdateRequest(
        @NotBlank @Size(max = 65535) String configValue
) {}
