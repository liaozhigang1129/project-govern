package com.hex.projectgovern.module.reporting.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 订阅事件监听器 (WP-M7-03).
 * 实际: 调 NotificationDispatcher 把报表分发到 email/im.
 * MVP 阶段: 只 log, 留 v5 落地.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionEventListener {

    @EventListener
    public void onTriggered(SubscriptionTriggeredEvent e) {
        log.info("[SubscriptionEvent] triggered sub={} channel={} filename={} size={} bytes",
            e.getSubscriptionId(), e.getChannelSet(), e.getFilename(), e.getExportContent().length);
        // 实际: notificationService.dispatch(...)
    }
}
