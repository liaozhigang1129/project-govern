package com.hex.projectgovern.module.alert;

import com.hex.projectgovern.module.finance.ReconciliationService.CostDiffDetectedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * CostDiffAlertListener 单测 (WP-M4-03 / T-04)
 */
class CostDiffAlertListenerTest {

    private final AlertRuleRepository ruleRepo = mock(AlertRuleRepository.class);
    private final AlertEventRepository eventRepo = mock(AlertEventRepository.class);
    private final CostDiffAlertListener listener = new CostDiffAlertListener(ruleRepo, eventRepo);

    private AlertRule rule() {
        AlertRule r = new AlertRule();
        r.setCode("RULE_COST_DIFF_100");
        r.setTypeCode("COST_DIFF");
        r.setName("成本对账差异 ≥ ¥100 警告");
        r.setThreshold(new BigDecimal("100.00"));
        r.setComparison("GT");
        r.setSeverity("HIGH");
        r.setEnabled(true);
        return r;
    }

    /** 反射设 id,模拟 save 返回值 */
    private static <T> T withId(T obj, Long id) {
        try {
            Field f = obj.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(obj, id);
        } catch (Exception e) { throw new RuntimeException(e); }
        return obj;
    }

    // ============================================================
    // 核心触发
    // ============================================================

    @Test
    @DisplayName("创建 alert_event: rule 存在 + 无近期重复")
    void createsEvent() {
        AlertRule r = withId(rule(), 7L);
        when(ruleRepo.findByCodeAndDeletedFalse("RULE_COST_DIFF_100")).thenReturn(Optional.of(r));
        when(eventRepo.findRecentOpen(eq(7L), eq(100L), any(OffsetDateTime.class)))
            .thenReturn(List.of());
        when(eventRepo.save(any(AlertEvent.class)))
            .thenAnswer(inv -> withId(inv.getArgument(0), 999L));

        listener.onCostDiff(new CostDiffDetectedEvent(100L, 5));

        ArgumentCaptor<AlertEvent> cap = ArgumentCaptor.forClass(AlertEvent.class);
        verify(eventRepo).save(cap.capture());
        AlertEvent e = cap.getValue();
        assertThat(e.getRuleId()).isEqualTo(7L);
        assertThat(e.getSeverity()).isEqualTo("HIGH");
        assertThat(e.getTargetType()).isEqualTo("PROJECT");
        assertThat(e.getTargetId()).isEqualTo(100L);
        assertThat(e.getProjectId()).isEqualTo(100L);
        assertThat(e.getActualValue()).isEqualByComparingTo("5");
        assertThat(e.getThresholdValue()).isEqualByComparingTo("100");
        assertThat(e.getStatus()).isEqualTo("NEW");
        assertThat(e.getNotifyStatus()).isEqualTo("PENDING");
        assertThat(e.getMessage()).contains("100");
    }

    @Test
    @DisplayName("rule 不存在 → 跳过,不创建事件")
    void ruleNotFound_skipped() {
        when(ruleRepo.findByCodeAndDeletedFalse("RULE_COST_DIFF_100")).thenReturn(Optional.empty());

        listener.onCostDiff(new CostDiffDetectedEvent(100L, 5));

        verify(eventRepo, never()).save(any());
    }

    @Test
    @DisplayName("rule disabled → 跳过")
    void ruleDisabled_skipped() {
        AlertRule r = withId(rule(), 7L);
        r.setEnabled(false);
        when(ruleRepo.findByCodeAndDeletedFalse("RULE_COST_DIFF_100")).thenReturn(Optional.of(r));

        listener.onCostDiff(new CostDiffDetectedEvent(100L, 5));

        verify(eventRepo, never()).save(any());
    }

    // ============================================================
    // 去重
    // ============================================================

    @Test
    @DisplayName("24h 内已有未解决事件 → 跳过")
    void dedup_skipped() {
        AlertRule r = withId(rule(), 7L);
        when(ruleRepo.findByCodeAndDeletedFalse("RULE_COST_DIFF_100")).thenReturn(Optional.of(r));
        when(eventRepo.findRecentOpen(eq(7L), eq(100L), any(OffsetDateTime.class)))
            .thenReturn(List.of(new AlertEvent()));

        listener.onCostDiff(new CostDiffDetectedEvent(100L, 5));

        verify(eventRepo, never()).save(any());
    }

    @Test
    @DisplayName("24h 内无未解决事件 → 创建")
    void dedup_noRecent_creates() {
        AlertRule r = withId(rule(), 7L);
        when(ruleRepo.findByCodeAndDeletedFalse("RULE_COST_DIFF_100")).thenReturn(Optional.of(r));
        when(eventRepo.findRecentOpen(eq(7L), eq(100L), any(OffsetDateTime.class)))
            .thenReturn(List.of());

        listener.onCostDiff(new CostDiffDetectedEvent(100L, 5));

        verify(eventRepo, times(1)).save(any(AlertEvent.class));
    }

    // ============================================================
    // 失败隔离
    // ============================================================

    @Test
    @DisplayName("ruleRepo 抛错 → 不抛回主流程")
    void ruleRepoFailure_isolation() {
        when(ruleRepo.findByCodeAndDeletedFalse(anyString()))
            .thenThrow(new RuntimeException("DB error"));

        listener.onCostDiff(new CostDiffDetectedEvent(100L, 5));

        verify(eventRepo, never()).save(any());
    }

    @Test
    @DisplayName("eventRepo.save 抛错 → 不抛回主流程")
    void saveFailure_isolation() {
        AlertRule r = withId(rule(), 7L);
        when(ruleRepo.findByCodeAndDeletedFalse("RULE_COST_DIFF_100")).thenReturn(Optional.of(r));
        when(eventRepo.findRecentOpen(anyLong(), anyLong(), any()))
            .thenReturn(List.of());
        when(eventRepo.save(any())).thenThrow(new RuntimeException("save failed"));

        listener.onCostDiff(new CostDiffDetectedEvent(100L, 5));
    }
}
