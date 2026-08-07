package com.hex.projectgovern.module.wbs;

import com.hex.projectgovern.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * WBS 任务-人员分配 (谁负责这任务, 占多少工时)。
 * <p>对齐 V2.5 {@code wbs_assignment} 表。
 * unique = (wbs_task_id, user_id), 即同一任务同一人员只一行,
 * 改工时用 UPDATE, 不用新增。
 */
@Entity
@Table(name = "wbs_assignment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_wbs_assignment_task_user", columnNames = {"wbs_task_id", "user_id"})
})
@Getter @Setter @NoArgsConstructor
public class WbsAssignment extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wbs_task_id", nullable = false) private Long wbsTaskId;
    @Column(name = "user_id",     nullable = false) private Long userId;

    @Column(length = 64) private String role = "DOER";

    @Column(name = "planned_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal plannedHours = BigDecimal.ZERO;

    @Column(name = "actual_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal actualHours = BigDecimal.ZERO;

    @Column(name = "start_date") private LocalDate startDate;
    @Column(name = "end_date")   private LocalDate endDate;

    @Column(columnDefinition = "text") private String note;
}
