package com.company.pmo.module.org.dto;

import jakarta.validation.constraints.*;

public record RoleCreateRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,31}$",
                message = "code 必须是大写字母/数字/下划线, 2-32 字符, 首位字母")
        String code,

        @NotBlank @Size(min = 2, max = 64)
        String name,

        @Size(max = 256)
        String description,

        Boolean enabled,         // 默认 true

        Integer sortOrder        // 默认 100
) {}
