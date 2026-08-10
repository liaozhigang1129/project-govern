package com.hex.projectgovern.module.alert;

import com.hex.projectgovern.module.notification.NotificationDispatcher;
import com.hex.projectgovern.module.notification.NotificationMessage;
import com.hex.projectgovern.module.org.AppUser;
import com.hex.projectgovern.module.org.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AlertNotifier 单测 (V5.1 / WP-M4-03 / T-08)
 *
 * 覆盖:
 *  - 收件人解析: rule.notify_emails 命中 → userId 返回
 *  - 收件人兜底: emails 没匹配 → PMO_ADMIN/ADMIN 兜底
 *  - 消息构造: category / linkUrl / extras 字段
 *  - 状态更新: SENT (ok>0) / FAILED (ok=0 或 no recipients)
 *  - 失败隔离: dispatcher 抛错 → mark FAILED,不抛回
 *  - 幂等: notifyStatus != PENDING → skip
 */
class AlertNotifierTest {

    private NotificationDispatcher dispatcher;
    private UserRepository userRepository;
    private AlertEventRepository eventRepo;
    private AlertNotifier notifier;

    @BeforeEach
    void setUp() {
        dispatcher = mock(NotificationDispatcher.class);
        userRepository = mock(UserRepository.class);
        eventRepo = mock(AlertEventRepository.class);
        notifier = new AlertNotifier(dispatcher, userRepository, eventRepo);
    }

    private AlertRule rule() {
        AlertRule r = new AlertRule();
        r.setCode("RULE_COST_DIFF_100");
        r.setName("成本对账差异 ≥ ¥100 警告");
        r.setTypeCode("COST_DIFF");
        r.setSeverity("HIGH");
        r.setThreshold(new BigDecimal("100.00"));
        r.setNotifyEmails("pmo@company.com,finance@company.com");
        return withId(r, 7L);
    }

    private AlertEvent event() {
        AlertEvent e = new AlertEvent();
        e.setRuleId(7L);
        e.setSeverity("HIGH");
        e.setMessage("项目 #100 财务-成本对账发现 5 个对账桶异常");
        e.setTargetType("PROJECT");
        e.setTargetId(100L);
        e.setProjectId(100L);
        e.setActualValue(new BigDecimal("5"));
        e.setThresholdValue(new BigDecimal("100"));
        e.setStatus("NEW");
        e.setNotifyStatus("PENDING");
        return withId(e, 999L);
    }

    private AppUser user(Long id, String email, String role, boolean enabled) {
        AppUser u = new AppUser();
        try {
            Field f1 = AppUser.class.getDeclaredField("id");
            f1.setAccessible(true);
            f1.set(u, id);
            Field f2 = AppUser.class.getDeclaredField("email");
            f2.setAccessible(true);
            f2.set(u, email);
            Field f3 = AppUser.class.getDeclaredField("primaryRole");
            f3.setAccessible(true);
            com.hex.projectgovern.module.org.Role r = new com.hex.projectgovern.module.org.Role();
            Field rc = com.hex.projectgovern.module.org.Role.class.getDeclaredField("code");
            rc.setAccessible(true);
            rc.set(r, role);
            f3.set(u, r);
            Field f4 = AppUser.class.getDeclaredField("enabled");
            f4.setAccessible(true);
            f4.set(u, enabled);
            // deleted 在父类 SoftDeletableEntity
            Field f5 = AppUser.class.getSuperclass().getDeclaredField("deleted");
            f5.setAccessible(true);
            f5.set(u, false);
        } catch (Exception e) { throw new RuntimeException(e); }
        return u;
    }

    private static <T> T withId(T obj, Long id) {
        try {
            Field f = obj.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(obj, id);
        } catch (Exception e) { throw new RuntimeException(e); }
        return obj;
    }

    // ============================================================
    // 主流程
    // ============================================================

    @Test
    @DisplayName("happy path: 2 收件人 + 1 通道成功 → SENT")
    void happyPath_sent() {
        AlertRule r = rule();
        AlertEvent e = event();
        AppUser pmo = user(1L, "pmo@company.com", "PMO_ADMIN", true);
        AppUser fin = user(2L, "finance@company.com", "FINANCE", true);
        when(userRepository.findAll()).thenReturn(List.of(pmo, fin));
        when(dispatcher.dispatch(any())).thenReturn(1); // 1 个通道成功

        int ok = notifier.dispatch(e, r);

        assertThat(ok).isEqualTo(1);
        ArgumentCaptor<NotificationMessage> cap = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(dispatcher).dispatch(cap.capture());
        assertThat(cap.getValue().recipientUserIds()).containsExactly(1L, 2L);
        assertThat(cap.getValue().category()).isEqualTo("ALERT_COST_DIFF");
        assertThat(cap.getValue().linkUrl()).isEqualTo("/projects/100");

        assertThat(e.getNotifyStatus()).isEqualTo("SENT");
        assertThat(e.getNotifySentAt()).isNotNull();
    }

    @Test
    @DisplayName("无收件人 → FAILED, 0 通道")
    void noRecipients_failed() {
        AlertRule r = rule();
        AlertEvent e = event();
        // user 表空 → emails 解析不出
        when(userRepository.findAll()).thenReturn(List.of());
        // 兜底也用 PMO_ADMIN/ADMIN 查
        when(userRepository.findAllByPrimaryRoleCodeInAndEnabledAndDeletedFalse(
                any(), eq(true))).thenReturn(List.of());

        int ok = notifier.dispatch(e, r);

        assertThat(ok).isZero();
        verify(dispatcher, never()).dispatch(any());
        assertThat(e.getNotifyStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("dispatcher 抛错 → 失败隔离, mark FAILED, 不抛回")
    void dispatcherException_isolation() {
        AlertRule r = rule();
        AlertEvent e = event();
        AppUser pmo = user(1L, "pmo@company.com", "PMO_ADMIN", true);
        when(userRepository.findAll()).thenReturn(List.of(pmo));
        when(dispatcher.dispatch(any())).thenThrow(new RuntimeException("channel error"));

        // 不应抛
        int ok = notifier.dispatch(e, r);

        assertThat(ok).isZero();
        assertThat(e.getNotifyStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("dispatcher 返回 0 (所有通道失败) → FAILED")
    void dispatcherZero_failed() {
        AlertRule r = rule();
        AlertEvent e = event();
        AppUser pmo = user(1L, "pmo@company.com", "PMO_ADMIN", true);
        when(userRepository.findAll()).thenReturn(List.of(pmo));
        when(dispatcher.dispatch(any())).thenReturn(0);

        notifier.dispatch(e, r);

        assertThat(e.getNotifyStatus()).isEqualTo("FAILED");
        assertThat(e.getNotifySentAt()).isNull();
    }

    // ============================================================
    // 收件人解析
    // ============================================================

    @Test
    @DisplayName("收件人兜底: emails 没匹配 → PMO_ADMIN + ADMIN 兜底")
    void recipientsFallback() {
        AlertRule r = rule();
        AlertEvent e = event();
        AppUser u1 = user(10L, "admin@x.com", "ADMIN", true);
        AppUser u2 = user(11L, "pmo@x.com", "PMO_ADMIN", true);
        // 改 rule.notify_emails 为没有匹配项,触发 fallback
        r.setNotifyEmails("notexist@x.com");
        when(userRepository.findAll()).thenReturn(List.of(u1, u2));
        when(userRepository.findAllByPrimaryRoleCodeInAndEnabledAndDeletedFalse(
                any(), eq(true))).thenReturn(List.of(u1, u2));
        when(dispatcher.dispatch(any())).thenReturn(1);

        notifier.dispatch(e, r);

        ArgumentCaptor<NotificationMessage> cap = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(dispatcher).dispatch(cap.capture());
        assertThat(cap.getValue().recipientUserIds()).containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    @DisplayName("重复收件人去重")
    void recipientsDedup() {
        AlertRule r = rule();
        AlertEvent e = event();
        // 一个邮箱出现 2 次,但只一个 user
        r.setNotifyEmails("pmo@company.com,pmo@company.com");
        AppUser pmo = user(1L, "pmo@company.com", "PMO_ADMIN", true);
        when(userRepository.findAll()).thenReturn(List.of(pmo));
        when(dispatcher.dispatch(any())).thenReturn(1);

        notifier.dispatch(e, r);

        ArgumentCaptor<NotificationMessage> cap = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(dispatcher).dispatch(cap.capture());
        assertThat(cap.getValue().recipientUserIds()).containsExactly(1L); // 去重
    }

    // ============================================================
    // 幂等
    // ============================================================

    @Test
    @DisplayName("幂等: notifyStatus=SKIPPED → 跳过,不调 dispatcher")
    void idempotency_skipped() {
        AlertRule r = rule();
        AlertEvent e = event();
        e.setNotifyStatus("SKIPPED");

        int ok = notifier.dispatch(e, r);

        assertThat(ok).isZero();
        verify(dispatcher, never()).dispatch(any());
    }

    // ============================================================
    // 工具
    // ============================================================

    @Test
    @DisplayName("mapCategory: COST_DIFF → ALERT_COST_DIFF")
    void categoryMapping() {
        assertThat(AlertNotifier.mapCategory("COST_DIFF")).isEqualTo("ALERT_COST_DIFF");
        assertThat(AlertNotifier.mapCategory("BUDGET_EXCEED")).isEqualTo("ALERT_BUDGET");
        assertThat(AlertNotifier.mapCategory("PAYMENT_OVERDUE")).isEqualTo("ALERT_PAYMENT");
        assertThat(AlertNotifier.mapCategory(null)).isEqualTo("ALERT_GENERIC");
        assertThat(AlertNotifier.mapCategory("UNKNOWN_TYPE")).isEqualTo("ALERT_GENERIC");
    }

    @Test
    @DisplayName("parseEmails: csv 解析 + 去空")
    void parseEmailsTest() {
        assertThat(AlertNotifier.parseEmails("a@x.com,b@x.com")).containsExactly("a@x.com", "b@x.com");
        assertThat(AlertNotifier.parseEmails(" a@x.com , , b@x.com ")).containsExactly("a@x.com", "b@x.com");
        assertThat(AlertNotifier.parseEmails(null)).isEmpty();
        assertThat(AlertNotifier.parseEmails("")).isEmpty();
    }

    @Test
    @DisplayName("buildMessage: extras 含 ruleCode/severity/projectId + linkUrl 拼接")
    void buildMessage() {
        AlertRule r = rule();
        AlertEvent e = event();

        var msg = notifier.buildMessage(e, r, List.of(1L));

        assertThat(msg.category()).isEqualTo("ALERT_COST_DIFF");
        assertThat(msg.title()).contains("[预警]").contains("成本对账");
        assertThat(msg.summary()).contains("项目 #100");
        assertThat(msg.linkUrl()).isEqualTo("/projects/100");
        assertThat(msg.resourceType()).isEqualTo("ALERT_EVENT");
        assertThat(msg.resourceId()).isEqualTo(999L);
        assertThat(msg.resourceCode()).isEqualTo("RULE_COST_DIFF_100");
        assertThat(msg.extras()).containsEntry("ruleCode", "RULE_COST_DIFF_100");
        assertThat(msg.extras()).containsEntry("severity", "HIGH");
        assertThat(msg.extras()).containsEntry("projectId", 100L);
    }

    @Test
    @DisplayName("buildMessage: projectId=null → linkUrl=null")
    void buildMessage_noProject() {
        AlertRule r = rule();
        AlertEvent e = event();
        e.setProjectId(null);
        e.setTargetId(null);

        var msg = notifier.buildMessage(e, r, List.of(1L));

        assertThat(msg.linkUrl()).isNull();
        assertThat(msg.extras()).doesNotContainKey("projectId");
    }
}
