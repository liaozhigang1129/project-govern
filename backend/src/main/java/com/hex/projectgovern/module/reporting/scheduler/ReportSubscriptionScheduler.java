package com.hex.projectgovern.module.reporting.scheduler;

import com.hex.projectgovern.module.reporting.ReportSubscription;
import com.hex.projectgovern.module.reporting.ReportSubscriptionService;
import com.hex.projectgovern.module.reporting.async.ReportExportTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * 报表订阅调度器 (WP-M7-03) — 扫描 next_run_at, 触发异步导出任务.
 *
 * <p>周期: 5min (与 AlertScheduler 一致, 简化运维).
 * <p>多实例协调: R-006 SchedulerLockService (本期未集成, v5 计划).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportSubscriptionScheduler {

    private final ReportSubscriptionService subscriptionService;
    private final ReportExportTask exportTask;

    /** 每 5 分钟扫一次 */
    @Scheduled(fixedDelayString = "300000", initialDelayString = "120000")
    public void scan() {
        List<ReportSubscription> due = subscriptionService.findDue();
        if (due.isEmpty()) {
            log.debug("[ReportSubscription] no due subscriptions");
            return;
        }
        log.info("[ReportSubscription] {} subscriptions due", due.size());
        for (ReportSubscription s : due) {
            try {
                exportTask.runAsync(s.getId());
                s.setLastRunAt(Instant.now());
                s.setNextRunAt(Instant.now().plusSeconds(86400));  // 24h 后
            } catch (Exception e) {
                log.error("[ReportSubscription] trigger failed sub={} err={}", s.getId(), e.getMessage());
            }
        }
    }
}
