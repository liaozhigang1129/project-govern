package com.company.pmo.module.notification;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * 通知中心持久化(P1.5 收尾)
 *
 * 解决原来 NotificationListener 只发邮件(用户不在邮件中也能看到)
 * - 事件触发时:写一行(status=UNREAD),再发邮件
 * - 前端右上角铃铛:轮询 /unread-count
 * - 点击铃铛:list 分页
 * - 点单条:mark-read
 * - 批量:mark-all-read
 */
@Entity
@Table(name = "notification", indexes = {
        @Index(name = "ix_notification_recipient_unread", columnList = "recipient_id,status,created_at"),
        @Index(name = "ix_notification_recipient_created", columnList = "recipient_id,created_at")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Notification {

    public enum NotificationStatus { UNREAD, READ }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 谁接收(永远 = 当前操作者的 user.id) */
    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    /** 业务分类:INITIATION_SUBMIT / INITIATION_DECIDE / INITIATION_SUPPLEMENT */
    @Column(nullable = false, length = 32)
    private String category;

    /** 关联业务资源(立项 id / 审批 id) */
    @Column(name = "resource_id")
    private Long resourceId;

    /** 业务编号(冗余,方便列表展示不用 JOIN) */
    @Column(name = "resource_code", length = 64)
    private String resourceCode;

    @Column(nullable = false, length = 256)
    private String title;

    @Column(columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationStatus status;

    @Column(name = "read_at")
    private Instant readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}