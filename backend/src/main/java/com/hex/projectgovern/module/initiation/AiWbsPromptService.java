package com.hex.projectgovern.module.initiation;

import com.hex.projectgovern.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * AI WBS Prompt 引擎 (V4.20 新增)
 * <p>
 * 通过提示词调用 LLM (默认通义千问 OpenAI-compatible 模式),把 SOW 拆解为与规则引擎
 * <b>完全一致</b>的 JSON 结构 (milestones / workPackages / risks / industry / totalWeeks)。
 * </p>
 *
 * <p><b>设计目标</b>: 与 {@link InitiationAiWbsService#generateDraft} 输出契约严格对齐,
 * 让前端 / Step 3 apply / draft 表 schema 不用改,只需在 Controller 入口按 engine 参数路由。</p>
 *
 * <p><b>A/B 对比</b>: 调用方传 {@code engine="RULE"} (走规则) 或 {@code engine="PROMPT"} (走 LLM)。
 * 同时跑两路并输出对比,见 {@code /api/initiations/{id}/ai-wbs/compare}。</p>
 */
@Slf4j
@Service
public class AiWbsPromptService {

    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    @Value("${pmo.ai.prompt.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    @Value("${pmo.ai.prompt.model:qwen-plus}")
    private String model;

    @Value("${pmo.ai.prompt.api-key:}")
    private String apiKey;

    @Value("${pmo.ai.prompt.timeout-seconds:60}")
    private int timeoutSec;

    @Value("${pmo.ai.prompt.temperature:0.2}")
    private double temperature;

    public AiWbsPromptService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 是否启用(有 apiKey 即视为可用) */
    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * 主入口: 用 LLM 把 SOW 拆成标准 draft 结构
     *
     * @param sowText  SOW 原文 (已经 resolveSowText 聚合过)
     * @param industry 算法推荐的行业 (prompt 内部可覆盖,用于 A/B 评估)
     * @return 与规则引擎结构一致的 draft JSON
     */
    public Map<String, Object> generate(String sowText, String industry) {
        if (!enabled()) {
            throw new BusinessException(503,
                    "LLM prompt engine not configured (set PMO_AI_PROMPT_API_KEY env or pmo.ai.prompt.api-key)");
        }
        long t0 = System.currentTimeMillis();
        String prompt = buildPrompt(sowText, industry);
        String content = callChatCompletion(prompt);
        long cost = System.currentTimeMillis() - t0;

        // LLM 偶发输出 markdown 包裹的 JSON,做一次清洗
        String json = stripCodeFence(content);
        // LLM 还可能在字符串里输出未转义换行/制表符,默认 Jackson strict 会拒
        // 用 ALLOW 控制字符 + 单引号 + 注释 三件套,容错
        Map<String, Object> draft;
        try {
            com.fasterxml.jackson.databind.ObjectMapper lenient = new com.fasterxml.jackson.databind.ObjectMapper();
            lenient.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
            lenient.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
            lenient.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS, true);
            draft = lenient.readValue(json, Map.class);
        } catch (Exception e) {
            log.error("[Prompt] LLM output is not valid JSON ({}ms). raw={}", cost, abbreviate(content, 500));
            throw new BusinessException(500,
                    "LLM output invalid JSON: " + e.getMessage() + " (raw=" + abbreviate(content, 200) + ")");
        }

        // V4.20 守门: LLM 输出再走一遍 token 白名单校验, 把不在 SOW 的里程碑名 / WP 名 / 风险 title 裁掉
        sanitizeDraftAgainstSow(draft, sowText);

        // 补强必备字段 (避免 LLM 漏字段)
        draft.putIfAbsent("industry", industry);
        draft.putIfAbsent("totalWeeks", 12);
        draft.putIfAbsent("modelVersion", "prompt-" + model);
        draft.putIfAbsent("source", Map.of(
                "usedBodySowText", true, "usedPasteText", false, "usedFiles", 0));
        draft.put("generatedAt", java.time.Instant.now().toString());
        // 标记是 prompt 引擎生成的 (审计/对比用)
        draft.put("engine", "PROMPT");
        draft.put("promptEngineLatencyMs", cost);

        log.info("[Prompt] generated OK in {}ms, industry={}, milestones={}, wp={}, risks={}",
                cost, draft.get("industry"),
                size(draft, "milestones"), size(draft, "workPackages"), size(draft, "risks"));
        return draft;
    }

    private int size(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v instanceof List<?> l ? l.size() : 0;
    }

    /**
     * 提示词核心: 把 SOW 喂给 LLM,要求严格按指定 JSON Schema 输出。
     * <p>关键点:
     * <ol>
     *   <li>System 注入行业词典 + 角色规则 + 输出 schema</li>
     *   <li>User 只贴 SOW 原文,不诱导多余内容</li>
     *   <li>要求纯 JSON (不要 markdown 包裹,不要解释),用 stop=[".","\n\n"] 兜底</li>
     * </ol>
     */
    private String buildPrompt(String sowText, String industryHint) {
        String sys = """
                你是 PMO 项目立项 AI 助手。请阅读用户给的 SOW (Statement of Work),输出标准 WBS JSON。

                ## 行业识别优先级 (第一个命中即返回)
                1. AI_AGENT: 同时含智能体/agent + 大模型/Qwen/AgentUniverse/多模态
                2. INSURANCE: 含核保/理赔/查勘/定损/准备金/IFRS17/续保/保单
                3. BANKING_CORE: 含核心系统/总账/CIF/五级分类/EAST/1104/超级网银/外汇
                4. SECURITIES: 含证券/券商/交易柜台/量化/投行/IPO/银证/三方存管/适当性
                5. BANKING_LOAN: 含经营贷/抵押贷/消费贷/按揭/信贷/授信/征信/担保/抵押登记
                6. AI: 含 AI/模型/大模型/llm
                7. ERP: 含 ERP/财务/供应链
                8. DATA: 含数据/BI/报表/ETL
                9. CLOUDNATIVE: 含 K8s/Docker/微服务
                10. CRM (兜底)

                ## 【V4.20 强制禁止项 - hallucinate 守门】
                <b>核心禁令:</b> <u>未在 SOW 中出现的系统或功能, 一律不能体现到 WBS 中</u>(不能写进里程碑名、工作包名、deliverable、techStack、sowContext.modules/deliverables、风险 evidence/suggestion 等任何字段)。
                你<b>绝不能</b>编造 SOW 里没有出现的系统 / 功能 / 模块名, 也不能把通用模板短语塞到每个里程碑。
                ① 里程碑名: 不要用 "<里程碑名>(模块1 / 模块2 / 模块3)" 这种模板拼接, 除非每个模块都在 SOW 里逐字出现; 里程碑涉及的业务模块必须能在 SOW 原文找到对应表述
                ② 工作包名 / deliverable: 不得出现 SOW 没提的子系统(如 OCR 适配器 / ESB / Kibana / 中间件网关 / 智能体平台 / 规则引擎 / 工作流引擎 / BI 看板等); deliverable 描述中引用的系统名、功能点、技术组件必须在 SOW 中可定位
                ③ 风险标题: 不得凭空加 SOW 没提到的风险类别, evidence 必须能在 SOW 找到原词, suggestion 中提到的应对措施也只能针对 SOW 实际描述的风险
                ④ 智能体识别 (AI_AGENT): 仅当 SOW 显式提到 [坐席小结 / 语音质检 / 语音打标 / 财报分析] 才输出对应智能体; 否则按通用 AI_AGENT 处理, 不要补全 SOW 未列出的智能体场景
                ⑤ 行业识别时, AI_AGENT 必须 SOW 含 agent/智能体/坐席/语音/打标/财报分析; 否则降级回 INSURANCE / AI / CRM
                ⑥ <b>SOW 未提及的内容宁可少输出, 绝不要补全</b>: SOW 没提到的第三方系统、未列出的报表/接口/页面、未提及的部署环境/技术栈都不要自行追加; 如确实无法判断, 请把对应字段留空或省略, 而不是臆造
                服务端会做二次校验 (token 白名单比对), 不在 SOW 的 token 会自动裁掉 — 请尽量输出 SOW 里有的

                ## 输出 JSON Schema (严格遵守,字段名/类型都不能错)
                {
                  "industry": "<上述 10 种之一>",
                  "totalWeeks": <整数 6~24>,
                  "milestones": [
                    {
                      "code": "1","2","3"...,
                      "name": "<里程碑名,中间里程碑追加 SOW 实际业务模块名>",
                      "targetWeek": <该里程碑预计周>,
                      "workPackageCodes": ["1.x","1.y"] ,
                      "sowContext": {
                        "modules":["业务模块名1","业务模块名2"],
                        "techStack":["技术栈"],
                        "deliverables":["交付物"],
                        "durationRaw":"SOW里的工期原文",
                        "budgetRaw":"SOW里的预算原文"
                      }
                    }
                  ],
                  "workPackages": [
                    {
                      "wbsCode": "1.1",
                      "name": "<WP 名,简洁具体可执行,不要套话>",
                      "ownerRole": "PM|AR|SR|FR|QA|DATA|BA",
                      "estimateHours": <8~160 整数>,
                      "deliverable": "<可交付物描述>"
                    }
                  ],
                  "risks": [
                    {
                      "level": "LOW|MEDIUM|HIGH|CRITICAL",
                      "probability": <1-5>,
                      "impact": <1-5>,
                      "title": "<风险标题>",
                      "evidence": ["触发关键词1"],
                      "suggestion": "<缓解建议>"
                    }
                  ]
                }

                ## 质量要求
                - milestones 数: CRM/ERP/数据/AI 通用 = 6, AI_AGENT = 7, BANKING_LOAN = 7, BANKING_CORE/INSURANCE/SECURITIES = 8
                - workPackages 数: 每个里程碑 3-6 个 WP
                - risks 数: ≥ 5 条,至少 1 条 CRITICAL,覆盖合规/集成/工期/团队
                - 行业为 AI_AGENT 时,从 SOW 识别出 [坐席小结/语音质检/语音打标/财报分析] 中的所有命中的智能体,每个智能体在 M4 阶段都展开 1 份 WP
                - 风险 evidence 字段必填,且必须能在 SOW 里找到原词
                - 不要 markdown 包裹,不要解释,只输出纯 JSON
                """;
        String user = """
                SOW 行业线索 (可被覆盖): %s

                ===== SOW 原文开始 =====
                %s
                ===== SOW 原文结束 =====
                """.formatted(industryHint == null ? "" : industryHint,
                sowText == null ? "" : sowText);
        return sys + "\n\n" + user;
    }

    /**
     * 调用通义千问 OpenAI-compatible chat/completions。
     * 不引第三方 SDK,直接走 HttpClient,减少依赖。
     */
    private String callChatCompletion(String prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", temperature);
        body.put("max_tokens", 4096);
        body.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
        ));

        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new BusinessException(500, "build request json failed: " + e.getMessage());
        }

        HttpRequest req;
        try {
            req = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(timeoutSec))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
        } catch (Exception e) {
            throw new BusinessException(500, "build http request failed: " + e.getMessage());
        }

        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new BusinessException(502, "call LLM failed: " + e.getMessage());
        }

        if (resp.statusCode() / 100 != 2) {
            throw new BusinessException(502,
                    "LLM HTTP " + resp.statusCode() + ": " + abbreviate(resp.body(), 300));
        }

        try {
            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                throw new BusinessException(502, "LLM response has no choices: " + abbreviate(resp.body(), 300));
            }
            return choices.get(0).get("message").get("content").asText();
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException(502, "parse LLM response failed: " + e.getMessage());
        }
    }

    private String stripCodeFence(String s) {
        if (s == null) return "";
        String t = s.trim();
        // ```json ... ``` / ``` ... ```
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            if (firstNl > 0 && firstNl < 12) t = t.substring(firstNl + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t.trim();
    }

private void sanitizeDraftAgainstSow(Map<String, Object> draft, String sowText) {
        if (draft == null) return;
        if (sowText == null || sowText.isBlank()) return; // 没 SOW 原文就不裁,避免空数据
        List<String> dropped = new ArrayList<>();

        // 1) milestones.name — 处理模板拼接 (用 sowText 整段子串匹配)
        Object msObj = draft.get("milestones");
        if (msObj instanceof List<?> msList) {
            List<Map<String, Object>> kept = new ArrayList<>();
            for (Object o : msList) {
                if (!(o instanceof Map<?, ?> mp)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) mp;
                String name = (String) m.get("name");
                if (name != null) {
                    String safe = SowTokenGuard.stripTemplatePhrase(name, sowText);
                    if (!safe.equals(name)) {
                        dropped.add("milestone#" + m.get("code") + ":" + name + "→" + safe);
                    }
                    m.put("name", safe);
                }
                kept.add(m);
            }
            draft.put("milestones", kept);
        }

        // 2) workPackages.name / deliverable — 命中率不够的整条丢弃
        Object wpObj = draft.get("workPackages");
        if (wpObj instanceof List<?> wpList) {
            List<Map<String, Object>> kept = new ArrayList<>();
            for (Object o : wpList) {
                if (!(o instanceof Map<?, ?> mp)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> w = (Map<String, Object>) mp;
                String name = (String) w.getOrDefault("name", "");
                String deliv = (String) w.getOrDefault("deliverable", "");
                String text = name + " " + deliv;
                if (SowTokenGuard.score(text, sowText) < SowTokenGuard.MIN_HIT_RATIO) {
                    dropped.add("wp#" + w.get("wbsCode") + ":" + name);
                    continue;
                }
                kept.add(w);
            }
            draft.put("workPackages", kept);
        }

        // 3) risks.title — 命中率不够的整条丢弃
        Object rkObj = draft.get("risks");
        if (rkObj instanceof List<?> rkList) {
            List<Map<String, Object>> kept = new ArrayList<>();
            for (Object o : rkList) {
                if (!(o instanceof Map<?, ?> mp)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> r = (Map<String, Object>) mp;
                String title = (String) r.getOrDefault("title", "");
                if (SowTokenGuard.score(title, sowText) < SowTokenGuard.MIN_HIT_RATIO) {
                    dropped.add("risk:" + title);
                    continue;
                }
                kept.add(r);
            }
            draft.put("risks", kept);
        }

        if (!dropped.isEmpty()) {
            log.info("[Prompt] SOW-guard dropped {} items: {}", dropped.size(), dropped);
            draft.put("sowGuardDropped", dropped);
        }
    }

    private String abbreviate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}