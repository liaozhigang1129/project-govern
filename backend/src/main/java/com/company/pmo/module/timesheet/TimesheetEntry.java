package com.company.pmo.module.timesheet;

import com.company.pmo.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 工时明细行 — 每天/每项目/每里程碑 一行。
 * <p>对齐 V1.6 {@code timesheet_entry} 表。{@code hours} 上限 24h(DB 校验)。
 */
@Entity
@Table(name = "timesheet_entry", uniqueConstraints = @UniqueConstraint(columnNames = {"timesheet_id", "work_date", "project_id", "milestone_id"}))
@Getter @Setter @NoArgsConstructor
public class TimesheetEntry extends SoftDeletableEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "timesheet_id", nullable = false)
    private TimesheetWeek timesheet;

    @Column(name = "work_date", nullable = false) private LocalDate workDate;

    @Column(name = "project_id",   nullable = false) private Long projectId;
    @Column(name = "milestone_id")                    private Long milestoneId;

    @Column(nullable = false, precision = 5, scale = 2) private BigDecimal hours;

    @Column(columnDefinition = "text") private String description;
}
