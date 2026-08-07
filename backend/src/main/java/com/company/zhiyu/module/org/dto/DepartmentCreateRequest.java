package com.company.zhiyu.module.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 部门新建请求(V4.12 增 leaderUserId)
 */
public record DepartmentCreateRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,31}$",
                message = "code 必须以大写字母开头, 仅含 A-Z/0-9/_ , 2-32 位")
        String code,

        @NotBlank @Size(max = 64) String name,
        Long parentId,             // null = 根
        Integer sortOrder,
        Boolean enabled,
        Long leaderUserId          // 部门负责人 user_id(可选)
) {}