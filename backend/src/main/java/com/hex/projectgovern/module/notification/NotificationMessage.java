package com.hex.projectgovern.module.notification;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 通道无关的通知消息(P2-A IM 通知抽象)。
 *
 * 设计原则:
 *  - 上游业务(NotificationDispatcher)只构造 NotificationMessage,不关心具体通道
 *  - 各 IM 通道(WechatWork/DingTalk/Feishu/Mail)各自把 NotificationMessage 渲染成自家协议
 *  - 一次事件 → 多通道扇出;失败隔离
 *  - recipientUserIds 用来查询 user_im_binding 决定目标 external_user_id
 *
 * actionButtons 用于 IM 卡片"一键审批"(V2 启用,MVP 只展示文本)
 */
public record NotificationMessage(
        String category,            // INITIATION_SUBMIT / INITIATION_DECIDE / INITIATION_SUPPLEMENT ...
        String title,               // 短标题(<= 64 字,IM 卡片标题)
        String summary,             // 摘要正文(纯文本/Markdown,各通道各自渲染)
        String resourceType,        // INITIATION / TIMESHEET ...
        Long resourceId,
        String resourceCode,        // IR-2025-001 / TS-2025-001 ...
        String linkUrl,             // 详情链接(V2 可点击跳转)
        List<Long> recipientUserIds,
        Map<String, Object> extras, // 通道特定扩展(暂未使用)
        Instant occurredAt
) {
    public NotificationMessage {
        if (recipientUserIds == null) recipientUserIds = List.of();
        if (extras == null) extras = Map.of();
        if (occurredAt == null) occurredAt = Instant.now();
    }
}
