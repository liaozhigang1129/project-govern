package com.company.pmo.module.healthadvisor;

import com.company.pmo.module.milestone.Milestone;
import com.company.pmo.module.project.Project;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 纯函数计算器: 输入 (Project + 它的 milestones), 输出 (suggestedCode, reasons)。
 *
 * 规则(P1.5 设计):
 *  1. 项目已 CLOSED / REJECTED — 跳过,返回 null (不打扰已结项目)
 *  2. 超期天数 >= 30 OR  进度落后 严重(完成率 < 期望值的 50%)
 *       → RED
 *  3. 超期 1-29 天 OR  进度落后(完成率 < 期望值的 80%)
 *       → YELLOW
 *  4. 其它 → GREEN
 *
 * 期望完成率 = clamp(已过天数 / 总工期天数, 0, 1) * 100
 *  - 已过天数 = max(0, today - plan_start_date)
 *  - 总工期天数 = max(1, plan_end_date - plan_start_date)
 *  - 若 plan_start/end 任一为 null → 期望完成率 = 100(不参与落后判定)
 */
public final class HealthAdvisor {

    private static final int RED_OVERDUE_DAYS = 30;
    private static final int YELLOW_OVERDUE_DAYS = 1; // 1 天就算
    private static final double RED_LAG_RATIO = 0.5;
    private static final double YELLOW_LAG_RATIO = 0.8;

    private HealthAdvisor() {}

    public static String suggestedCode(Project project, List<Milestone> milestones,
                                       LocalDate today, ZoneId zone) {
        HealthSuggestion s = compute(project, milestones, today, zone);
        return s == null ? null : s.getSuggestedCode();
    }

    /**
     * 计算一条建议。返回的 suggestedCode 可能为 null(代表跳过该项目)。
     */
    public static HealthSuggestion compute(Project project, List<Milestone> milestones,
                                           LocalDate today, ZoneId zone) {
        // 1. 终态项目不评估
        if (project.getStatus() != null) {
            String sc = project.getStatus().getCode();
            if ("CLOSED".equals(sc) || "REJECTED".equals(sc) || "DRAFT".equals(sc) || "PENDING".equals(sc)) {
                return HealthSuggestion.builder()
                        .projectId(project.getId())
                        .projectCode(project.getCode())
                        .projectName(project.getName())
                        .currentCode(project.getHealth() == null ? null : project.getHealth().getCode())
                        .currentName(project.getHealth() == null ? null : project.getHealth().getName())
                        .suggestedCode(null)
                        .suggestedName("(跳过)")
                        .overdueDays(0)
                        .milestoneCompletionPct(weightedPct(milestones))
                        .reasons(List.of("项目状态=" + sc + ",不参与健康度评估"))
                        .decidedAt(today.atStartOfDay(zone).toInstant())
                        .build();
            }
        }

        int overdueDays = overdueDays(project, today);
        int milestonePct = weightedPct(milestones);
        int expectedPct = expectedPct(project, today);
        double lagRatio = (expectedPct <= 0) ? 1.0 : (double) milestonePct / expectedPct;

        List<String> reasons = new ArrayList<>();
        String suggestion;
        if (overdueDays >= RED_OVERDUE_DAYS || (expectedPct > 0 && lagRatio < RED_LAG_RATIO)) {
            suggestion = "RED";
            if (overdueDays >= RED_OVERDUE_DAYS) reasons.add("超期 " + overdueDays + " 天(阈值 30)");
            if (expectedPct > 0 && lagRatio < RED_LAG_RATIO)
                reasons.add(String.format("进度严重落后:实际 %d%% / 期望 %d%%(%.0f%%)",
                        milestonePct, expectedPct, lagRatio * 100));
        } else if (overdueDays >= YELLOW_OVERDUE_DAYS || (expectedPct > 0 && lagRatio < YELLOW_LAG_RATIO)) {
            suggestion = "YELLOW";
            if (overdueDays >= YELLOW_OVERDUE_DAYS) reasons.add("超期 " + overdueDays + " 天");
            if (expectedPct > 0 && lagRatio < YELLOW_LAG_RATIO)
                reasons.add(String.format("进度落后:实际 %d%% / 期望 %d%%(%.0f%%)",
                        milestonePct, expectedPct, lagRatio * 100));
        } else {
            suggestion = "GREEN";
            if (milestones.isEmpty()) reasons.add("无里程碑,按计划期内不延期判定为 GREEN");
            else reasons.add("进度 " + milestonePct + "%,无超期,健康");
        }

        return HealthSuggestion.builder()
                .projectId(project.getId())
                .projectCode(project.getCode())
                .projectName(project.getName())
                .currentCode(project.getHealth() == null ? null : project.getHealth().getCode())
                .currentName(project.getHealth() == null ? null : project.getHealth().getName())
                .suggestedCode(suggestion)
                .suggestedName(nameOf(suggestion))
                .overdueDays(overdueDays)
                .milestoneCompletionPct(milestonePct)
                .reasons(reasons)
                .decidedAt(today.atStartOfDay(zone).toInstant())
                .build();
    }

    private static int overdueDays(Project p, LocalDate today) {
        if (p.getPlanEndDate() == null) return 0;
        if (!today.isAfter(p.getPlanEndDate())) return 0; // today <= planEndDate
        return (int) java.time.temporal.ChronoUnit.DAYS.between(p.getPlanEndDate(), today);
    }

    private static int expectedPct(Project p, LocalDate today) {
        if (p.getPlanStartDate() == null || p.getPlanEndDate() == null) return -1;
        long total = java.time.temporal.ChronoUnit.DAYS.between(p.getPlanStartDate(), p.getPlanEndDate());
        if (total <= 0) return -1;
        long elapsed = java.time.temporal.ChronoUnit.DAYS.between(p.getPlanStartDate(), today);
        if (elapsed <= 0) return 0;
        double ratio = (double) elapsed / total;
        if (ratio >= 1.0) return 100;
        return (int) Math.round(ratio * 100);
    }

    private static int weightedPct(List<Milestone> milestones) {
        long totalWeight = 0;
        long doneWeight = 0;
        for (Milestone m : milestones) {
            if (m.getStatus() == null) continue;
            int w = m.getWeight() <= 0 ? 1 : m.getWeight();
            totalWeight += w;
            if ("COMPLETED".equalsIgnoreCase(m.getStatus().getCode())) {
                doneWeight += w;
            }
        }
        if (totalWeight == 0) return 0;
        return (int) Math.round(100.0 * doneWeight / totalWeight);
    }

    private static String nameOf(String code) {
        return switch (code) {
            case "GREEN" -> "正常";
            case "YELLOW" -> "关注";
            case "RED" -> "严重";
            default -> code;
        };
    }
}
