package com.company.pmo.module.notification;

import com.company.pmo.common.api.ApiResponse;
import com.company.pmo.common.security.RequireRoles;
import com.company.pmo.module.notification.dto.NotificationDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通知中心 API(P1.5 收尾)
 *  - /unread-count         铃铛右上角红点
 *  - /page?status=UNREAD   下拉分页
 *  - /read  批量标已读(单条 / 全量)
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @GetMapping("/unread-count")
    @RequireRoles.Read
    public ApiResponse<NotificationDtos.UnreadCount> unreadCount() {
        return ApiResponse.ok(service.unreadCount());
    }

    @GetMapping("/page")
    @RequireRoles.Read
    public ApiResponse<NotificationDtos.PageResponse<NotificationDtos.View>> page(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.page(page, size, status));
    }

    /** 标记已读: ids 非空 = 标单/多;ids 为空 = 全部已读 */
    @PostMapping("/read")
    @RequireRoles.Read
    public ApiResponse<Integer> markRead(@RequestBody(required = false) NotificationDtos.MarkReq body) {
        List<Long> ids = body == null ? List.of() : body.ids();
        return ApiResponse.ok(service.markRead(ids));
    }
}