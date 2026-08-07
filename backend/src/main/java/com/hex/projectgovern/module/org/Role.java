package com.hex.projectgovern.module.org;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "role")
@Getter @Setter @NoArgsConstructor
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 256)
    private String description;

    /**
     * 内置角色标记:
     *   true  = seed 数据 (PM / DEPT_LEAD / PMO_ADMIN / EXEC / VIEWER), 不可删除 / 不可改 code
     *   false = 自定义角色, 可任意改
     */
    @Column(name = "built_in", nullable = false)
    private boolean builtIn = true;

    /** 是否启用 (停用后该角色不可被分配给新用户) */
    @Column(nullable = false)
    private boolean enabled = true;

    /** 排序号 (升序) */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
