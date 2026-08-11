package com.hex.projectgovern.module.alert;

import com.hex.projectgovern.module.alert.engine.AlertRuleRegistry;
import com.hex.projectgovern.module.alert.AlertEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AlertScheduler 单测 (WP-M5-02 / T-04)
 *
 * 覆盖:
 *  - 全规则扫描 + 入库
 *  - 内存级去重 (5 分钟内同 rule+project 不重复)
 *  - DB 级去重 (24h 内已有未解决 → 跳过)
 *  - 失败隔离 (单规则抛错不影响其他)
 *  - 通知分发集成
 */
class AlertSchedulerTest {

    private AlertRuleRegistry registry;
    private AlertRuleRepository ruleRepo;
    private AlertEventRepository eventRepo;
    private AlertNotifier alertNotifier;
    private com.hex.projectgovern.common.lock.SchedulerLockService lockService;
    private AlertScheduler scheduler;

    private com.hex.projectgovern.module.alert.engine.AlertRule ruleA; // 返回 1 个 event
    private com.hex.projectgovern.module.alert.engine.AlertRule ruleB; // 返回 2 个 event
    private com.hex.projectgovern.module.alert.engine.AlertRule ruleErr; // 抛异常

    @BeforeEach
    void setUp() {
        registry = mock(AlertRuleRegistry.class);
        ruleRepo = mock(AlertRuleRepository.class);
        eventRepo = mock(AlertEventRepository.class);
        alertNotifier = mock(AlertNotifier.class);
        lockService = mock(com.hex.projectgovern.common.lock.SchedulerLockService.class);

        ruleA = stubRule("RULE_A", "Rule A", "HIGH", List.of(sampleEvent(100L)));
        ruleB = stubRule("RULE_B", "Rule B", "MEDIUM", List.of(
                sampleEvent(200L), sampleEvent(201L)));
        ruleErr = throwingRule("RULE_ERR", "Error Rule", "LOW");

        when(registry.all()).thenReturn(List.of(ruleA, ruleB, ruleErr));

        // 默认: 规则都存在 DB
        when(ruleRepo.findByCodeAndDeletedFalse(anyString())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            com.hex.projectgovern.module.alert.AlertRule entity = new com.hex.projectgovern.module.alert.AlertRule();
            entity.setCode(code);
            return java.util.Optional.of(withId(entity, (long) code.hashCode()));
        });
        // 默认: 无 24h 重复
        when(eventRepo.findRecentOpen(anyLong(), anyLong(), any(OffsetDateTime.class)))
            .thenReturn(List.of());
        // 默认 save 返回自身
        when(eventRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // 默认通知返回 1
        when(alertNotifier.dispatch(any(), any())).thenReturn(1);

        when(lockService.tryLock(anyString(), any(java.time.Duration.class))).thenReturn(true);
        scheduler = new AlertScheduler(registry, ruleRepo, eventRepo, alertNotifier, lockService);
    }

    private static <T> T withId(T obj, Long id) {
        try {
            Field f = obj.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(obj, id);
        } catch (Exception e) { throw new RuntimeException(e); }
        return obj;
    }

    private AlertEvent sampleEvent(Long projectId) {
        AlertEvent e = new AlertEvent();
        e.setRuleId(0L);
        e.setSeverity("HIGH");
        e.setMessage("trigger " + projectId);
        e.setTargetType("PROJECT");
        e.setTargetId(projectId);
        e.setProjectId(projectId);
        e.setActualValue(new BigDecimal("100"));
        e.setThresholdValue(new BigDecimal("50"));
        e.setStatus("NEW");
        e.setNotifyStatus("PENDING");
        e.setTriggeredAt(OffsetDateTime.now());
        return e;
    }

    private com.hex.projectgovern.module.alert.engine.AlertRule stubRule(String code, String name, String sev, List<AlertEvent> events) {
        return new com.hex.projectgovern.module.alert.engine.AlertRule() {
            @Override public String code() { return code; }
            @Override public String name() { return name; }
            @Override public String severity() { return sev; }
            @Override public double defaultThreshold() { return 0; }
            @Override public List<AlertEvent> evaluate() { return events; }
            @Override public List<AlertEvent> evaluate(Long projectId) { return events; }
        };
    }

    private com.hex.projectgovern.module.alert.engine.AlertRule throwingRule(String code, String name, String sev) {
        return new com.hex.projectgovern.module.alert.engine.AlertRule() {
            @Override public String code() { return code; }
            @Override public String name() { return name; }
            @Override public String severity() { return sev; }
            @Override public double defaultThreshold() { return 0; }
            @Override public List<AlertEvent> evaluate() {
                throw new RuntimeException("rule error");
            }
            @Override public List<AlertEvent> evaluate(Long projectId) {
                throw new RuntimeException("rule error");
            }
        };
    }

    // ============================================================
    // 正常路径
    // ============================================================

    @Test
    @DisplayName("scan: 3 规则扫到 3 event, 全部入库 + 通知")
    void scan_allCreated() {
        AlertScheduler.ScanResult r = scheduler.scan();

        assertThat(r.rulesScanned).isEqualTo(3);
        // ruleA 1 + ruleB 2 = 3 created; ruleErr 抛错 → errors=1
        assertThat(r.eventsCreated).isEqualTo(3);
        assertThat(r.errors).isEqualTo(1);
        verify(eventRepo, times(3)).save(any(AlertEvent.class));
        verify(alertNotifier, times(3)).dispatch(any(), any());
    }

    @Test
    @DisplayName("scan: 事件入库前 setRuleId 来自 DB")
    void scan_setRuleIdFromDb() {
        scheduler.scan();

        ArgumentCaptor<AlertEvent> cap = ArgumentCaptor.forClass(AlertEvent.class);
        verify(eventRepo, times(3)).save(cap.capture());
        // 所有 save 的 event 都应该有非零 ruleId
        for (AlertEvent e : cap.getAllValues()) {
            assertThat(e.getRuleId()).isNotZero();
        }
    }

    // ============================================================
    // 去重
    // ============================================================

    @Test
    @DisplayName("内存去重: 同 (rule, project) 在窗口期内只入库 1 次")
    void memoryDedup() {
        // 第 1 次扫:ruleA 触发 1 次
        AlertScheduler.ScanResult r1 = scheduler.scan();
        assertThat(r1.eventsCreated).isEqualTo(3);

        // 第 2 次扫 (窗口期内): 全部应被去重
        AlertScheduler.ScanResult r2 = scheduler.scan();
        assertThat(r2.eventsCreated).isZero();
        assertThat(r2.eventsDeduped).isEqualTo(3);
    }

    @Test
    @DisplayName("DB 去重: 24h 内已有未解决 → 跳过")
    void dbDedup() {
        // 先 clear 内存去重
        scheduler.clearInMemoryDedup();
        // 模拟 DB 已有最近未解决事件
        when(eventRepo.findRecentOpen(anyLong(), eq(100L), any()))
            .thenReturn(List.of(sampleEvent(100L)));

        AlertScheduler.ScanResult r = scheduler.scan();

        // ruleA/project=100 被 DB 去重,其他入库
        assertThat(r.eventsCreated).isEqualTo(2); // ruleB 的 200 + 201
        assertThat(r.eventsDeduped).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("clearInMemoryDedup: 清空后下一次扫可以重新入库")
    void clearDedup() {
        scheduler.scan(); // 全部入库
        scheduler.clearInMemoryDedup();
        // 内存清空,但 DB 仍然没数据,内存也空了,所以可以再次入库
        AlertScheduler.ScanResult r = scheduler.scan();
        assertThat(r.eventsCreated).isEqualTo(3);
    }

    // ============================================================
    // 失败隔离
    // ============================================================

    @Test
    @DisplayName("单规则抛错 → errors+1,其他规则仍处理")
    void singleRuleError_isolation() {
        AlertScheduler.ScanResult r = scheduler.scan();

        assertThat(r.errors).isEqualTo(1); // ruleErr
        assertThat(r.eventsCreated).isEqualTo(3); // 其他两个仍处理
    }

    @Test
    @DisplayName("ruleRepo 找不到规则 → skip 该条 event (warn log)")
    void ruleNotFound_skip() {
        // ruleA 在 DB 找不到
        when(ruleRepo.findByCodeAndDeletedFalse("RULE_A")).thenReturn(java.util.Optional.empty());

        AlertScheduler.ScanResult r = scheduler.scan();

        // ruleA 的 event 被 skip;ruleB 的 2 条正常入库
        assertThat(r.eventsCreated).isEqualTo(2);
    }

    @Test
    @DisplayName("notify 抛错 → 不阻断入库 (下一条继续)")
    void notifyError_isolation() {
        when(alertNotifier.dispatch(any(), any()))
            .thenThrow(new RuntimeException("channel down"));

        AlertScheduler.ScanResult r = scheduler.scan();

        // 3 条都入库(无异常),notify 失败仅 warn log
        assertThat(r.eventsCreated).isEqualTo(3);
    }

    @Test
    @DisplayName("event.projectId=null → skip 该 event")
    void nullProjectId_skip() {
        // ruleA 返回 event 没 projectId
        AlertEvent noProj = sampleEvent(null);
        when(registry.all()).thenReturn(List.of(stubRule("RULE_NP", "no project", "LOW", List.of(noProj))));

        AlertScheduler.ScanResult r = scheduler.scan();

        assertThat(r.eventsCreated).isZero();
    }

    // ============================================================
    // 调度入口
    // ============================================================

    @Test
    @DisplayName("scheduledScan: 调用 scan + 捕获顶层异常")
    void scheduledScan_callsScan() {
        // 模拟 scan 内部抛异常
        when(registry.all()).thenThrow(new RuntimeException("registry broken"));

        // 不应抛
        scheduler.scheduledScan();

        // error log 而非抛回
    }

    @Test
    @DisplayName("scheduledScan: 正常调用")
    void scheduledScan_normal() {
        scheduler.scheduledScan();
        verify(registry, atLeastOnce()).all();
    }
}
