package com.company.pmo.module.org.dto;

import com.company.pmo.module.org.Role;

import java.time.Instant;

/** 角色列表行 (L1-2 角色管理) */
public record RoleListItem(
        Long id,
        String code,
        String name,
        String description,
        boolean builtIn,
        boolean enabled,
        int sortOrder,
        /** 该角色下"主角色"是它的用户数 (不包含 extra role) */
        long primaryUserCount,
        Instant createdAt
) {
    public static RoleListItem from(Role r, long primaryUserCount) {
        return new RoleListItem(
                r.getId(), r.getCode(), r.getName(), r.getDescription(),
                r.isBuiltIn(), r.isEnabled(), r.getSortOrder(),
                primaryUserCount, r.getCreatedAt()
        );
    }
}
