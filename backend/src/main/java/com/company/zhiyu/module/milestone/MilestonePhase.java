package com.company.zhiyu.module.milestone;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 里程碑阶段字典 (P1-V3.1)
 *  7 阶段: 立项/需求/设计/开发/测试/上线运维/维保
 */
@Entity
@Table(name = "milestone_phase")
@Getter @Setter @NoArgsConstructor
public class MilestonePhase {

    @Id private Long id;            // 固定 1-7, 不自增
    @Column(nullable = false, length = 32) private String code;
    @Column(nullable = false, length = 64) private String name;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(columnDefinition = "text") private String description;
}
