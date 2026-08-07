package com.company.pmo.module.notification;

import com.company.pmo.common.security.SecurityUtils;
import com.company.pmo.module.notification.dto.NotificationDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repo;
    @Autowired private SecurityUtils securityUtils;

    public NotificationDtos.UnreadCount unreadCount() {
        return new NotificationDtos.UnreadCount(
                (int) repo.countByRecipientIdAndStatus(currentUserId(),
                        Notification.NotificationStatus.UNREAD));
    }

    public NotificationDtos.PageResponse<NotificationDtos.View> page(int page, int size, String status) {
        if (size <= 0 || size > 100) size = 20;
        Page<Notification> p = (status == null || status.isBlank())
                ? repo.findByRecipientIdOrderByCreatedAtDesc(currentUserId(),
                        PageRequest.of(Math.max(0, page), size))
                : repo.findByRecipientIdAndStatusOrderByCreatedAtDesc(currentUserId(),
                        Notification.NotificationStatus.valueOf(status),
                        PageRequest.of(Math.max(0, page), size));
        return new NotificationDtos.PageResponse<>(
                p.map(NotificationDtos.View::of).getContent(),
                p.getTotalElements(), p.getNumber(), p.getSize());
    }

    @Transactional
    public int markRead(List<Long> ids) {
        Long me = currentUserId();
        Instant now = Instant.now();
        if (ids == null || ids.isEmpty()) {
            return repo.markAllRead(me, now);
        }
        return repo.markRead(me, ids, now);
    }

    private Long currentUserId() {
        return securityUtils.currentUserId();
    }
}