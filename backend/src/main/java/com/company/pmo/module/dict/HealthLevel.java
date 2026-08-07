package com.company.pmo.module.dict;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "health_level")
@Getter @Setter @NoArgsConstructor
public class HealthLevel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 16) private String code;
    @Column(nullable = false, length = 32) private String name;
    @Column(name = "color_hex", length = 8) private String colorHex;
}
