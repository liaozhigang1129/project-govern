package com.company.zhiyu.module.org.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 用户-角色分配请求 (V4.13)
 * - 用于「为单个用户分配角色」(独立管理端点, 区别于 PUT /api/users/{id} 整体改)
 * - 全量替换语义: 不传 roleIds 表示清空(危险操作, 但允许)
 *
 * @param userId       目标用户
 * @param roleIds      角色 ID 列表 (主角色必须在此列表里, 否则会被自动追加)
 */
public record UserRoleAssignRequest(
        @NotNull Long userId,
        List<Long> roleIds
) {}
