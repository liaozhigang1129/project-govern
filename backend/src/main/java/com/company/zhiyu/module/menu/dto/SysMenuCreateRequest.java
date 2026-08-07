package com.company.zhiyu.module.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新建菜单请求体
 */
public record SysMenuCreateRequest(
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$",
                 message = "code 必须大写字母/数字/下划线, 以字母开头")
        String code,

        @NotBlank @Size(max = 64)
        String name,

        Long parentId,                              // NULL = 顶层

        @Size(max = 128)
        String path,

        @Size(max = 32)
        String icon,

        int sortOrder,

        @Pattern(regexp = "DIR|PAGE", message = "menuType 只能 DIR / PAGE")
        String menuType,                            // 缺省 PAGE

        Boolean enabled,

        @Size(max = 256)
        String description
) {}