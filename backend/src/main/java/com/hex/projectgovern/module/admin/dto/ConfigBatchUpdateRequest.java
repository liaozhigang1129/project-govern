package com.hex.projectgovern.module.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ConfigBatchUpdateRequest(
        @NotEmpty @Valid List<ConfigItemUpdate> items
) {
    public record ConfigItemUpdate(
            @jakarta.validation.constraints.NotBlank String configKey,
            @jakarta.validation.constraints.NotNull String configValue
    ) {}
}
