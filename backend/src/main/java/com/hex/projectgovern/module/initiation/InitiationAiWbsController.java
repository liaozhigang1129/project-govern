package com.hex.projectgovern.module.initiation;

import com.hex.projectgovern.common.api.ApiResponse;
import com.hex.projectgovern.common.audit.AuditLog;
import com.hex.projectgovern.common.security.RequireRoles;
import com.hex.projectgovern.module.org.AppUser;
import com.hex.projectgovern.module.org.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Tag(name = "Initiations / AI WBS 助手", description = "Step 2 智能 WBS 转化(SOW → 里程碑 + 工作包 + 风险)")
@RestController
@RequestMapping("/initiations/{id}/ai-wbs")
@RequiredArgsConstructor
public class InitiationAiWbsController {

    private final InitiationAiWbsService service;
    private final UserRepository userRepository;
    private final AiWbsPromptService promptService;

    /**
     * V4.20: 加 engine 字段, RULE = 规则引擎(默认), PROMPT = LLM 提示词
     */
    public record GenerateRequest(String sowText, Integer granularityWeeks, String engine) {}

    @PostMapping("/generate")
    @RequireRoles.Operate
    @AuditLog(module = "INITIATION", action = "AI_WBS_GENERATE", extractResourceId = false)
    @Operation(summary = "AI 智能 WBS 转化",
        description = "入参:{sowText, granularityWeeks, engine='RULE'|'PROMPT'}; " +
                      "出参:{milestones, workPackages, risks, industry, totalWeeks, modelVersion}")
    public ApiResponse<Map<String, Object>> generate(
            @PathVariable Long id,
            @RequestBody GenerateRequest req,
            @AuthenticationPrincipal UserDetails ud) {
        AppUser user = userRepository.findByUsernameAndDeletedFalse(ud.getUsername()).orElseThrow();

        String engine = (req.engine() == null || req.engine().isBlank()) ? "RULE" : req.engine().toUpperCase();

        if ("PROMPT".equals(engine)) {
            // V4.20: 走 LLM 提示词路径 — 不写 draft 表,只返回,前端展示用
            // 这里也直接用 service 聚合后的 SOW,与规则引擎走同样的聚合源
            String sow = service.resolveSowTextForPrompt(id, req.sowText());
            Map<String, Object> draft = promptService.generate(sow, service.detectIndustryForPrompt(sow));
            return ApiResponse.ok(Map.of(
                    "draftId", 0L,
                    "initiationId", id,
                    "granularityWeeks", req.granularityWeeks() == null ? 2 : req.granularityWeeks(),
                    "modelVersion", draft.get("modelVersion"),
                    "createdAt", java.time.Instant.now().toString(),
                    "engine", "PROMPT",
                    "draft", draft
            ));
        }

        // RULE 引擎(原行为,保持完全兼容)
        InitiationAiWbsDraft d = service.generateDraft(id, req.sowText(), req.granularityWeeks(), user.getId());
        // V4.21: 顶层返 unmatchedAgents (4 智能体 + 未命中原因) + hallucinationReport (被裁掉的 WP)
        // V4.23: 顶层返 sourceMeta.fileExtractions, 让 UI 知道"哪些 PDF 抽到了/哪些没抽到"
        Map<String, Object> draftBody = service.parseDraftJson(d);
        Map<String, Object> src = draftBody.get("source") instanceof Map
                ? (Map<String, Object>) draftBody.get("source")
                : Map.of();
        return ApiResponse.ok(Map.of(
                "draftId", d.getId(),
                "initiationId", d.getInitiationId(),
                "granularityWeeks", d.getGranularityWeeks(),
                "modelVersion", d.getModelVersion(),
                "createdAt", d.getCreatedAt(),
                "engine", "RULE",
                "draft", draftBody,
                "unmatchedAgents", service.latestUnmatchedAgents(),
                "hallucinationReport", service.latestHallucinationReport(),
                "sourceMeta", Map.of(
                        "usedBodySowText", src.getOrDefault("usedBodySowText", false),
                        "usedPasteText", src.getOrDefault("usedPasteText", false),
                        "usedFiles", src.getOrDefault("usedFiles", 0),
                        "extractedFiles", src.getOrDefault("extractedFiles", 0),
                        "failedFiles", src.getOrDefault("failedFiles", 0),
                        "fileExtractions", service.latestFileExtractions()
                )
        ));
    }

    /**
     * V4.20: A/B 对比 — 同一 SOW 同时跑规则引擎 + LLM 提示词,生成对比报告
     */
    @PostMapping("/compare")
    @RequireRoles.Operate
    @AuditLog(module = "INITIATION", action = "AI_WBS_COMPARE", extractResourceId = false)
    @Operation(summary = "AI WBS A/B 对比", description = "同一 SOW 跑 RULE + PROMPT 两路,输出差异指标")
    public ApiResponse<Map<String, Object>> compare(
            @PathVariable Long id,
            @RequestBody GenerateRequest req,
            @AuthenticationPrincipal UserDetails ud) {
        AppUser user = userRepository.findByUsernameAndDeletedFalse(ud.getUsername()).orElseThrow();

        // 1) RULE 路径
        InitiationAiWbsDraft ruleDraft = service.generateDraft(id, req.sowText(), req.granularityWeeks(), user.getId());
        Map<String, Object> ruleBody = service.parseDraftJson(ruleDraft);

        // 2) PROMPT 路径 (如果未配置 apiKey,降级返回错误而不是抛 503)
        Map<String, Object> promptBody = null;
        Map<String, Object> promptMeta = null;
        if (promptService.enabled()) {
            try {
                String sow = service.resolveSowTextForPrompt(id, req.sowText());
                promptBody = promptService.generate(sow, service.detectIndustryForPrompt(sow));
                Object latency = promptBody.get("promptEngineLatencyMs");
                promptMeta = new LinkedHashMap<>();
                promptMeta.put("engine", "PROMPT");
                promptMeta.put("ok", true);
                promptMeta.put("latencyMs", latency);
            } catch (Exception e) {
                promptMeta = new LinkedHashMap<>();
                promptMeta.put("engine", "PROMPT");
                promptMeta.put("ok", false);
                promptMeta.put("error", e.getMessage());
            }
        } else {
            promptMeta = new LinkedHashMap<>();
            promptMeta.put("engine", "PROMPT");
            promptMeta.put("ok", false);
            promptMeta.put("error", "pmo.ai.prompt.api-key not set");
        }

        // 3) 计算差异指标
        Map<String, Object> metrics = computeCompareMetrics(ruleBody, promptBody);

        return ApiResponse.ok(Map.of(
                "initiationId", id,
                "rule", Map.of("draft", ruleBody, "draftId", ruleDraft.getId()),
                "prompt", Map.of("draft", promptBody, "meta", promptMeta),
                "metrics", metrics,
                "createdAt", java.time.Instant.now().toString()
        ));
    }

    private Map<String, Object> computeCompareMetrics(Map<String, Object> rule, Map<String, Object> prompt) {
        if (prompt == null) {
            return Map.of("promptAvailable", false, "ruleAvailable", true);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("promptAvailable", true);
        m.put("ruleAvailable", true);

        m.put("industryRule", rule.get("industry"));
        m.put("industryPrompt", prompt.get("industry"));
        m.put("industryMatch", Objects.equals(rule.get("industry"), prompt.get("industry")));

        m.put("milestoneCountRule", sizeOf(rule, "milestones"));
        m.put("milestoneCountPrompt", sizeOf(prompt, "milestones"));
        m.put("wpCountRule", sizeOf(rule, "workPackages"));
        m.put("wpCountPrompt", sizeOf(prompt, "workPackages"));
        m.put("riskCountRule", sizeOf(rule, "risks"));
        m.put("riskCountPrompt", sizeOf(prompt, "risks"));

        // 行业覆盖一致性: 同样的 modules 集合有多少交集?
        List<String> ruleMods = stringList(rule, List.of("milestones", "sowContext", "modules"));
        List<String> promptMods = stringList(prompt, List.of("milestones", "sowContext", "modules"));
        // 简化: 不去深挖 milestones[].sowContext, 改用 prompt 输出里的 extractedModules(若有)
        m.put("ruleModulesCount", ruleMods.size());
        m.put("promptModulesCount", promptMods.size());

        // 计算总时长
        m.put("totalWeeksRule", rule.get("totalWeeks"));
        m.put("totalWeeksPrompt", prompt.get("totalWeeks"));

        return m;
    }

    private int sizeOf(Map<String, Object> root, String key) {
        Object v = root.get(key);
        return v instanceof List<?> l ? l.size() : 0;
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(Map<String, Object> root, List<String> path) {
        Object cur = root;
        for (String k : path) {
            if (cur instanceof Map<?, ?> mp) cur = mp.get(k);
            else return List.of();
        }
        if (cur instanceof List<?> l) {
            return (List<String>) l;
        }
        return List.of();
    }

    @GetMapping("/latest")
    @RequireRoles.Read
    @Operation(summary = "取最近一份未应用的草稿(Step 3 入口)")
    public ApiResponse<Map<String, Object>> latest(@PathVariable Long id) {
        InitiationAiWbsDraft d = service.latestDraft(id);
        if (d == null) return ApiResponse.ok(Map.of());
        return ApiResponse.ok(Map.of(
                "draftId", d.getId(),
                "initiationId", d.getInitiationId(),
                "granularityWeeks", d.getGranularityWeeks(),
                "modelVersion", d.getModelVersion(),
                "createdAt", d.getCreatedAt(),
                "draft", service.parseDraftJson(d)
        ));
    }

    /**
     * 手动 apply:把 AI 草稿拆解写入 wbs_task + milestone。
     * <p>通常 EXEC 终审通过时自动 apply, 本接口作为"提前 apply"或"重跑"用。</p>
     */
    @PostMapping("/apply/{draftId}")
    @RequireRoles.Operate
    @AuditLog(module = "INITIATION", action = "AI_WBS_APPLY", extractResourceId = true)
    @Operation(summary = "Apply AI 草稿到 wbs_task + milestone",
        description = "前提:立项已建项目(projectId != null)。已 apply 过的草稿会抛 409。")
    public ApiResponse<Map<String, Object>> apply(
            @PathVariable Long id,
            @PathVariable Long draftId,
            @AuthenticationPrincipal UserDetails ud) {
        AppUser user = userRepository.findByUsernameAndDeletedFalse(ud.getUsername()).orElseThrow();
        Map<String, Object> result = service.applyDraft(draftId, user.getId());
        return ApiResponse.ok(result);
    }

    /**
     * 重置 apply 状态(运维/调试用):把某草稿的 applied_at 置空, 允许重新 apply。
     * <p>仅 PMO_ADMIN 可调, 不写审计日志(避免误用)。</p>
     */
    @DeleteMapping("/apply/{draftId}")
    @RequireRoles.Admin
    @Operation(summary = "[Admin] 重置 apply 状态, 允许重跑 apply")
    public ApiResponse<Map<String, Object>> resetApply(@PathVariable Long draftId) {
        service.unmarkApplied(draftId);
        return ApiResponse.ok(Map.of("draftId", draftId, "appliedAt", null, "note", "reset by admin"));
    }
}
