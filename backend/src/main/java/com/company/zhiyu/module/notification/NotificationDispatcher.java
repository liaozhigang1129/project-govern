package com.company.zhiyu.module.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 通知路由器(P2-A)。
 *
 * 设计:
 *  - 业务事件只调 dispatch(message),不关心具体通道
 *  - 路由策略:每个收件人独立决定走哪些通道
 *      - 有 IM binding → 按 ImProperties.routing.boundUserRoute 走(默认 [email, im])
 *      - 无 IM binding → 按 ImProperties.routing.defaultRoute 走(默认 [email])
 *  - IM 通道具体推送:由 UserImBinding.channel 决定走 WechatWork/DingTalk/Feishu 哪个
 *  - 邮件通道:沿用旧 MailService 行为(写邮件 + UNREAD)
 *  - 失败隔离:任一通道失败不影响其他通道
 *
 * 兼容:
 *  - MailService 仍直接由 NotificationListener 调(不破坏现有事件链路)
 *  - 新事件或 V2 切换:NotificationListener 改调 dispatcher.dispatch()
 *  - P2 #2: 用户的 DND 窗口期内,IM 通道跳过(邮件仍走)— 防止深夜被吵醒
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcher {

    private final ImProperties imProps;
    private final UserImBindingService bindingService;
    /** 用 ObjectProvider 避免 ImChannel 在 IM 关闭时不存在的强依赖 */
    private final ObjectProvider<List<NotificationChannel>> channelsProvider;
    /** DND 勿扰时段判定(P2 #2) */
    private final QuietHoursEvaluator dndEvaluator;

    /**
     * 主入口:把消息扇出到所有通道。
     * @return 成功通道数(只用于测试/log;不向上抛)
     */
    public int dispatch(NotificationMessage msg) {
        if (msg == null || msg.recipientUserIds() == null || msg.recipientUserIds().isEmpty()) {
            log.debug("[Dispatch] skip: no recipients, category={}", msg == null ? "?" : msg.category());
            return 0;
        }

        List<NotificationChannel> channels = channelsProvider.getIfAvailable(List::of);
        if (channels.isEmpty()) {
            log.debug("[Dispatch] no channels in context, category={}", msg.category());
            return 0;
        }

        int ok = 0;
        for (Long userId : msg.recipientUserIds()) {
            Set<String> routes = resolveRouteFor(userId);
            log.debug("[Dispatch] userId={} route={} category={}", userId, routes, msg.category());
            for (String r : routes) {
                if ("email".equalsIgnoreCase(r)) {
                    // email 通道由 MailService 现有事件处理(暂不重复发);V2 切到 dispatcher
                    continue;
                }
                if ("im".equalsIgnoreCase(r)) {
                    // P2 #2: 用户在勿扰时段 → 跳过 IM 推送(邮件仍由 NotificationListener 走)
                    if (dndEvaluator.isInQuietHours(userId)) {
                        log.info("[Dispatch] userId={} in DND, skip IM category={}",
                                userId, msg.category());
                        continue;
                    }
                    ok += sendToIm(msg, userId, channels) ? 1 : 0;
                }
                // 未来扩展:sms / phone
            }
        }
        return ok;
    }

    /**
     * 解析单个收件人的路由集合。
     *  - 有 binding 的用户:boundUserRoute(默认 [email, im])
     *  - 无 binding:defaultRoute(默认 [email])
     */
    Set<String> resolveRouteFor(Long userId) {
        boolean hasBinding = !bindingService.findByUserGrouped(userId).isEmpty();
        List<String> r = hasBinding
                ? imProps.getRouting().getBoundUserRoute()
                : imProps.getRouting().getDefaultRoute();
        return new HashSet<>(r);
    }

    /**
     * 推送到该用户绑定的所有 IM 通道。
     * 返回是否任一通道成功。
     */
    private boolean sendToIm(NotificationMessage msg, Long userId, List<NotificationChannel> channels) {
        Map<String, List<UserImBinding>> grouped = bindingService.findByUserGrouped(userId);
        if (grouped.isEmpty()) {
            log.debug("[Dispatch] userId={} no IM binding, skip im route", userId);
            return false;
        }
        // 把当前用户的 binding 转换为 (channel, externalUserId) → 找到对应 NotificationChannel → 发
        List<NotificationChannel> imChannels = channels.stream()
                .filter(c -> c.type() != NotificationChannel.Type.EMAIL)
                .collect(Collectors.toList());

        // 构造"单用户版"消息(只发给这个 user,避免一呼百应)
        NotificationMessage perUser = new NotificationMessage(
                msg.category(), msg.title(), msg.summary(), msg.resourceType(),
                msg.resourceId(), msg.resourceCode(), msg.linkUrl(),
                List.of(userId), msg.extras(), msg.occurredAt()
        );

        boolean anyOk = false;
        for (Map.Entry<String, List<UserImBinding>> e : grouped.entrySet()) {
            String chCode = e.getKey();
            NotificationChannel ch = imChannels.stream()
                    .filter(c -> c.type().code().equalsIgnoreCase(chCode))
                    .findFirst()
                    .orElse(null);
            if (ch == null) {
                log.debug("[Dispatch] userId={} channel={} not registered", userId, chCode);
                continue;
            }
            if (!ch.isEnabled()) {
                log.debug("[Dispatch] userId={} channel={} disabled", userId, chCode);
                continue;
            }
            try {
                if (ch.send(perUser)) anyOk = true;
            } catch (Exception ex) {
                log.warn("[Dispatch] userId={} channel={} threw: {}", userId, chCode, ex.getMessage());
            }
        }
        return anyOk;
    }

    /** 把"事件"装成 NotificationMessage 的便利方法(便于 V2 切换) */
    public NotificationMessage envelope(String category, String title, String summary,
                                        String resourceType, Long resourceId, String resourceCode,
                                        String linkUrl, List<Long> recipients) {
        return new NotificationMessage(category, title, summary, resourceType,
                resourceId, resourceCode, linkUrl,
                recipients == null ? new ArrayList<>() : recipients,
                Map.of(), java.time.Instant.now());
    }
}
