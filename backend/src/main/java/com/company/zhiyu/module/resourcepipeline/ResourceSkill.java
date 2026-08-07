package com.company.zhiyu.module.resourcepipeline;

import com.company.zhiyu.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** P6 人员技能 */
@Entity
@Table(name = "resource_skill")
@Getter @Setter @NoArgsConstructor
public class ResourceSkill extends SoftDeletableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "skill_code", nullable = false, length = 32) private String skillCode;
    @Column(name = "skill_level", nullable = false) private Byte skillLevel = 3;
    @Column(nullable = false, columnDefinition = "TINYINT") private Byte certified = 0;
    @Column(name = "cert_date") private LocalDate certDate;
    @Column(name = "years_exp", precision = 4, scale = 1) private BigDecimal yearsExp;
    @Column(length = 256) private String remark;
}
