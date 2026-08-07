package com.company.pmo.module.llm;

import java.util.List;

/**
 * LLM 润色服务 (P5-智能预警 LLM 增强)
 *
 * 设计目标:
 *  - 把 MilestoneAiAdvisor.analyze() 出来的结构化 suggestions 润色成 PM 能直接 copy 给老板的自然语言
 *  - 失败/超时/未配 LLM 一律降级 (返回 null) → 主业务 (建议生成) 永远不被 LLM 拖垮
 *
 * 输入: 触发信号 (signalType, intensity, weight, score, description, missing)
 * 输出: 1 段 ~80 字的中文建议,带"🤖"前缀
 */
public interface LlmPolisher {

    /**
     * 异步润色 (用 @Async 包装)。返回 null 表示 LLM 不可用 / 失败 → 调用方用规则引擎原文。
     *
     * @param projectName  项目名 (上下文)
     * @param milestoneName 里程碑名
     * @param severity     INFO / WARNING / CRITICAL
     * @param totalScore   0-100
     * @param signals      Top-N 触发信号(按 score 降序,已截断 max-suggestions)
     * @return 润色后的中文建议;null 表示 LLM 不可用
     */
    String polish(String projectName,
                  String milestoneName,
                  String severity,
                  double totalScore,
                  List<Signal> signals);

    /**
     * 内部 DTO (避免与 MilestoneAiAdvisor.Signal 耦合)
     */
    record Signal(String type,
                  String description,
                  double intensity,
                  double weight,
                  double score) {
    }
}
