package com.company.zhiyu.module.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * P5-ML 把 PM 反馈写入 milestone_ai_outcome 表
 * (跟 outcome_recompute.py 写同一张表, 下次 train 拉全部)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MlFeedbackWriter {

    private final JdbcTemplate jdbc;
    private final ObjectMapper om = new ObjectMapper();

    /**
     * @param advisoryId   关联 advisory
     * @param outcome      INFO / WARNING / CRITICAL
     * @param reason       e.g. PM_FEEDBACK_REJECTED_NOISY_RULE
     * @param features     18 维特征 (序列化到 outcome 表)
     * @param modelVersion 反馈时模型版本
     */
    public void write(Long advisoryId, String outcome, String reason,
                      Map<String, Double> features, String modelVersion) {
        try {
            String featuresJson = om.writeValueAsString(features);
            String sql = """
                INSERT INTO milestone_ai_outcome
                    (advisory_id, outcome_severity, outcome_reason,
                     features_json, model_version, decided_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, CURDATE(), NOW(3), NOW(3))
                ON DUPLICATE KEY UPDATE
                    outcome_severity = VALUES(outcome_severity),
                    outcome_reason   = VALUES(outcome_reason),
                    features_json    = VALUES(features_json),
                    model_version    = VALUES(model_version),
                    decided_at       = CURDATE(),
                    updated_at       = NOW(3)
            """;
            jdbc.update(sql, advisoryId, outcome, reason,
                    featuresJson, modelVersion != null ? modelVersion : "rule-engine-v1.0");
            log.info("[P5-ML] outcome upserted advisory={} outcome={} reason={}",
                    advisoryId, outcome, reason);
        } catch (Exception e) {
            log.warn("[P5-ML] outcome write failed advisory={} err={}", advisoryId, e.getMessage());
        }
    }
}
