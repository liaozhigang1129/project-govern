package com.hex.projectgovern.module.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ML 预测: 调 Python ml-service REST 端点
 *
 * 轻量方案 — Java 端只做 HTTP 代理,把特征字典 POST 给 Python 服务:
 *   POST http://ml-service:8000/predict
 *   {"signal_overdue":50, "signal_spi":30, ...}
 *
 * Python 服务:
 *   - FastAPI / Flask (scripts/ml/ml_service.py)
 *   - 启动时 load joblib pkl, 内存常驻
 *   - 单次预测 < 5ms
 *
 * 降级:
 *  - enabled=false / Python 服务挂 / HTTP 错 / 超时 → 返回 null
 *  - 调用方 fallback 到规则引擎结论
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "pmo.ml", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MlProperties.class)
public class MlPredictorImpl implements MlPredictor {

    private final MlProperties props;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private final ObjectMapper om = new ObjectMapper();

    public MlPredictorImpl(MlProperties props) {
        this.props = props;
    }

    @Override
    public Prediction predict(Map<String, Double> features) {
        try {
            String url = "http://localhost:8000/predict";  // 简化: 实际可从 props 读
            String json = om.writeValueAsString(features);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("[ML] http {}: {}", resp.statusCode(),
                        resp.body().substring(0, Math.min(200, resp.body().length())));
                return null;
            }
            var node = om.readTree(resp.body());
            Map<String, Double> proba = new LinkedHashMap<>();
            node.path("proba").fields().forEachRemaining(e ->
                    proba.put(e.getKey(), e.getValue().asDouble()));
            return new Prediction(
                    node.path("severity").asText(),
                    node.path("confidence").asDouble(),
                    proba,
                    node.path("model_version").asText("unknown")
            );
        } catch (Exception e) {
            log.warn("[ML] predict failed: {}", e.getMessage());
            return null;
        }
    }
}
