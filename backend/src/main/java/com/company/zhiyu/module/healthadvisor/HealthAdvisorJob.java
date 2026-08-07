package com.company.zhiyu.module.healthadvisor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日凌晨 02:00 跑全量健康度评估(默认关闭,生产用 PMO_HEALTH_JOB_ENABLED=true 开启)。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pmo.health-advisor.job-enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class HealthAdvisorJob {

    private final HealthAdvisorService service;

    @Scheduled(cron = "${pmo.health-advisor.job-cron:0 0 2 * * *}", zone = "Asia/Shanghai")
    public void runDaily() {
        log.info("[HealthAdvisorJob] 每日跑批开始");
        try {
            var list = service.runForAll(true);
            log.info("[HealthAdvisorJob] 每日跑批完成:评估 {} 个", list.size());
        } catch (Exception e) {
            log.error("[HealthAdvisorJob] 跑批失败", e);
        }
    }
}
