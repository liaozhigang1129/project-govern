package com.company.zhiyu.common.audit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 审计日志专用线程池。
 *
 * <p>8 线程,队列 1000,CallerRunsPolicy(队列满主线程跑,保证不丢)。
 * 审计失败不影响主业务线程,只是 warn 日志。
 *
 * @since 2026-Q1 P1.5-d
 */
@Configuration
public class AsyncConfig {

    @Bean("auditExecutor")
    public Executor auditExecutor() {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(4);
        e.setMaxPoolSize(8);
        e.setQueueCapacity(1000);
        e.setThreadNamePrefix("audit-");
        e.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        e.setWaitForTasksToCompleteOnShutdown(true);
        e.setAwaitTerminationSeconds(10);
        e.initialize();
        return e;
    }
}
