package com.company.zhiyu.module.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DepartmentUpdateRequest(
        /** 不可改: code (避免破坏引用) */
        @NotBlank @Size(max = 64) String name,
        Long parentId,                // null = 提为根; 不允许把自己设成自己的后代
        Integer sortOrder,
        Boolean enabled,
        Long leaderUserId             // V4.12: 部门负责人(可为 null = 清空)
) {}