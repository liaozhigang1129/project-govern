package com.hex.projectgovern.module.llm;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * NoOp LlmPolisher (默认, LLM 关闭时使用)
 *
 * 永远返回 null → 调用方 fallback 到规则引擎原文
 */
@Service
@ConditionalOnProperty(prefix = "pmo.llm", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpLlmPolisher implements LlmPolisher {
    @Override
    public String polish(String projectName, String milestoneName, String severity,
                         double totalScore, List<Signal> signals) {
        return null;
    }
}
