package com.hex.projectgovern.module.alert;

import com.hex.projectgovern.module.notification.NotificationDispatcher;
import com.hex.projectgovern.module.notification.NotificationMessage;
import com.hex.projectgovern.module.org.AppUser;
import com.hex.projectgovern.module.org.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * F4: 告警事件通知分发 (V5.1+ / WP-M4-03 / 通知分发)
 *
 * <p>职责:
 * <ol>
 *   <li>接收 {@link AlertEvent} + 关联 rule</li>
 *   <li>解析收件人列表 (规则 notify_emails + 项目相关用户)</li>
 *   <li>构造 {@link NotificationMessage} (category=COST_DIFF 等)</li>
 *   <li>调 {@link NotificationDispatcher#dispatch} 扇出到通道</li>
 *   <li>更新 {@link AlertEvent#notifyStatus}:PENDING → SENT/FAILED</li>
 * </ol>
 *
 * <p>失败隔离:
 * <ul>
 *   <li>解析收件人失败 → 至少回落到 rule.notify_emails 解析</li>
 *   <li>通道发送失败 → warn log,不抛回 (NotificationChannel.send 已吞异常)</li>
 *   <li>状态更新失败 → 仅 log,不重试</li>
 * </ul>
 *
 * @since V5.1 / WP-M4-03 / T-08
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertNotifier {

    private final NotificationDispatcher dispatcher;
    private final UserRepository userRepository;
    private final AlertEventRepository eventRepo;

    /**
     * 主入口:分发一个告警事件
     * @param event  已创建的 alert_event (含 project_id / message / target)
     * @param rule   关联的 alert_rule (含 notify_emails)
     * @return       实际下发的通道数
     */
    @Transactional
    public int dispatch(AlertEvent event, AlertRule rule) {
        if (event == null || rule == null) return 0;
        if (!"PENDING".equals(event.getNotifyStatus())) {
            log.debug("[AlertNotifier] event {} notify_status={}, skip (already processed)",
                    event.getId(), event.getNotifyStatus());
            return 0;
        }

        try {
            // 1) 收件人解析
            List<Long> recipients = resolveRecipients(event, rule);
            if (recipients.isEmpty()) {
                log.warn("[AlertNotifier] event {} no recipients (rule.notify_emails={}, project_id={}), mark FAILED",
                        event.getId(), rule.getNotifyEmails(), event.getProjectId());
                markFailed(event, "no_recipients");
                return 0;
            }

            // 2) 构造 NotificationMessage
            NotificationMessage msg = buildMessage(event, rule, recipients);

            // 3) 分发
            int ok = dispatcher.dispatch(msg);

            // 4) 状态更新
            OffsetDateTime now = OffsetDateTime.now();
            if (ok > 0) {
                event.setNotifyStatus("SENT");
                event.setNotifySentAt(now);
                log.info("[AlertNotifier] event {} dispatched to {} channel(s), recipients={}",
                        event.getId(), ok, recipients.size());
            } else {
                event.setNotifyStatus("FAILED");
                log.warn("[AlertNotifier] event {} dispatched but 0 channel succeeded", event.getId());
            }
            eventRepo.save(event);
            return ok;

        } catch (Exception e) {
            log.warn("[AlertNotifier] event {} dispatch error: {}", event.getId(), e.getMessage(), e);
            markFailed(event, "exception:" + e.getClass().getSimpleName());
            return 0;
        }
    }

    // ============================================================
    // 收件人解析
    // ============================================================

    /**
     * 收件人解析策略(优先级 ↓):
     *  1. rule.notify_emails 解析 → 查 user 表拿 userId
     *  2. event.project_id 不为空 → 加 project 所属 PMO_ADMIN 列表(全员)
     *  3. 兜底:全公司 PMO_ADMIN + FINANCE 角色
     */
    List<Long> resolveRecipients(AlertEvent event, AlertRule rule) {
        List<Long> result = new ArrayList<>();

        // 1) rule.notify_emails 解析
        if (rule.getNotifyEmails() != null && !rule.getNotifyEmails().isBlank()) {
            String[] emails = rule.getNotifyEmails().split(",");
            for (String e : emails) {
                String trimmed = e.trim();
                if (trimmed.isEmpty()) continue;
                userRepository.findAll().stream()
                    .filter(u -> !u.isDeleted())
                    .filter(u -> trimmed.equalsIgnoreCase(u.getEmail()))
                    .findFirst()
                    .ifPresent(u -> {
                        if (!result.contains(u.getId())) result.add(u.getId());
                    });
            }
        }

        // 2) 如果规则要求项目级收件人(这里 COST_DIFF 默认 false — 规则级已足够)
        // 如未来需要按 project 路由 PMO,可在此扩展

        // 3) 兜底:全公司 PMO_ADMIN + ADMIN (如果 1) 没找到任何人)
        if (result.isEmpty()) {
            log.debug("[AlertNotifier] rule.notify_emails 没匹配到 user, 兜底用 PMO_ADMIN + ADMIN");
            for (AppUser u : userRepository.findAllByPrimaryRoleCodeInAndEnabledAndDeletedFalse(
                    List.of("PMO_ADMIN", "ADMIN"), true)) {
                if (!result.contains(u.getId())) result.add(u.getId());
            }
        }

        return result;
    }

    // ============================================================
    // 消息构造
    // ============================================================

    NotificationMessage buildMessage(AlertEvent event, AlertRule rule, List<Long> recipients) {
        String category = mapCategory(rule.getTypeCode());

        // extras 给前端/通道扩展用
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("ruleCode", rule.getCode());
        extras.put("ruleName", rule.getName());
        extras.put("severity", rule.getSeverity());
        extras.put("actualValue", event.getActualValue());
        extras.put("thresholdValue", event.getThresholdValue());
        if (event.getProjectId() != null) extras.put("projectId", event.getProjectId());

        String linkUrl = event.getProjectId() != null
                ? "/projects/" + event.getProjectId()
                : null;

        return new NotificationMessage(
                category,
                "[预警] " + rule.getName(),
                event.getMessage(),
                "ALERT_EVENT",
                event.getId(),
                rule.getCode(),
                linkUrl,
                recipients,
                extras,
                Instant.now()
        );
    }

    /** 规则类型 → 通知 category (供前端/IM 路由分类) */
    static String mapCategory(String typeCode) {
        if (typeCode == null) return "ALERT_GENERIC";
        return switch (typeCode) {
            case "COST_DIFF" -> "ALERT_COST_DIFF";
            case "BUDGET_EXCEED" -> "ALERT_BUDGET";
            case "HOURS_OVER" -> "ALERT_HOURS";
            case "CONTRACT_BALANCE" -> "ALERT_CONTRACT";
            case "PROJECT_STALE" -> "ALERT_STALE";
            case "PAYMENT_OVERDUE" -> "ALERT_PAYMENT";
            case "ROLE_DEFAULT" -> "ALERT_ROLE_DEFAULT";
            default -> "ALERT_GENERIC";
        };
    }

    // ============================================================
    // 状态管理
    // ============================================================

    private void markFailed(AlertEvent event, String reason) {
        event.setNotifyStatus("FAILED");
        eventRepo.save(event);
        log.warn("[AlertNotifier] event {} marked FAILED, reason={}", event.getId(), reason);
    }

    /** 收件人邮箱列表(测试用) */
    static List<String> parseEmails(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
