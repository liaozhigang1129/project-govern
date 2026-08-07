package com.company.zhiyu.module.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    long countByRecipientIdAndStatus(Long recipientId, Notification.NotificationStatus status);

    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    Page<Notification> findByRecipientIdAndStatusOrderByCreatedAtDesc(
            Long recipientId, Notification.NotificationStatus status, Pageable pageable);

    @Modifying
    @Query("update Notification n set n.status = 'READ', n.readAt = :readAt " +
           "where n.recipientId = :rid and n.status = 'UNREAD' and n.id in :ids")
    int markRead(@Param("rid") Long recipientId,
                 @Param("ids") List<Long> ids,
                 @Param("readAt") Instant readAt);

    @Modifying
    @Query("update Notification n set n.status = 'READ', n.readAt = :readAt " +
           "where n.recipientId = :rid and n.status = 'UNREAD'")
    int markAllRead(@Param("rid") Long recipientId, @Param("readAt") Instant readAt);
}