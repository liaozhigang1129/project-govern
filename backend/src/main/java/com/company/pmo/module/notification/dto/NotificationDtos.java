package com.company.pmo.module.notification.dto;

import com.company.pmo.module.notification.Notification;

import java.time.Instant;

public class NotificationDtos {

    public record UnreadCount(int count) {}

    public record PageResponse<T>(java.util.List<T> rows, long total, int page, int size) {}

    public record MarkReq(java.util.List<Long> ids) {}

    public record View(
            Long id,
            Long recipientId,
            String category,
            Long resourceId,
            String resourceCode,
            String title,
            String content,
            String status,
            Instant readAt,
            Instant createdAt
    ) {
        public static View of(Notification n) {
            return new View(
                    n.getId(), n.getRecipientId(), n.getCategory(),
                    n.getResourceId(), n.getResourceCode(),
                    n.getTitle(), n.getContent(),
                    n.getStatus().name(), n.getReadAt(), n.getCreatedAt()
            );
        }
    }
}