package com.hex.projectgovern.module.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 通知中心统一持久化入口(P2-C / P2-A §10 限制 #4 修复)。
 *
 * 设计:
 *  - 任何"事件触发后写一条 UNREAD"的操作都走这里,与通道(邮件/IM/SSE)解耦
 *  - REQUIRES_NEW: 通知入库与主业务事务隔离,主事务回滚不影响通知
 *                  (通知要尽量送达,即使业务事务失败也要留下"已尝试"的痕迹)
 *  - 失败兜底: 异常只打 warn log,业务事件继续(参考 NotificationDispatcherListener 风格)
 *
 * 兼容性:
 *  - V1: MailService.onXxx 中内联调用 writeUnread()
 *  - V2: 业务事件 → NotificationDispatcherListener → writePerRecipient(...) (去重 IM 场景)
 *  - 同一事件不会重复写入(NotificationDispatcherListener 是唯一入口)
 *
 * 调用顺序(重要):
 *  1. 写 UNREAD (本服务)
 *  2. 发邮件  (MailService.send via NotificationDispatcher / 兼容旧路径)
 *  3. 推 IM   (NotificationDispatcher.dispatch)
 *  4. 推 SSE  (SseEmitterRegistry.sendToUser)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationWriteService {

    private final NotificationRepository repo;

    /**
     * 单条写入(对应当前事件对当前收件人产生 1 条 UNREAD)。
     * @return 写入后的 entity(用于测试断言),失败返回 null
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification writeOne(Long recipientId, String category, Long resourceId,
                                  String resourceCode, String title, String content) {
        if (recipientId == null) {
            log.debug("[Notif] skip write: recipientId is null, category={}, resource={}",
                    category, resourceCode);
            return null;
        }
        try {
            Notification n = Notification.builder()
                    .recipientId(recipientId)
                    .category(category)
                    .resourceId(resourceId)
                    .resourceCode(resourceCode)
                    .title(title)
                    .content(content)
                    .status(Notification.NotificationStatus.UNREAD)
                    .build();
            return repo.save(n);
        } catch (Exception ex) {
            log.warn("[Notif] write failed (non-fatal): recipientId={}, category={}, err={}",
                    recipientId, category, ex.getMessage());
            return null;
        }
    }

    /**
     * 批量写入(同事件,多收件人:如 onDecided 写给申请人 + 下一审批人)。
     * @return 实际写入的条数
     */
    public int writeAll(List<OneWrite> writes) {
        if (writes == null || writes.isEmpty()) return 0;
        int n = 0;
        for (OneWrite w : writes) {
            Notification saved = writeOne(w.recipientId, w.category, w.resourceId,
                    w.resourceCode, w.title, w.content);
            if (saved != null) n++;
        }
        return n;
    }

    /** 单条写入参数 record(让 batch 调用更清晰) */
    public record OneWrite(
            Long recipientId,
            String category,
            Long resourceId,
            String resourceCode,
            String title,
            String content
    ) {}
}
