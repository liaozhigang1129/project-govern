package com.company.zhiyu.module.org.dto;

import com.company.zhiyu.module.org.AppUser;

import java.time.Instant;
import java.util.List;

/**
 * 用户列表行 (L1-1 用户管理)
 * - 手机列表里脱敏: 138****8000 (前端拿到即可直接展示)
 * - 邮箱列表里明文 (内部系统, 导出时单独审计)
 * - 角色: primaryRoleCode + 全部 roleCodes (含主)
 */
public record UserListItem(
        Long id,
        String username,
        String fullName,
        String email,
        String phone,                  // 列表 = 脱敏; 详情 = 可能是明文(看是不是自己)
        Long departmentId,
        String departmentName,
        String departmentPath,
        String primaryRoleCode,
        String primaryRoleName,
        List<String> roleCodes,        // 多角色 (含主)
        String jobTitle,
        boolean enabled,
        boolean locked,                // failedLoginCount>=5 || lockedUntil > now
        Instant lastLoginAt,
        boolean mustChangePassword,
        Instant createdAt
) {
    public static UserListItem from(AppUser u,
                                    String deptName,
                                    String deptPath,
                                    List<String> roleCodes,
                                    String maskedPhone) {
        boolean locked = u.getLockedUntil() != null
                && u.getLockedUntil().isAfter(Instant.now());
        return new UserListItem(
                u.getId(), u.getUsername(), u.getFullName(),
                u.getEmail(), maskedPhone,
                u.getDepartmentId(), deptName, deptPath,
                u.getPrimaryRole() == null ? null : u.getPrimaryRole().getCode(),
                u.getPrimaryRole() == null ? null : u.getPrimaryRole().getName(),
                roleCodes,
                u.getJobTitle(),
                u.isEnabled(),
                locked,
                u.getLastLoginAt(),
                u.isMustChangePassword(),
                u.getCreatedAt());
    }
}
