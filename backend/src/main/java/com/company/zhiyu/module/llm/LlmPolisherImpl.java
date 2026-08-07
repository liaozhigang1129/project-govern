package com.company.zhiyu.module.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LLM 润色默认实现 (OpenAI 兼容协议)
 *
 * - DEEPSEEK / OPENAI 都走 OpenAI Chat Completions API (/chat/completions)
 * - CLAUDE 走 Anthropic Messages API (/v1/messages) — 协议不同
 *
 * 限流:
 *  - 同步 HTTP 调用,timeout 走 properties.timeout-seconds
 *  - max_tokens=300 (建议 ~80 中文字,留 buffer)
 *  - temperature=0.4 (略低于 0.5,稳定但不僵化)
 *
 * 降级:
 *  - LLM 关闭 (enabled=false) / 没配 api-key / 超时 / HTTP 错  → 返回 null
 *  - 调用方在 LlmPolishListener 里 fallback 到规则引擎原文
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "pmo.llm", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(LlmProperties.class)
public class LlmPolisherImpl implements LlmPolisher {

    private final LlmProperties props;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final ObjectMapper om = new ObjectMapper();

    public LlmPolisherImpl(LlmProperties props) {
        this.props = props;
    }

    @Override
    public String polish(String projectName, String milestoneName, String severity,
                         double totalScore, List<Signal> signals) {
        LlmProperties.Provider p = props.activeProvider();
        if (p.getApiKey() == null || p.getApiKey().isBlank()) {
            log.debug("[LLM] skipped: api-key empty");
            return null;
        }
        try {
            return "CLAUDE".equalsIgnoreCase(props.getProvider())
                    ? callClaude(p, projectName, milestoneName, severity, totalScore, signals)
                    : callOpenAi(p, projectName, milestoneName, severity, totalScore, signals);
        } catch (Exception e) {
            log.warn("[LLM] polish failed: {}", e.getMessage());
            return null;
        }
    }

    // ============================================================
    // OpenAI / DeepSeek 协议
    // ============================================================
    private String callOpenAi(LlmProperties.Provider p, String projectName, String milestoneName,
                              String severity, double totalScore, List<Signal> signals) throws Exception {
        String url = p.getBaseUrl().endsWith("/")
                ? p.getBaseUrl() + "chat/completions"
                : p.getBaseUrl() + "/chat/completions";

        Map<String, Object> body = Map.of(
                "model", p.getModel(),
                "temperature", 0.4,
                "max_tokens", 300,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt()),
                        Map.of("role", "user", "content",
                                userPrompt(projectName, milestoneName, severity, totalScore, signals))
                )
        );
        String json = om.writeValueAsString(body);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + p.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            log.warn("[LLM] openai http {}: {}", resp.statusCode(), resp.body().substring(0, Math.min(200, resp.body().length())));
            return null;
        }
        return parseOpenAiContent(resp.body());
    }

    private String parseOpenAiContent(String body) throws Exception {
        var node = om.readTree(body);
        var choices = node.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) return null;
        var content = choices.get(0).path("message").path("content");
        return content.isMissingNode() || content.isNull() ? null : content.asText().trim();
    }

    // ============================================================
    // Claude 协议 (Anthropic Messages)
    // ============================================================
    private String callClaude(LlmProperties.Provider p, String projectName, String milestoneName,
                              String severity, double totalScore, List<Signal> signals) throws Exception {
        String url = p.getBaseUrl().endsWith("/v1")
                ? p.getBaseUrl() + "/messages"
                : p.getBaseUrl() + "/v1/messages";

        Map<String, Object> body = Map.of(
                "model", p.getModel(),
                "max_tokens", 300,
                "system", systemPrompt(),
                "messages", List.of(
                        Map.of("role", "user", "content",
                                userPrompt(projectName, milestoneName, severity, totalScore, signals))
                )
        );
        String json = om.writeValueAsString(body);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("x-api-key", p.getApiKey())
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            log.warn("[LLM] claude http {}: {}", resp.statusCode(),
                    resp.body().substring(0, Math.min(200, resp.body().length())));
            return null;
        }
        var node = om.readTree(resp.body());
        var content = node.get("content");
        if (content == null || !content.isArray() || content.isEmpty()) return null;
        var first = content.get(0);
        return first.path("text").isMissingNode() ? null : first.path("text").asText().trim();
    }

    // ============================================================
    // Prompts
    // ============================================================
    private String systemPrompt() {
        return """
                你是 PMO 项目管理顾问,擅长把结构化的风险信号润色成简洁、可执行的中文建议(80 字以内)。
                要求:
                1) 第一句直接点严重度 + 核心矛盾
                2) 第二句给出 1-2 条具体建议(谁、做什么)
                3) 不要 markdown,不要 emoji 装饰(调用方会加 🤖)
                4) 语气专业、克制、不夸张
                """;
    }

    private String userPrompt(String projectName, String milestoneName, String severity,
                              double totalScore, List<Signal> signals) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("项目:%s | 里程碑:%s | 严重度:%s | 总分:%.1f/100\n",
                projectName, milestoneName, severity, totalScore));
        sb.append("触发信号(Top ").append(signals.size()).append("):\n");
        for (Signal s : signals) {
            sb.append(String.format("- [%s] 强度=%.0f 权重=%.2f 得分=%.1f  描述:%s\n",
                    s.type(), s.intensity(), s.weight(), s.score(), s.description()));
        }
        sb.append("\n请润色成 1 段 80 字内的中文建议,直接输出润色结果,不要加任何前缀。");
        return sb.toString();
    }
}
