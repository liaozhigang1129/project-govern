package com.company.zhiyu.module.llm;

import com.company.zhiyu.module.milestoneai.MilestoneAiAdvisory;
import com.company.zhiyu.module.milestoneai.MilestoneAiAdvisor;
import com.company.zhiyu.module.milestoneai.MilestoneAiAdvisoryRepository;
import com.company.zhiyu.module.milestoneai.MilestoneAiSignal;
import com.company.zhiyu.module.milestoneai.MilestoneAiSignalRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * LLM 润色监听器 (P5-智能预警 LLM 增强)
 *
 * 触发: MilestoneAdvisoryDecidedEvent (severity = CRITICAL/WARNING)
 * 流程:
 *  1) 读 advisory + signals
 *  2) 排序取 Top-N 触发信号
 *  3) 调 LlmPolisher.polish() (异步 + 8s timeout + 降级)
 *  4) 把润色结果回写 advisory.suggestions_json (LLM_POLISHED 标记)
 *  5) 不抛异常 (异步 + try/catch 全包)
 *
 * 降级策略:
 *  - LLM 关闭 / 失败 / 超时 → 不改库,前端只看到规则引擎原文 (reasons_json)
 *  - 这是 feature flag 模式: 关闭 LLM = 当前功能
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LlmPolishListener {

    private final LlmPolisher polisher;        // may be NoOpLlmPolisher (未启用时)
    private final LlmProperties props;        // for max-suggestions
    private final MilestoneAiAdvisoryRepository advisoryRepo;
    private final MilestoneAiSignalRepository signalRepo;
    private final ObjectMapper om;

    @Async
    @EventListener
    @Transactional
    public void onAdvisoryDecided(com.company.zhiyu.module.notification.MilestoneAdvisoryDecidedEvent e) {
        try {
            Optional<MilestoneAiAdvisory> opt = advisoryRepo.findById(e.advisoryId());
            if (opt.isEmpty()) return;
            MilestoneAiAdvisory a = opt.get();

            // INFO 级别默认跳过 (避免 LLM 噪音)
            if ("INFO".equalsIgnoreCase(a.getSeverity())) {
                log.debug("[LLM] skip INFO: advisory={}", e.advisoryId());
                return;
            }

            // 1) 拉 signals
            List<MilestoneAiSignal> allSignals = signalRepo.findByAdvisoryIdOrderByIdAsc(e.advisoryId());
            if (allSignals.isEmpty()) {
                log.debug("[LLM] no signals: advisory={}", e.advisoryId());
                return;
            }

            // 2) Top-N 触发信号 (按 score 降序, 截断到 maxSuggestions)
            List<MilestoneAiSignal> top = allSignals.stream()
                    .sorted((x, y) -> Double.compare(
                            y.getScore() == null ? 0 : y.getScore().doubleValue(),
                            x.getScore() == null ? 0 : x.getScore().doubleValue()))
                    .limit(Math.max(1, props.getMaxSuggestions()))
                    .toList();

            List<LlmPolisher.Signal> input = top.stream()
                    .map(s -> new LlmPolisher.Signal(
                            s.getSignalType(),
                            s.getDescription() == null ? "" : s.getDescription(),
                            s.getIntensity() == null ? 0 : s.getIntensity().doubleValue(),
                            s.getWeight() == null ? 0 : s.getWeight().doubleValue(),
                            s.getScore() == null ? 0 : s.getScore().doubleValue()))
                    .toList();

            // 3) 调 LLM
            String polished = polisher.polish(
                    e.projectName(),
                    e.milestoneName(),
                    a.getSeverity(),
                    a.getScore() == null ? 0 : a.getScore().doubleValue(),
                    input);

            if (polished == null || polished.isBlank()) {
                log.debug("[LLM] polisher returned null/blank: advisory={}", e.advisoryId());
                return;
            }

            // 4) 回写 suggestions_json
            //    格式: [{source:"RULE",text:"..."}, {source:"LLM",text:"🤖 ..."}]
            ArrayNode arr = om.createArrayNode();

            // 保留 rule 引擎原文 (如果有)
            try {
                if (a.getSuggestionsJson() != null && !a.getSuggestionsJson().isBlank()) {
                    try {
                        var node = om.readTree(a.getSuggestionsJson());
                        if (node != null && node.isArray()) node.forEach(arr::add);
                    } catch (Exception ignore) {}
                }
            } catch (Exception ignore) {
                // 原 json 损坏, 忽略
            }

            ObjectNode llmNode = om.createObjectNode();
            llmNode.put("source", "LLM");
            llmNode.put("text", "🤖 " + polished);
            arr.add(llmNode);

            a.setLlmSummary(polished);
            a.setSuggestionsJson(arr.toString());
            advisoryRepo.save(a);
            log.info("[LLM] polished advisory={} bytes={}", e.advisoryId(), polished.length());
        } catch (Exception ex) {
            // 全包: LLM 链路失败不影响主业务
            log.warn("[LLM] listener failed: advisory={} err={}", e.advisoryId(), ex.getMessage());
        }
    }
}
