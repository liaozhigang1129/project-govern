package com.hex.projectgovern.module.timesheet.dto;

import com.hex.projectgovern.module.timesheet.TimesheetEntry;
import com.hex.projectgovern.module.timesheet.TimesheetStatus;
import com.hex.projectgovern.module.timesheet.TimesheetWeek;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TimesheetResponses {

    /** 列表简版(不含明细,但聚合 userName/approverName/projectCount/entryCount 供前端展示) */
    @Data
    public static class Summary {
        private Long id;
        private Long userId;
        private String userName;            // 提交人姓名(P2.A 补:前端表格渲染)
        private LocalDate weekStart;
        private LocalDate weekEnd;
        private TimesheetStatus status;
        private BigDecimal totalHours;       // 聚合
        private int projectCount;            // 涉及项目数
        private int entryCount;              // 明细行数
        private String submitterNote;
        private Instant submittedAt;
        private Long approverId;
        private String approverName;         // 审批人姓名(P2.A 补)
        private Instant approvedAt;
    }

    /** 详情(含 entries, 同样填 userName/approverName) */
    @Data
    public static class Detail {
        private Long id;
        private Long userId;
        private String userName;
        private LocalDate weekStart;
        private LocalDate weekEnd;
        private TimesheetStatus status;
        private String submitterNote;
        private Instant submittedAt;
        private Long approverId;
        private String approverName;
        private Instant approvedAt;
        private Instant createdAt;
        private Instant updatedAt;
        private List<EntryView> entries;
        private BigDecimal totalHours;
    }

    @Data
    public static class EntryView {
        private Long id;
        private LocalDate workDate;
        private Long projectId;
        private Long milestoneId;
        private BigDecimal hours;
        private String description;

        public static EntryView from(TimesheetEntry e) {
            EntryView v = new EntryView();
            v.id = e.getId();
            v.workDate = e.getWorkDate();
            v.projectId = e.getProjectId();
            v.milestoneId = e.getMilestoneId();
            v.hours = e.getHours();
            v.description = e.getDescription();
            return v;
        }
    }

    public static Summary toSummary(TimesheetWeek t) {
        Summary s = new Summary();
        s.id = t.getId();
        s.userId = t.getUserId();
        s.weekStart = t.getWeekStart();
        s.weekEnd = t.getWeekEnd();
        s.status = t.getStatus();
        s.totalHours = t.getEntries().stream()
                .map(TimesheetEntry::getHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // P2.A 补:聚合 projectCount / entryCount(供前端历史表格展示)
        Set<Long> projectIds = new HashSet<>();
        int entryCount = 0;
        for (TimesheetEntry e : t.getEntries()) {
            projectIds.add(e.getProjectId());
            entryCount++;
        }
        s.projectCount = projectIds.size();
        s.entryCount = entryCount;
        s.submitterNote = t.getSubmitterNote();
        s.submittedAt = t.getSubmittedAt();
        s.approverId = t.getApproverId();
        s.approvedAt = t.getApprovedAt();
        return s;
    }

    public static Detail toDetail(TimesheetWeek t) {
        Detail d = new Detail();
        d.id = t.getId();
        d.userId = t.getUserId();
        d.weekStart = t.getWeekStart();
        d.weekEnd = t.getWeekEnd();
        d.status = t.getStatus();
        d.submitterNote = t.getSubmitterNote();
        d.submittedAt = t.getSubmittedAt();
        d.approverId = t.getApproverId();
        d.approvedAt = t.getApprovedAt();
        d.createdAt = t.getCreatedAt();
        d.updatedAt = t.getUpdatedAt();
        d.entries = t.getEntries().stream()
                .map(EntryView::from)
                .sorted((a, b) -> a.workDate.compareTo(b.workDate))
                .toList();
        d.totalHours = t.getEntries().stream()
                .map(TimesheetEntry::getHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return d;
    }
}
