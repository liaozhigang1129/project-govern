package com.company.zhiyu.module.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoleUpdateRequest(
        /** 不可改: code (内置角色) / builtIn */
        @NotBlank @Size(min = 2, max = 64) String name,
        @Size(max = 256) String description,
        Boolean enabled,
        Integer sortOrder
) {}
