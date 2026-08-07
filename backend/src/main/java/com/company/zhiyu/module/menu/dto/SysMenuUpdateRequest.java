package com.company.zhiyu.module.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 更新菜单请求体 — code / builtin 不可改
 */
public record SysMenuUpdateRequest(
        @NotBlank @Size(max = 64)
        String name,

        Long parentId,

        @Size(max = 128)
        String path,

        @Size(max = 32)
        String icon,

        int sortOrder,

        @Pattern(regexp = "DIR|PAGE", message = "menuType 只能 DIR / PAGE")
        String menuType,

        Boolean enabled,

        @Size(max = 256)
        String description
) {}