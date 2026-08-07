package com.company.zhiyu.module.dict;

import com.company.zhiyu.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 产品线 PL(Product Line)。
 *  - 隶属于 BU(bu_id 必填)
 *  - 一个项目最多属于 1 个 PL
 */
@Entity
@Table(name = "product_line")
@Getter @Setter @NoArgsConstructor
public class ProductLine extends SoftDeletableEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bu_id", nullable = false)
    private BusinessUnit bu;

    @Column(nullable = false, unique = true, length = 32)
    private String code;          // FIN-PAY / FIN-CORE ...

    @Column(nullable = false, length = 64)
    private String name;          // 支付产品线

    @Column(length = 256)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(nullable = false)
    private boolean enabled = true;
}
