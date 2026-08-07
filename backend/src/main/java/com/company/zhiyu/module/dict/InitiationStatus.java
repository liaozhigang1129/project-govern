package com.company.zhiyu.module.dict;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "initiation_status")
@Getter @Setter @NoArgsConstructor
public class InitiationStatus {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 32) private String code;
    @Column(nullable = false, length = 64) private String name;
    @Column(name = "sort_order", nullable = false) private int sortOrder = 0;
    @Column(name = "is_terminal", nullable = false) private boolean terminal;
}
