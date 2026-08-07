package com.hex.projectgovern.module.menu;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 系统菜单 (L1-3)
 * 设计: 树形结构, parent_id = NULL 即顶层.
 *       builtin=true 表示 seed 数据, code 不可改 / 不可删.
 */
@Entity
@Table(name = "sys_menu")
@Getter @Setter @NoArgsConstructor
public class SysMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 英文唯一键: DASHBOARD / TIMESHEET / TIMESHEET_MGMT ... */
    @Column(nullable = false, unique = true, length = 64)
    private String code;

    /** 中文显示名 */
    @Column(nullable = false, length = 64)
    private String name;

    /** 父菜单 ID (NULL = 顶层) */
    @Column(name = "parent_id")
    private Long parentId;

    /** 前端路由路径; 目录可空 */
    @Column(length = 128)
    private String path;

    /** Element Plus icon 名 (House / Calendar / ...) */
    @Column(length = 32)
    private String icon;

    /** 同级排序 */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    /** DIR / PAGE — 一期只用这两种 */
    @Column(name = "menu_type", nullable = false, length = 16)
    private String menuType = "PAGE";

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private boolean builtin = false;

    @Column(length = 256)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}