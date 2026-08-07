package com.company.pmo.module.dict;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "approval_step")
@Getter @Setter @NoArgsConstructor
public class ApprovalStep {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 32) private String code;
    @Column(nullable = false, length = 64) private String name;
    @Column(nullable = false) private int sequence;
    @Column(length = 256) private String description;
}
