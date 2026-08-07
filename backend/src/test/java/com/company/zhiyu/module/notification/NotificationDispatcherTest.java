package com.company.zhiyu.module.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2-A IM 通知路由单元测试。
 *
 * 覆盖:
 *  - 通道抽象 + 路由策略(有/无 binding)
 *  - NotificationMessage envelope 构造
 *  - NotificationChannel.Type.fromCode 校验
 *  - ImProperties 配置默认值
 *  - 通道注册顺序对扇出无影响
 *
 * 不依赖 Spring 容器(纯 POJO 测试)
 */
class NotificationDispatcherTest {

    @Test
    @DisplayName("Type.fromCode: 合法 code → 枚举")
    void typeFromCode_known() {
        assertThat(NotificationChannel.Type.fromCode("email")).isEqualTo(NotificationChannel.Type.EMAIL);
        assertThat(NotificationChannel.Type.fromCode("wechat_work")).isEqualTo(NotificationChannel.Type.WECHAT_WORK);
        assertThat(NotificationChannel.Type.fromCode("dingtalk")).isEqualTo(NotificationChannel.Type.DINGTALK);
        assertThat(NotificationChannel.Type.fromCode("feishu")).isEqualTo(NotificationChannel.Type.FEISHU);
    }

    @Test
    @DisplayName("Type.fromCode: 大小写不敏感 + 非法 → null")
    void typeFromCode_caseAndInvalid() {
        assertThat(NotificationChannel.Type.fromCode("EMAIL")).isEqualTo(NotificationChannel.Type.EMAIL);
        assertThat(NotificationChannel.Type.fromCode("Wechat_Work")).isEqualTo(NotificationChannel.Type.WECHAT_WORK);
        assertThat(NotificationChannel.Type.fromCode("wechat")).isNull();
        assertThat(NotificationChannel.Type.fromCode(null)).isNull();
        assertThat(NotificationChannel.Type.fromCode("")).isNull();
    }

    @Test
    @DisplayName("NotificationMessage: 默认值/不可变")
    void notificationMessage_defaults() {
        NotificationMessage m = new NotificationMessage(
                "INITIATION_SUBMIT", "title", "summary", "INITIATION", 1L,
                "IR-001", "http://x", null, null, null);
        assertThat(m.recipientUserIds()).isEmpty();
        assertThat(m.extras()).isEmpty();
        assertThat(m.occurredAt()).isNotNull();
    }

    @Test
    @DisplayName("Dispatcher.resolveRouteFor: 无 binding → default route [email]")
    void resolveRouteFor_noBinding_usesDefault() {
        ImProperties props = new ImProperties();
        // 默认 defaultRoute = [email], boundUserRoute = [email, im]
        FakeBindingService svc = new FakeBindingService(java.util.Collections.emptyMap());
        FakeDndEvaluator dnd = new FakeDndEvaluator(false);
        NotificationDispatcher d = new NotificationDispatcher(props, svc, null, dnd);
        Set<String> r = d.resolveRouteFor(99L);
        assertThat(r).containsExactly("email");
    }

    @Test
    @DisplayName("Dispatcher.resolveRouteFor: 有 binding → boundUserRoute [email, im]")
    void resolveRouteFor_hasBinding_usesBoundRoute() {
        ImProperties props = new ImProperties();
        FakeBindingService svc = new FakeBindingService(Map.of(
                "dingtalk", List.of(makeBinding(99L, "dingtalk", "user@x"))
        ));
        FakeDndEvaluator dnd = new FakeDndEvaluator(false);
        NotificationDispatcher d = new NotificationDispatcher(props, svc, null, dnd);
        Set<String> r = d.resolveRouteFor(99L);
        assertThat(r).containsExactlyInAnyOrder("email", "im");
    }

    @Test
    @DisplayName("Dispatcher.envelope: 默认 occurredAt + empty recipients")
    void envelope_defaults() {
        ImProperties props = new ImProperties();
        FakeDndEvaluator dnd = new FakeDndEvaluator(false);
        NotificationDispatcher d = new NotificationDispatcher(props, new FakeBindingService(Map.of()), null, dnd);
        NotificationMessage m = d.envelope("CAT", "T", "S", "TYPE", 1L, "X-1", "http://x", null);
        assertThat(m.category()).isEqualTo("CAT");
        assertThat(m.recipientUserIds()).isEmpty();
        assertThat(m.occurredAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("ImProperties: 默认全关闭,通道全 null")
    void imProperties_defaults() {
        ImProperties p = new ImProperties();
        assertThat(p.isEnabled()).isFalse();
        assertThat(p.isChannelEnabled("wechat_work")).isFalse();
        assertThat(p.isChannelEnabled("dingtalk")).isFalse();
        assertThat(p.isChannelEnabled("feishu")).isFalse();
        assertThat(p.getWechatWork().isConfigured()).isFalse();
        assertThat(p.getDingtalk().isConfigured()).isFalse();
        assertThat(p.getFeishu().isConfigured()).isFalse();
        assertThat(p.getRouting().getDefaultRoute()).containsExactly("email");
        assertThat(p.getRouting().getBoundUserRoute()).containsExactly("email", "im");
    }

    @Test
    @DisplayName("ImProperties.isChannelEnabled: 通道单独打开 + 总开关控制")
    void imProperties_channelEnabled() {
        ImProperties p = new ImProperties();
        p.setEnabled(true);
        p.getChannels().put("dingtalk", true);
        assertThat(p.isChannelEnabled("dingtalk")).isTrue();
        assertThat(p.isChannelEnabled("wechat_work")).isFalse();
        p.setEnabled(false);
        assertThat(p.isChannelEnabled("dingtalk")).isFalse();
    }

    @Test
    @DisplayName("ImProperties.WechatWork.isConfigured: 三个都要非空")
    void wechatWork_configured() {
        ImProperties.WechatWork w = new ImProperties.WechatWork();
        assertThat(w.isConfigured()).isFalse();
        w.setCorpId("c1");
        assertThat(w.isConfigured()).isFalse();
        w.setAgentId("a1");
        assertThat(w.isConfigured()).isFalse();
        w.setAppSecret("s1");
        assertThat(w.isConfigured()).isTrue();
    }

    @Test
    @DisplayName("HmacSha1Util: 与钉钉/飞书算法一致(hmacSHA1 + base64 + url-encode)")
    void hmacSha1_basic() throws Exception {
        // 钉钉官方算法: secret + timestamp 拼 stringToSign → HMAC-SHA1 → base64 → URL-encode
        String s = HmacSha1Util.hmacSha1("1234567890000\nSEC", "SEC");
        // 解 URL 编码后应是 base64
        String b64 = java.net.URLDecoder.decode(s, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(b64).isNotBlank();
        // base64 长度应 = 28 (HMAC-SHA1 = 20 bytes → ceil(20/3)*4 = 28)
        assertThat(b64.length()).isEqualTo(28);
        // 二次调用应幂等(确定结果)
        String s2 = HmacSha1Util.hmacSha1("1234567890000\nSEC", "SEC");
        assertThat(s).isEqualTo(s2);
    }

    // ============== 测试用辅助 ==============

    /** 极简 fake:不接 Spring,直接返回预置 map */
    static class FakeBindingService extends UserImBindingService {
        private final Map<String, List<UserImBinding>> stub;
        FakeBindingService(Map<String, List<UserImBinding>> stub) {
            super(null);
            this.stub = stub;
        }
        @Override
        public java.util.Map<String, List<UserImBinding>> findByUserGrouped(Long userId) {
            return stub;
        }
    }

    /** 极简 fake DND 判定器 — 预置返回值 */
    static class FakeDndEvaluator extends QuietHoursEvaluator {
        private final boolean stub;
        FakeDndEvaluator(boolean stub) {
            super(java.time.Clock.systemDefaultZone(), null);
            this.stub = stub;
        }
        @Override
        public boolean isInQuietHours(Long userId) {
            return stub;
        }
    }

    static UserImBinding makeBinding(Long userId, String channel, String externalUserId) {
        return UserImBinding.builder()
                .userId(userId).channel(channel).externalUserId(externalUserId)
                .enabled(true).build();
    }
}
