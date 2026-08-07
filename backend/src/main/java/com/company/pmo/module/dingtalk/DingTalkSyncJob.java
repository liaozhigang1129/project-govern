package com.company.pmo.module.dingtalk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 钉钉同步定时任务 (V2.13 Phase 1)
 *
 * - cron 由 system_config integration.dingtalk.sync_cron 控制 (默认 0 0 2 * * * = 每天 02:00)
 * - @Scheduled 的 cron 表达式在启动时锁定 (不支持热更新 cron)
 *   → 如果改了 sync_cron, 需要重启后端才生效
 * - @ConditionalOnProperty false → 完全跳过, 不占资源
 */
@Component
@RequiredArgsConstructor
@Slf4j
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "pmo.dingtalk.sync-job-enabled", havingValue = "true", matchIfMissing = false)
public class DingTalkSyncJob {

    private final DingTalkSyncService sync;

    @Scheduled(cron = "${pmo.dingtalk.sync-cron:0 0 2 * * *}", zone = "Asia/Shanghai")
    public void runDaily() {
        log.info("[DingTalkSyncJob] 每日跑批开始");
        try {
            DingTalkSyncLog result = sync.syncNow("CRON", "SYSTEM");
            log.info("[DingTalkSyncJob] 跑批完成 status={} depts={} users={} disabled={}",
                    result.getStatus(), result.getTotalDepts(), result.getTotalUsers(), result.getDisabledCount());
        } catch (Exception e) {
            log.error("[DingTalkSyncJob] 跑批失败", e);
        }
    }
}
