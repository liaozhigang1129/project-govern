package com.hex.projectgovern.module.org.dto;

import jakarta.validation.constraints.*;

import java.util.List;

/** L1-1 更新用户 — 不可改字段 (username/id/createdAt) 由后端忽略 */
public record UserUpdateRequest(
        @Size(max = 64) String fullName,
        @Email @Size(max = 128) String email,
        @Size(max = 32) String phone,
        Long departmentId,
        Long primaryRoleId,
        List<Long> roleIds,
        @Size(max = 64) String jobTitle,
        Boolean enabled,
        Boolean mustChangePassword,
        Long backupUserId
) {}
