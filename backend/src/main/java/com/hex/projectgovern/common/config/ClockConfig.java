package com.hex.projectgovern.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 系统 Clock 注入(P2 #2)。
 *
 * 业务:
 *  - QuietHoursEvaluator 用 Clock 计算"现在几点" → 可单测
 *  - 集成测试用 Clock.fixed()
 *  - 默认 = Clock.systemDefaultZone() (生产)
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
