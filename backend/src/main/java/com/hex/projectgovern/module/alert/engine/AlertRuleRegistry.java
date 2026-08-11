package com.hex.projectgovern.module.alert.engine;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 规则注册中心 (V5.1+ / WP-M5-02 / T-02)
 *
 * <p>Spring 启动时自动收集所有 {@link AlertRule} bean,按 code() 注册。
 * 调度器通过 {@link #get(String)} / {@link #all()} 访问。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlertRuleRegistry {

    private final List<AlertRule> rules;
    private final Map<String, AlertRule> byCode = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        byCode.clear();
        for (AlertRule r : rules) {
            String code = r.code();
            if (code == null || code.isBlank()) {
                log.warn("[AlertRuleRegistry] rule {} has empty code, skip", r.getClass().getSimpleName());
                continue;
            }
            if (byCode.containsKey(code)) {
                log.warn("[AlertRuleRegistry] duplicate code {}: {} vs {}, override",
                        code, byCode.get(code).getClass().getSimpleName(), r.getClass().getSimpleName());
            }
            byCode.put(code, r);
        }
        log.info("[AlertRuleRegistry] registered {} alert rule(s): {}",
                byCode.size(), byCode.keySet());
    }

    public Optional<AlertRule> get(String code) {
        return Optional.ofNullable(byCode.get(code));
    }

    public List<AlertRule> all() {
        return List.copyOf(byCode.values());
    }

    public int size() {
        return byCode.size();
    }
}
