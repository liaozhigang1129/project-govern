package com.hex.projectgovern.module.reporting.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 订阅触发事件 (WP-M7-03).
 * 触发后, NotificationDispatcher 路由到 email/im 通道.
 */
@Getter
public class SubscriptionTriggeredEvent extends ApplicationEvent {
    private final Long subscriptionId;
    private final String channelSet;
    private final byte[] exportContent;
    private final String filename;

    public SubscriptionTriggeredEvent(Object source, Long subscriptionId, String channelSet, byte[] exportContent, String filename) {
        super(source);
        this.subscriptionId = subscriptionId;
        this.channelSet = channelSet;
        this.exportContent = exportContent;
        this.filename = filename;
    }
}
