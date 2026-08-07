package com.company.pmo.module.ml;

import com.company.pmo.module.milestoneai.MilestoneAiAdvisory;
import com.company.pmo.module.milestoneai.MilestoneAiAdvisoryRepository;
import com.company.pmo.module.notification.MilestoneAdvisoryDecidedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * P5-ML 异步预测监听器
 *
 * 触发: MilestoneAdvisoryDecidedEvent (来自 MilestoneAiAdvisorService.publishAdvisoryEvent)
 * 流程: 18 维特征 → MlPredictor.predict (HTTP :8000) → 写 ml_severity/ml_confidence/ml_predicted_at
 * 降级: ML 不可用 → 静默跳过, 不影响主业务
 */
@Component
@RequiredArgsConstructor
@Slf4j
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "pmo.ml", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MlAdvisorListener {

    private final MlPredictor predictor;
    private final MilestoneAiAdvisoryRepository advisoryRepo;

    @Async
    @EventListener
    @Transactional
    public void onAdvisoryDecided(MilestoneAdvisoryDecidedEvent e) {
        Long advisoryId = e.advisoryId();
        try {
            Optional<MilestoneAiAdvisory> opt = advisoryRepo.findById(advisoryId);
            if (opt.isEmpty()) {
                log.warn("[ML] advisory {} not found", advisoryId);
                return;
            }
            MilestoneAiAdvisory a = opt.get();

            // 1) 构造 18 维特征
            Map<String, Double> features = buildFeatures(a);

            // 2) 调 MlPredictor.predict
            MlPredictor.Prediction pred = predictor.predict(features);
            if (pred == null) {
                log.warn("[ML] skip: predictor returned null advisory={}", advisoryId);
                return;
            }

            // 3) 写回
            a.setMlSeverity(pred.severity());
            a.setMlConfidence(BigDecimal.valueOf(pred.confidence()));
            a.setMlPredictedAt(Instant.now());
            advisoryRepo.save(a);

            log.info("[ML] advisory={} ml_severity={} confidence={} model={}",
                    advisoryId, pred.severity(),
                    String.format("%.2f", pred.confidence()),
                    pred.modelVersion());
        } catch (Exception ex) {
            log.warn("[ML] predict failed advisory={} err={}", advisoryId, ex.getMessage());
        }
    }

    private Map<String, Double> buildFeatures(MilestoneAiAdvisory a) {
        Map<String, Double> f = new HashMap<>();
        // 5 维原始信号
        f.put("signal_overdue",     toDouble(a.getSignalOverdue()));
        f.put("signal_spi",         toDouble(a.getSignalSpi()));
        f.put("signal_phase_lag",   toDouble(a.getSignalPhaseLag()));
        f.put("signal_velocity",    toDouble(a.getSignalVelocity()));
        f.put("signal_historical",  toDouble(a.getSignalHistorical()));
        // 上下文
        f.put("score",              toDouble(a.getScore()));
        f.put("confidence",         toDouble(a.getConfidence()));
        f.put("is_critical",        "CRITICAL".equalsIgnoreCase(a.getSeverity()) ? 1.0 : 0.0);
        f.put("is_warning",         "WARNING".equalsIgnoreCase(a.getSeverity()) ? 1.0 : 0.0);
        // 简化: 静态上下文 (实际应查 project/milestone)
        f.put("phase_lag_days",     0.0);
        f.put("project_age_days",   90.0);
        f.put("milestone_age_days", 7.0);
        f.put("pm_experience_projects", 3.0);
        f.put("team_size",          5.0);
        f.put("historical_hit_rate",0.7);
        // 派生
        double score = f.get("score");
        f.put("overdue_ratio",      f.get("signal_overdue") / (score + 1e-6));
        f.put("spi_ratio",          f.get("signal_spi") / (score + 1e-6));
        f.put("critical_x_history", f.get("is_critical") * f.get("signal_historical"));
        return f;
    }

    private static double toDouble(BigDecimal v) {
        return v == null ? 0.0 : v.doubleValue();
    }
}
