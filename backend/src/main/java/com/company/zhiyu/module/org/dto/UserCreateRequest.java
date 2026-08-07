package com.company.zhiyu.module.org.dto;

import jakarta.validation.constraints.*;

import java.util.List;

/** L1-1 新建用户 */
public record UserCreateRequest(
        @NotBlank
        @Pattern(regexp = "^[a-z][a-z0-9._-]{2,31}$",
                message = "username 必须以小写字母开头, 2-32 位, 仅含 a-z/0-9/._-")
        String username,

        @NotBlank @Size(min = 10, max = 64, message = "初始密码 10-64 位")
        String initialPassword,

        @NotBlank @Size(max = 64) String fullName,
        @Email @Size(max = 128) String email,
        @Size(max = 32) String phone,
        @NotNull Long departmentId,
        @NotNull Long primaryRoleId,
        List<Long> roleIds,                 // 可选, 多角色 (主角色自动包含)
        @Size(max = 64) String jobTitle,
        Boolean enabled,                    // 默认 true
        Boolean mustChangePassword,         // 默认 true
        Long backupUserId
) {}
