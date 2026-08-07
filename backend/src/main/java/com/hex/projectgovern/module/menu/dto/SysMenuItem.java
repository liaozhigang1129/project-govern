package com.hex.projectgovern.module.menu.dto;

import com.hex.projectgovern.module.menu.SysMenu;

import java.time.Instant;

/**
 * 菜单项 (扁平) — 列表 + 编辑表单都用这个.
 * 不带 children: 前端组装树.
 */
public record SysMenuItem(
        Long id,
        String code,
        String name,
        Long parentId,
        String parentName,
        String path,
        String icon,
        int sortOrder,
        String menuType,
        boolean enabled,
        boolean builtin,
        String description,
        Instant createdAt
) {
    public static SysMenuItem from(SysMenu m, String parentName) {
        return new SysMenuItem(
                m.getId(), m.getCode(), m.getName(),
                m.getParentId(), parentName,
                m.getPath(), m.getIcon(),
                m.getSortOrder(), m.getMenuType(),
                m.isEnabled(), m.isBuiltin(),
                m.getDescription(), m.getCreatedAt());
    }
}