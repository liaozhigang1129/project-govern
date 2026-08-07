package com.company.pmo.module.dict;

import com.company.pmo.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 业务单元 BU(Business Unit)。
 *  - 一级业务划分(如"金融事业部"/"政企事业部")
 *  - 公司层面的组织维度,跨部门
 *  - 一个项目最多属于 1 个 BU
 */
@Entity
@Table(name = "business_unit")
@Getter @Setter @NoArgsConstructor
public class BusinessUnit extends SoftDeletableEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;          // FIN / GOV / MKT ...

    @Column(nullable = false, length = 64)
    private String name;          // 金融事业部

    @Column(length = 256)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(nullable = false)
    private boolean enabled = true;
}
