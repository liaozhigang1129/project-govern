package com.company.zhiyu.module.milestoneai;

import com.company.zhiyu.common.api.ApiResponse;
import com.company.zhiyu.common.security.SecurityUtils;
import com.company.zhiyu.module.ml.MlPredictor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 里程碑 AI 预警 REST 端点 (P5: 规则引擎 + ML + LLM + PM 反馈闭环)
 *
 *  规则引擎端点 (v1.0)
 *    POST /api/milestone-ai/run
 *    GET  /api/milestone-ai/advisory/{id}
 *    GET  /api/milestone-ai/advisory
 *    GET  /api/milestone-ai/summary
 *    POST /api/milestone-ai/apply/{id}
 *    POST /api/milestone-ai/reject/{id}
 *    POST /api/milestone-ai/run-batch
 *
 *  ML 预测端点 (P5-ML)
 *    GET  /api/milestone-ai/ml/predict/{advisoryId}
 *    POST /api/milestone-ai/ml/predict
 *
 *  PM 反馈端点 (P5-Feedback)
 *    POST /api/milestone-ai/feedback
 *    GET  /api/milestone-ai/feedback/{advisoryId}
 *    GET  /api/milestone-ai/feedback/kpi
 */
@RestController
@RequestMapping("/milestone-ai")
@RequiredArgsConstructor
public class MilestoneAiAdvisorController {

    private final MilestoneAiAdvisorService service;
    private final SecurityUtils securityUtils;

    // ===== P5: ML 预测器 (条件注入: PMO_ML_ENABLED=true) =====
    private final Optional<MlPredictor> mlPredictorOpt;

    // ===== P5: PM 反馈 (总是注入) =====
    private final MilestoneAiFeedbackService feedbackService;
    private final MilestoneAiFeedbackRepository feedbackRepo;

    // ========================================================================
    // 规则引擎端点 (v1.0)
    // ========================================================================

    /** POST /api/milestone-ai/run?projectId=&milestoneId= */
    @PostMapping("/run")
    public ApiResponse<MilestoneAiAdvisoryDto> run(
            @RequestParam Long projectId,
            @RequestParam Long milestoneId) {
        return ApiResponse.ok(service.runForMilestone(projectId, milestoneId,
                securityUtils.currentUserId() == null ? 0L : securityUtils.currentUserId(),
                1.0, 0, 0.0, 0.5));
    }

    /** GET /api/milestone-ai/advisory/{id} */
    @GetMapping("/advisory/{id}")
    public ApiResponse<MilestoneAiAdvisoryDto> getById(@PathVariable Long id) {
        return ApiResponse.ok(service.getById(id));
    }

    /** GET /api/milestone-ai/advisory?projectId=&status=PENDING */
    @GetMapping("/advisory")
    public ApiResponse<List<MilestoneAiAdvisoryDto>> listByProject(
            @RequestParam Long projectId,
            @RequestParam(required = false, defaultValue = "PENDING") String status,
            @RequestParam(required = false, defaultValue = "ALL") String severity) {
        return ApiResponse.ok(service.listByProject(projectId, status, severity));
    }

    /** GET /api/milestone-ai/summary?projectId= */
    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary(@RequestParam Long projectId) {
        return ApiResponse.ok(service.summary(projectId));
    }

    /** POST /api/milestone-ai/apply/{id} */
    @PostMapping("/apply/{id}")
    public ApiResponse<MilestoneAiAdvisoryDto> apply(@PathVariable Long id) {
        return ApiResponse.ok(service.apply(id, securityUtils.currentUserId()));
    }

    /** POST /api/milestone-ai/reject/{id} */
    @PostMapping("/reject/{id}")
    public ApiResponse<MilestoneAiAdvisoryDto> reject(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        return ApiResponse.ok(service.reject(id, securityUtils.currentUserId(), reason));
    }

    /** POST /api/milestone-ai/run-batch?scope=&buId=&plId= */
    @PostMapping("/run-batch")
    public ApiResponse<Integer> runBatch(
            @RequestParam(defaultValue = "ALL") String scope,
            @RequestParam(required = false) Long buId,
            @RequestParam(required = false) Long plId) {
        return ApiResponse.ok(service.runBatch(scope, buId, plId, securityUtils.currentUserId()));
    }

    // ========================================================================
    // P5: ML 预测端点
    // ========================================================================

    /** GET /api/milestone-ai/ml/predict/{advisoryId} - 即时预测 */
    @GetMapping("/ml/predict/{advisoryId}")
    public ApiResponse<MlPredictor.Prediction> mlPredict(@PathVariable Long advisoryId) {
        if (mlPredictorOpt.isEmpty()) {
            return ApiResponse.fail(503, "ML predictor not enabled (PMO_ML_ENABLED=false)");
        }
        try {
            Optional<MilestoneAiAdvisory> opt = service.getEntityById(advisoryId);
            if (opt.isEmpty()) {
                return ApiResponse.fail(404, "advisory not found: " + advisoryId);
            }
            Map<String, Double> features = buildFeatures(opt.get());
            MlPredictor.Prediction pred = mlPredictorOpt.get().predict(features);
            if (pred == null) {
                return ApiResponse.fail(503, "ML predictor unavailable (ml_service down?)");
            }
            return ApiResponse.ok(pred);
        } catch (Exception e) {
            return ApiResponse.fail(500, "ML predict failed: " + e.getMessage());
        }
    }

    /** POST /api/milestone-ai/ml/predict - 通用预测 (前端传 features) */
    @PostMapping("/ml/predict")
    public ApiResponse<MlPredictor.Prediction> mlPredictFeatures(@RequestBody Map<String, Double> features) {
        if (mlPredictorOpt.isEmpty()) {
            return ApiResponse.fail(503, "ML predictor not enabled");
        }
        try {
            MlPredictor.Prediction pred = mlPredictorOpt.get().predict(features);
            if (pred == null) {
                return ApiResponse.fail(503, "ML predictor unavailable");
            }
            return ApiResponse.ok(pred);
        } catch (Exception e) {
            return ApiResponse.fail(500, "ML predict failed: " + e.getMessage());
        }
    }

    /** 构造 18 维特征 (跟 milestone_lgbm.py 一致) */
    private Map<String, Double> buildFeatures(MilestoneAiAdvisory a) {
        Map<String, Double> f = new HashMap<>();
        f.put("signal_overdue",     toDouble(a.getSignalOverdue()));
        f.put("signal_spi",         toDouble(a.getSignalSpi()));
        f.put("signal_phase_lag",   toDouble(a.getSignalPhaseLag()));
        f.put("signal_velocity",    toDouble(a.getSignalVelocity()));
        f.put("signal_historical",  toDouble(a.getSignalHistorical()));
        f.put("score",              toDouble(a.getScore()));
        f.put("confidence",         toDouble(a.getConfidence()));
        f.put("is_critical",        "CRITICAL".equalsIgnoreCase(a.getSeverity()) ? 1.0 : 0.0);
        f.put("is_warning",         "WARNING".equalsIgnoreCase(a.getSeverity()) ? 1.0 : 0.0);
        f.put("phase_lag_days",     0.0);
        f.put("project_age_days",   90.0);
        f.put("milestone_age_days", 7.0);
        f.put("pm_experience_projects", 3.0);
        f.put("team_size",          5.0);
        f.put("historical_hit_rate",0.7);
        double score = f.get("score");
        f.put("overdue_ratio",      f.get("signal_overdue") / (score + 1e-6));
        f.put("spi_ratio",          f.get("signal_spi") / (score + 1e-6));
        f.put("critical_x_history", f.get("is_critical") * f.get("signal_historical"));
        return f;
    }

    private static double toDouble(BigDecimal v) { return v == null ? 0.0 : v.doubleValue(); }

    // ========================================================================
    // P5: PM 反馈端点
    // ========================================================================

    /** POST /api/milestone-ai/feedback  提交 PM 反馈 */
    @PostMapping("/feedback")
    public ApiResponse<MilestoneAiFeedback> submitFeedback(
            @RequestBody FeedbackRequest req,
            HttpServletRequest httpReq
    ) {
        Long userId = securityUtils.currentUserId();
        if (userId == null) return ApiResponse.fail(401, "unauthorized");
        try {
            MilestoneAiFeedback fb = feedbackService.submit(
                    req.advisoryId(),
                    req.type(),
                    req.reasonCode(),
                    req.comment(),
                    userId,
                    httpReq.getRemoteAddr(),
                    req.modelVersion()
            );
            return ApiResponse.ok(fb);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** GET /api/milestone-ai/feedback/{advisoryId}  取最近一次反馈 */
    @GetMapping("/feedback/{advisoryId}")
    public ApiResponse<MilestoneAiFeedback> latestFeedback(@PathVariable Long advisoryId) {
        return feedbackRepo.findFirstByAdvisoryIdOrderByFeedbackAtDesc(advisoryId)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.fail(404, "no feedback"));
    }

    /** GET /api/milestone-ai/feedback/kpi?days=7  KPI 看板 */
    @GetMapping("/feedback/kpi")
    public ApiResponse<Map<String, Object>> feedbackKpi(@RequestParam(defaultValue = "7") int days) {
        return ApiResponse.ok(feedbackService.kpi7d(days));
    }

    /** 反馈请求体 */
    public record FeedbackRequest(
            Long advisoryId,
            String type,           // ACCEPTED / REJECTED / MISLEAD / EXPIRED
            String reasonCode,     // NOISY_RULE / DATA_ERROR / MODEL_BIAS / UPGRADED / OTHER
            String comment,
            String modelVersion
    ) {}
}
