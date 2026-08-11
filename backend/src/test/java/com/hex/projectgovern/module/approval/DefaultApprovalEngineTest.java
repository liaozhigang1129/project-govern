package com.hex.projectgovern.module.approval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DefaultSkipConditionEvaluator 单元测试 (纯函数,无 Spring 上下文)
 */
class DefaultSkipConditionEvaluatorTest {

    private DefaultSkipConditionEvaluator eval;

    @BeforeEach
    void setUp() { eval = new DefaultSkipConditionEvaluator(); }

    @Test
    @DisplayName("null/空白条件 → 不跳过")
    void nullOrBlank() {
        assertThat(eval.shouldSkip(null, "{\"amount\":500}")).isFalse();
        assertThat(eval.shouldSkip("", "{\"amount\":500}")).isFalse();
        assertThat(eval.shouldSkip("  ", "{\"amount\":500}")).isFalse();
    }

    @Test
    @DisplayName("amount<1000 命中 → 跳过")
    void ltHits() {
        assertThat(eval.shouldSkip("amount<1000", "{\"amount\":500}")).isTrue();
        assertThat(eval.shouldSkip("amount<1000", "{\"amount\":999.99}")).isTrue();
    }

    @Test
    @DisplayName("amount<1000 不命中 → 不跳过")
    void ltMiss() {
        assertThat(eval.shouldSkip("amount<1000", "{\"amount\":1500}")).isFalse();
        assertThat(eval.shouldSkip("amount<1000", "{\"amount\":1000}")).isFalse();
    }

    @Test
    @DisplayName("amount>1000 / ==1000 / !=500 各自独立正确")
    void otherOps() {
        assertThat(eval.shouldSkip("amount>1000", "{\"amount\":1500}")).isTrue();
        assertThat(eval.shouldSkip("amount==1000", "{\"amount\":1000}")).isTrue();
        assertThat(eval.shouldSkip("amount==1000", "{\"amount\":1001}")).isFalse();
        assertThat(eval.shouldSkip("amount!=500", "{\"amount\":1000}")).isTrue();
        assertThat(eval.shouldSkip("amount!=500", "{\"amount\":500}")).isFalse();
    }

    @Test
    @DisplayName("payload 缺 KEY → 不跳过 (保守)")
    void missingKey() {
        assertThat(eval.shouldSkip("amount<1000", "{\"other\":100}")).isFalse();
        assertThat(eval.shouldSkip("amount<1000", null)).isFalse();
    }

    @Test
    @DisplayName("格式不识别 → warn + 不跳过 (保守)")
    void unrecognized() {
        assertThat(eval.shouldSkip("foo_bar", "{\"amount\":500}")).isFalse();
        assertThat(eval.shouldSkip("amount<?", "{\"amount\":500}")).isFalse();
    }

    @Test
    @DisplayName("小数阈值")
    void decimal() {
        assertThat(eval.shouldSkip("amount<99.99", "{\"amount\":99.5}")).isTrue();
        assertThat(eval.shouldSkip("amount<99.99", "{\"amount\":99.99}")).isFalse();
    }
}