package com.hex.projectgovern.module.reporting.async;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 报表异步任务线程池 (WP-M7-03).
 */
@Configuration
public class ReportTaskExecutorConfig {

    @Bean(name = "reportTaskExecutor")
    public Executor reportTaskExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(8);
        ex.setQueueCapacity(100);
        ex.setThreadNamePrefix("report-");
        ex.setKeepAliveSeconds(60);
        ex.initialize();
        return ex;
    }
}
