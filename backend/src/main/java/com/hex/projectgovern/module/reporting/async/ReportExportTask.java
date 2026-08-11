package com.hex.projectgovern.module.reporting.async;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 报表异步导出任务 (WP-M7-03).
 *
 * <p>实际工作流:
 *  1. 拉 subscription (template / dashboard / params)
 *  2. 调 ReportTemplateService.render() 拿数据
 *  3. 调对应 Exporter (PDF / Excel / CSV / PNG) 渲染
 *  4. 按 channelSet 分发 (email / im / 链接)
 *
 * <p>MVP 阶段: render() 返回元信息, 不发邮件/IM, 留 v5 落地.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportExportTask {

    @Async("reportTaskExecutor")
    public void runAsync(Long subscriptionId) {
        long start = System.currentTimeMillis();
        try {
            log.info("[ReportExportTask] start sub={}", subscriptionId);
            // 实际渲染留 v5
            Thread.sleep(50);
            log.info("[ReportExportTask] done sub={} dur={}ms", subscriptionId, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("[ReportExportTask] failed sub={} err={}", subscriptionId, e.getMessage());
        }
    }

    /** 手动触发 (供 test 验证) */
    public void runSync(Long subscriptionId) {
        log.info("[ReportExportTask] sync run sub={} at={}", subscriptionId, Instant.now());
    }
}
