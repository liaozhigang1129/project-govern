package com.company.pmo.module.org.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 批量用户-角色分配 (V4.16)
 * - 给多个用户应用同一组角色
 * - mode = REPLACE: 全量替换 (默认)
 * - mode = ADD: 追加 (与现有角色合并, 去重)
 * - mode = REMOVE: 移除 (从现有角色中删除这些)
 */
public record BatchUserRoleAssignRequest(
        @NotEmpty List<Long> userIds,
        @NotEmpty List<Long> roleIds,
        String mode   // REPLACE | ADD | REMOVE
) {}
