package com.hex.projectgovern.module.risk;

import com.hex.projectgovern.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 项目风险登记(Risk Register)。
 * <p>对齐 V2.6 {@code risk} 表。
 * <p>关键设计:
 * <ul>
 *   <li>score = probability × impact (1-25), level 由 score 自动推导 (LOW/MEDIUM/HIGH/CRITICAL)</li>
 *   <li>probability / impact 都是 1-5 的离散值, 渲染 5x5 风险矩阵方便</li>
 *   <li>软关联 WBS 任务 / 里程碑(可选, 风险不一定挂在具体任务上)</li>
 *   <li>状态机: OPEN → MITIGATING → CLOSED, 还可以 OCCURRED(已发生) 或 ACCEPTED(接受)</li>
 * </ul>
 */
@Entity
@Table(name = "risk", uniqueConstraints = {
        @UniqueConstraint(name = "uk_risk_project_code", columnNames = {"project_id", "code"})
})
@Getter @Setter @NoArgsConstructor
public class Risk extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 32)
    private String code;          // R-001, R-002 ...

    @Column(nullable = false, length = 256)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 16)
    private String category;       // TECHNICAL / SCHEDULE / COST / QUALITY / EXTERNAL / ORGANIZATIONAL / OTHER

    @Column(nullable = false)
    private Integer probability;   // 1-5

    @Column(nullable = false)
    private Integer impact;        // 1-5

    @Column(nullable = false)
    private Integer score;         // 1-25 (probability × impact, 自动算)

    @Column(nullable = false, length = 16)
    private String level;          // LOW / MEDIUM / HIGH / CRITICAL (由 score 自动算)

    @Column(nullable = false, length = 16)
    private String status = "OPEN";  // OPEN / MITIGATING / CLOSED / OCCURRED / ACCEPTED

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(columnDefinition = "text")
    private String mitigation;        // 预防/缓解措施

    @Column(columnDefinition = "text")
    private String contingency;       // 应急/兜底措施

    @Column(name = "response_strategy", length = 16)
    private String responseStrategy;  // AVOID / MITIGATE / TRANSFER / ACCEPT / EXPLOIT / ENHANCE / SHARE

    @Column(name = "identified_date", nullable = false)
    private LocalDate identifiedDate = LocalDate.now();

    @Column(name = "target_close_date")
    private LocalDate targetCloseDate;

    @Column(name = "actual_close_date")
    private LocalDate actualCloseDate;

    @Column(name = "related_wbs_task_id")
    private Long relatedWbsTaskId;

    @Column(name = "related_milestone_id")
    private Long relatedMilestoneId;

    @Column(name = "created_by")
    private Long createdBy;

    // ============================================================
    // 静态工具: score → level 推导
    // ============================================================
    public static int computeScore(int probability, int impact) {
        return Math.max(1, Math.min(25, probability * impact));
    }

    public static String levelOf(int score) {
        if (score >= 16) return "CRITICAL";
        if (score >= 10) return "HIGH";
        if (score >= 5)  return "MEDIUM";
        return "LOW";
    }

    /** 配置版: 从 SystemConfigService 读阈值,fallback 到静态默认值 */
    public static String levelOf(int score, com.hex.projectgovern.module.admin.SystemConfigService cfg) {
        int crit = cfg.getInt("business.risk.score_threshold.critical", 16);
        int high = cfg.getInt("business.risk.score_threshold.high", 10);
        int med  = Math.min(high - 1, 5);
        if (score >= crit) return "CRITICAL";
        if (score >= high) return "HIGH";
        if (score >= med)  return "MEDIUM";
        return "LOW";
    }

    /** 同时刷新 score + level — 修改 probability/impact 后必调 */
    public void recomputeScoreAndLevel() {
        recomputeScoreAndLevel(null);
    }

    /** 配置版: 传入 SystemConfigService 走 UI 阈值,传 null 走静态默认 */
    public void recomputeScoreAndLevel(com.hex.projectgovern.module.admin.SystemConfigService cfg) {
        if (probability == null || impact == null) return;
        this.score = computeScore(probability, impact);
        this.level = cfg == null ? levelOf(score) : levelOf(score, cfg);
    }
}
