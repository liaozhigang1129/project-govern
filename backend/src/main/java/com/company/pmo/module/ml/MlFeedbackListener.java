package com.company.pmo.module.ml;

import com.company.pmo.module.milestoneai.MilestoneAiAdvisoryRepository;
import com.company.pmo.module.notification.MilestoneAdvisoryFeedbackEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * P5-ML 增量学习监听器
 * 触发: PM 反馈 (ACCEPTED / REJECTED / MISLEAD)
 * 动作: 反馈 → outcome_severity 写 milestone_ai_outcome
 *       (下次 train 自动 JOIN 新增训练样本)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MlFeedbackListener {

    private final MilestoneAiAdvisoryRepository advisoryRepo;
    private final MlFeedbackWriter writer;

    @Async
    @EventListener
    @Transactional
    public void onFeedback(MilestoneAdvisoryFeedbackEvent e) {
        try {
            // 1) 反馈 → outcome_severity 映射
            String outcome = switch (e.feedbackType()) {
                case "ACCEPTED" -> "WARNING";  // PM 采纳 = 建议合理
                case "REJECTED" -> "INFO";     // PM 拒绝 = 规则过度
                case "MISLEAD"  -> "INFO";     // 误报 = 模型错判
                case "EXPIRED"  -> "INFO";     // 过期 = 无所谓
                default -> null;
            };
            if (outcome == null) return;

            // 2) 拉 advisory 当前 5 维信号
            advisoryRepo.findById(e.advisoryId()).ifPresent(a -> {
                Map<String, Double> features = new LinkedHashMap<>();
                features.put("signal_overdue",    d(a.getSignalOverdue()));
                features.put("signal_spi",        d(a.getSignalSpi()));
                features.put("signal_phase_lag",  d(a.getSignalPhaseLag()));
                features.put("signal_velocity",   d(a.getSignalVelocity()));
                features.put("signal_historical", d(a.getSignalHistorical()));
                features.put("score",             d(a.getScore()));
                features.put("confidence",        d(a.getConfidence()));
                features.put("is_critical",       "CRITICAL".equals(a.getSeverity()) ? 1.0 : 0.0);
                features.put("is_warning",        "WARNING".equals(a.getSeverity()) ? 1.0 : 0.0);
                features.put("phase_lag_days",    0.0);
                features.put("project_age_days",  90.0);
                features.put("milestone_age_days",7.0);
                features.put("pm_experience_projects", 3.0);
                features.put("team_size",         5.0);
                features.put("historical_hit_rate", 0.7);
                double s = features.get("score");
                features.put("overdue_ratio",     features.get("signal_overdue") / (s + 1e-6));
                features.put("spi_ratio",         features.get("signal_spi") / (s + 1e-6));
                features.put("critical_x_history",features.get("is_critical") * features.get("signal_historical"));

                // 3) 写 milestone_ai_outcome
                String reason = "PM_FEEDBACK_" + e.feedbackType()
                        + (e.reasonCode() != null ? "_" + e.reasonCode() : "");
                writer.write(e.advisoryId(), outcome, reason, features, e.modelVersion());
            });

            log.info("[P5-ML] feedback relabeled advisory={} type={} → outcome={}",
                    e.advisoryId(), e.feedbackType(), outcome);
        } catch (Exception ex) {
            log.warn("[P5-ML] feedback listener failed: advisory={} err={}",
                    e.advisoryId(), ex.getMessage());
        }
    }

    private static double d(BigDecimal v) { return v == null ? 0.0 : v.doubleValue(); }
}
