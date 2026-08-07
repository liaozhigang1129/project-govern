package com.company.zhiyu.module.timesheet;

import com.company.zhiyu.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 工时周表 — 一周一报,PM 录入每日明细。
 * 状态机:DRAFT → SUBMITTED → APPROVED,不可降级。
 *
 * <p>字段对齐 V1.6 {@code timesheet_week} 表。
 */
@Entity
@Table(name = "timesheet_week", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "week_start"}))
@Getter @Setter @NoArgsConstructor
public class TimesheetWeek extends SoftDeletableEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "week_start", nullable = false) private LocalDate weekStart;
    @Column(name = "week_end",   nullable = false) private LocalDate weekEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TimesheetStatus status = TimesheetStatus.DRAFT;

    @Column(name = "submitter_note", columnDefinition = "text") private String submitterNote;
    @Column(name = "submitted_at") private Instant submittedAt;
    @Column(name = "approver_id")  private Long approverId;
    @Column(name = "approved_at")  private Instant approvedAt;

    @OneToMany(mappedBy = "timesheet", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TimesheetEntry> entries = new ArrayList<>();

    /** 业务校验:周一-周日 */
    public void validateWeekRange() {
        if (weekStart == null || weekEnd == null) {
            throw new IllegalStateException("weekStart/weekEnd 不能为空");
        }
        if (!weekStart.getDayOfWeek().toString().equals("MONDAY")) {
            throw new IllegalStateException("weekStart 必须是周一,实得 " + weekStart.getDayOfWeek());
        }
        if (!weekEnd.minusDays(6).isEqual(weekStart)) {
            throw new IllegalStateException("weekStart/weekEnd 必须相差 6 天");
        }
    }
}
