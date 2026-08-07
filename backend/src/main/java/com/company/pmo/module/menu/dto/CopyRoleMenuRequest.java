package com.company.pmo.module.menu.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * V4.12: 复制授权 — 把 sourceRoleId 的菜单授权复制到 targetRoleIds
 */
public record CopyRoleMenuRequest(
        @NotNull Long sourceRoleId,
        @NotEmpty @Size(max = 50) List<Long> targetRoleIds,
        /** 是否覆盖 (默认 false = 合并) */
        Boolean overwrite
) {}