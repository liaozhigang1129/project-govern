package com.hex.projectgovern.module.notification;

import java.time.Instant;

/**
 * 通知事件基类(sealed interface — 防止意外新增事件类型)。
 *
 * <p>为兼容工时事件(TimesheetSubmitted/Decided),父接口只保留通用方法,
 * 立项/工时各自的"资源 ID/编号"通过实现类自带字段(类型擦除,Java 不会冲突)。
 *
 * <p>使用 Spring 的 ApplicationEventPublisher 发布,NotificationListener 异步消费。
 * 不继承 ApplicationEvent(简化),用 record + 自定义事件。
 */
public sealed interface NotificationEvent permits
        InitiationSubmittedEvent,
        InitiationDecidedEvent,
        InitiationResubmittedEvent,
        TimesheetSubmittedEvent,
        TimesheetDecidedEvent,
        TimesheetBatchApprovedEvent,
        TimesheetReminderEvent,
        MilestoneAdvisoryDecidedEvent {

    /** 资源 ID(对立项=initiationId,对工时=timesheetId) */
    Long resourceId();
    /** 资源编号(展示用) */
    String resourceCode();
    /** 通知标题(用于 UNREAD 表的 title 字段) */
    String title();
    /** 触发人姓名(对立项=申请人,对工时=提交人) */
    String actorName();
    Instant occurredAt();
}
