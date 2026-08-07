package com.company.zhiyu.module.menu.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 角色批量授权请求 — 给前端一键全选/清空使用
 */
public record RoleMenuAssignRequest(
        @NotNull Long roleId,
        @NotNull List<Long> menuIds
) {}