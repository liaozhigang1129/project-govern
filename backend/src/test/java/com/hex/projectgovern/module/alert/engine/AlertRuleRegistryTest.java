package com.hex.projectgovern.module.alert.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AlertRuleRegistry 单测 (WP-M5-02 / T-02)
 */
class AlertRuleRegistryTest {

    private AlertRuleRegistry registry;
    private List<AlertRule> beans;

    @BeforeEach
    void setUp() {
        beans = new ArrayList<>();
        beans.add(stubRule("COST_DIFF", "成本对账差异", "HIGH"));
        beans.add(stubRule("BUDGET_EXCEED", "预算超支", "HIGH"));
        beans.add(stubRule("RESOURCE_OVERLOAD", "资源过载", "MEDIUM"));
        registry = new AlertRuleRegistry(beans);
        registry.init();
    }

    private AlertRule stubRule(String code, String name, String severity) {
        return new AlertRule() {
            @Override public String code() { return code; }
            @Override public String name() { return name; }
            @Override public String severity() { return severity; }
            @Override public double defaultThreshold() { return 0; }
            @Override public List<com.hex.projectgovern.module.alert.AlertEvent> evaluate() { return List.of(); }
            @Override public List<com.hex.projectgovern.module.alert.AlertEvent> evaluate(Long projectId) { return List.of(); }
        };
    }

    @Test
    @DisplayName("init: 3 规则全部注册")
    void init_registersAll() {
        assertThat(registry.size()).isEqualTo(3);
        assertThat(registry.all()).hasSize(3);
    }

    @Test
    @DisplayName("get(code): 命中 / 未命中")
    void get_byCode() {
        assertThat(registry.get("COST_DIFF")).isPresent();
        assertThat(registry.get("COST_DIFF").get().name()).isEqualTo("成本对账差异");
        assertThat(registry.get("NOT_EXIST")).isEmpty();
    }

    @Test
    @DisplayName("init: 重复 code → 后者覆盖 + warn log")
    void init_duplicateOverride() {
        beans.add(stubRule("COST_DIFF", "覆盖", "LOW"));
        registry.init();

        assertThat(registry.size()).isEqualTo(3); // 还是 3 条
        assertThat(registry.get("COST_DIFF").get().name()).isEqualTo("覆盖");
        assertThat(registry.get("COST_DIFF").get().severity()).isEqualTo("LOW");
    }

    @Test
    @DisplayName("init: 空 code → 跳过 + warn log")
    void init_emptyCode() {
        beans.add(stubRule("", "空 code", "LOW"));
        beans.add(stubRule(null, "null code", "LOW"));
        registry.init();

        // 原 3 + 新增 2 (都因为空 code 跳过)
        assertThat(registry.size()).isEqualTo(3);
    }
}
