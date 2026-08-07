package com.hex.projectgovern.module.dict;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 项目级别字典 (V4.17)
 * <p>S/A/B/C 四级, 与 project_type 字典平行。立项和 project 表都引用此表 code。</p>
 */
@Entity
@Table(name = "project_level")
@Getter @Setter @NoArgsConstructor
public class ProjectLevel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 32) private String code;
    @Column(nullable = false, length = 64) private String name;
    @Column(name = "sort_order", nullable = false) private int sortOrder = 0;
    @Column(length = 256) private String description;
}
