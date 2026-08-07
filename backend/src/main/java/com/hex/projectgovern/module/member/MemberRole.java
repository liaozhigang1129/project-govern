package com.hex.projectgovern.module.member;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 项目成员角色字典
 * <p>
 * 与 {@link com.hex.projectgovern.module.dict.ProjectType} 风格一致:
 *  - code 唯一,前端用 code 字符串做 value,后端 code→id 转换
 *  - sort_order 控制前端下拉排序
 *  - enabled=false 表示停用(下拉中不显示);deleted 字段来自 SoftDeletableEntity
 * </p>
 *
 * <p>V2.3 内置 7 个标准角色:PM / ASSISTANT / ARCH / BA / DEV / QA / CFG</p>
 */
@Entity
@Table(name = "member_role")
@Getter @Setter @NoArgsConstructor
public class MemberRole extends com.hex.projectgovern.common.entity.SoftDeletableEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 256)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(nullable = false)
    private boolean enabled = true;
}
