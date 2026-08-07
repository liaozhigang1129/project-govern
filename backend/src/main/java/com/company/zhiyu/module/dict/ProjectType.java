package com.company.zhiyu.module.dict;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "project_type")
@Getter @Setter @NoArgsConstructor
public class ProjectType {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 32) private String code;
    @Column(nullable = false, length = 64) private String name;
    @Column(length = 256) private String description;
}
