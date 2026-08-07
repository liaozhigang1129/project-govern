package com.company.zhiyu.module.notification;

import com.company.zhiyu.module.notification.NotificationEvent;

import java.time.Instant;
import java.util.List;

/**
 * P5-里程碑 AI 预警: 当建议生成 (CRITICAL/WARNING) 时, 异步触发通知派发
 *
 * 收件人:
 *  - 项目 PM (pm_user_id) — 主要负责处理
 *  - 项目 Sponsor (sponsor_user_id) — 知情 / 升级
 *  - 触发者 (operatorUserId) — 跑分析的人
 *
 * 静默规则:
 *  - INFO 级别默认不通知 (避免噪音)
 *  - 用户在 IM 静默时段内 (UserImQuietHours) → 写入 UNREAD, 不发 IM
 *  - 同一 advisory 多次 publish 去重 (envelope 内部, 见 NotificationDispatcher)
 */
public record MilestoneAdvisoryDecidedEvent(
        Long advisoryId,
        String advisoryCode,            // 形如 "AI-M-12345" (projectId-milestoneId-score 整数)
        String title,                   // "🔴 里程碑 AI 预警: <milestoneName>"
        String summary,                 // 摘要 + 5 维信号描述
        Long projectId,
        String projectName,
        Long milestoneId,
        String milestoneName,
        String severity,                // CRITICAL / WARNING / INFO
        Double score,
        Double confidence,
        List<String> reasons,
        List<String> suggestions,
        List<Long> recipientUserIds,    // 收件人 user_id 列表 (PM + Sponsor + operator)
        Long operatorUserId,            // 触发者
        Instant occurredAt
) implements NotificationEvent {
    @Override public Long resourceId() { return advisoryId; }
    @Override public String resourceCode() { return advisoryCode; }
    @Override public String actorName() { return "AI Advisor"; }
}
