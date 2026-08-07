package com.company.zhiyu.module.dict;

import com.company.zhiyu.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 关联产品(RelatedProduct)。
 *  - 隶属于 PL(pl_id 必填)
 *  - 一个项目最多关联 1 个产品(可空)
 *  - version 字段记录产品版本(如 v2.3)
 */
@Entity
@Table(name = "related_product")
@Getter @Setter @NoArgsConstructor
public class RelatedProduct extends SoftDeletableEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pl_id", nullable = false)
    private ProductLine pl;

    @Column(nullable = false, unique = true, length = 32)
    private String code;          // BANKLINK / CORECMS ...

    @Column(nullable = false, length = 128)
    private String name;          // 银企通

    @Column(length = 256)
    private String description;

    @Column(length = 32)
    private String version;       // v2.3

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(nullable = false)
    private boolean enabled = true;
}
