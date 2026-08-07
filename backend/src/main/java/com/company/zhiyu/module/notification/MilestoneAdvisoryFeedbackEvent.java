package com.company.zhiyu.module.notification;

import java.time.Instant;

/**
 * P5-PM 反馈事件
 * 多个 @Async 监听器: 增量学习 / KPI 更新 / 通知
 */
public record MilestoneAdvisoryFeedbackEvent(
        Long advisoryId,
        String feedbackType,    // ACCEPTED / REJECTED / MISLEAD / EXPIRED
        String reasonCode,
        String comment,
        Long feedbackBy,
        String modelVersion,
        Instant feedbackAt
) {}
