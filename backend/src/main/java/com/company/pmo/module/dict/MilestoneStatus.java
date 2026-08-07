package com.company.pmo.module.dict;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "milestone_status")
@Getter @Setter @NoArgsConstructor
public class MilestoneStatus {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 32) private String code;
    @Column(nullable = false, length = 64) private String name;
    @Column(name = "is_terminal", nullable = false) private boolean terminal;
}
