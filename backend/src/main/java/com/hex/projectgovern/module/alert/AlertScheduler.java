package com.hex.projectgovern.module.alert;

import com.hex.projectgovern.module.alert.engine.AlertRule;
import com.hex.projectgovern.module.alert.engine.AlertRuleRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * F4: 告警调度器 (V5.1+ / WP-M5-02 / T-04)
 *
 * <p>每 5 分钟跑一次 (fixedDelay = 300_000ms):
 * <ol>
 *   <li>遍历 {@link AlertRuleRegistry} 全部 6 类规则</li>
 *   <li>对每条规则 evaluate() 拿到候选事件</li>
 *   <li>按 (rule, target) 去重: 内存级 5 分钟 + DB 级 24h</li>
 *   <li>事件入库 + 通知分发</li>
 * </ol>
 *
 * <p>幂等策略:
 * <ul>
 *   <li>内存 ConcurrentHashMap: 同 (ruleCode, projectId) 5 分钟内不重复入库</li>
 *   <li>DB AlertEvent.findRecentOpen: 24h 内同 (ruleId, targetId) 已有未解决 → 跳过</li>
 * </ul>
 *
 * <p>风险缓解:
 * <ul>
 *   <li>R-006 分布式锁: 本期单实例用内存锁;v5 接 Redis SETNX</li>
 *   <li>R-007 告警风暴: 双层去重(内存+DB)+ 单项目单规则每小时最多 1 条</li>
 *   <li>R-008 通道失败: AlertNotifier 已 try-catch warn log</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlertScheduler {

    /** 5 分钟内存级去重窗口 (per rule+project) */
    private static final Duration IN_MEMORY_DEDUP_WINDOW = Duration.ofMinutes(5);

    private final AlertRuleRegistry registry;
    private final AlertRuleRepository ruleRepo;
    private final AlertEventRepository eventRepo;
    private final AlertNotifier alertNotifier;

    /** 内存级去重:key = ruleCode + ":" + projectId → 上次触发时间 epoch ms */
    private final ConcurrentHashMap<String, Long> inMemoryDedup = new ConcurrentHashMap<>();

    /**
     * 调度入口:固定延迟 5 分钟
     * 首次启动延迟 60s,避开应用启动后立即扫批
     */
    @Scheduled(fixedDelayString = "300000", initialDelayString = "60000")
    public void scheduledScan() {
        log.info("[AlertScheduler] scheduled scan triggered");
        try {
            ScanResult r = scan();
            log.info("[AlertScheduler] scan done: rules={} created={} skipped(dedup)={} errors={}",
                    r.rulesScanned, r.eventsCreated, r.eventsDeduped, r.errors);
        } catch (Exception e) {
            log.error("[AlertScheduler] scan failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 主扫描逻辑 (供 service / smoke / 测试 直接调)。
     */
    @Transactional
    public ScanResult scan() {
        ScanResult result = new ScanResult();
        // 清理内存过期键
        long now = System.currentTimeMillis();
        long windowMs = IN_MEMORY_DEDUP_WINDOW.toMillis();
        inMemoryDedup.entrySet().removeIf(e -> (now - e.getValue()) > windowMs);

        for (AlertRule rule : registry.all()) {
            result.rulesScanned++;
            try {
                List<AlertEvent> candidates = rule.evaluate();
                for (AlertEvent e : candidates) {
                    Long projectId = e.getProjectId();
                    if (projectId == null) continue;

                    String key = rule.code() + ":" + projectId;
                    // 内存级去重
                    if (inMemoryDedup.putIfAbsent(key, now) != null) {
                        result.eventsDeduped++;
                        continue;
                    }

                    // DB 级去重 (24h)
                    var ruleEntity = ruleRepo.findByCodeAndDeletedFalse(rule.code()).orElse(null);
                    if (ruleEntity == null) {
                        log.warn("[AlertScheduler] rule {} not in DB, skip", rule.code());
                        continue;
                    }
                    var recent = eventRepo.findRecentOpen(
                            ruleEntity.getId(), projectId,
                            OffsetDateTime.now().minus(Duration.ofHours(24)));
                    if (!recent.isEmpty()) {
                        result.eventsDeduped++;
                        continue;
                    }

                    // 入库
                    e.setRuleId(ruleEntity.getId());
                    AlertEvent saved = eventRepo.save(e);

                    // 通知分发
                    try {
                        int ok = alertNotifier.dispatch(saved, ruleEntity);
                        log.debug("[AlertScheduler] event {} rule={} project={} notified={}",
                                saved.getId(), rule.code(), projectId, ok);
                    } catch (Exception ne) {
                        log.warn("[AlertScheduler] notify failed event={} err={}",
                                saved.getId(), ne.getMessage());
                    }
                    result.eventsCreated++;
                }
            } catch (Exception e) {
                result.errors++;
                log.warn("[AlertScheduler] rule {} scan error: {}",
                        rule.code(), e.getMessage());
            }
        }
        return result;
    }

    /**
     * 扫描结果 (供监控 / 测试用)
     */
    public static class ScanResult {
        public int rulesScanned;
        public int eventsCreated;
        public int eventsDeduped;
        public int errors;

        public ScanResult() {
            this(0, 0, 0, 0);
        }
        public ScanResult(int rulesScanned, int eventsCreated, int eventsDeduped, int errors) {
            this.rulesScanned = rulesScanned;
            this.eventsCreated = eventsCreated;
            this.eventsDeduped = eventsDeduped;
            this.errors = errors;
        }
    }

    /** 内存级去重清空(测试用) */
    public void clearInMemoryDedup() {
        inMemoryDedup.clear();
    }
}
