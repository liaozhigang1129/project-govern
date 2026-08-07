package com.company.zhiyu.module.notification;

import org.springframework.stereotype.Component;

/**
 * 旧 NotificationListener 占位(P2-A: 已迁移到 NotificationDispatcherListener)。
 *
 * 留空类以避免:若有其他模块按类名反射查找导致 NPE。
 * 实际事件处理在 {@link NotificationDispatcherListener}。
 */
@Component
public class NotificationListener {
    // deprecated: 业务事件已由 NotificationDispatcherListener 统一处理
    // 此处保留仅作占位,不再挂 @EventListener,避免重复执行 mailService.onXxx
}
