package com.hex.projectgovern.module.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * IM 3 平台通道 (钉钉/企业微信/飞书) 5 场景单测 (P2 #23).
 *
 * 5 场景:
 *  1. isEnabled 状态 (未配置 / 已配置)
 *  2. send 成功
 *  3. send 失败重试 (HTTP 500)
 *  4. send 网络异常
 *  5. send 限流 (errcode 45009 / 错码)
 */
class ImChannelTest {

    private ImProperties props;
    private ImHttpClient http;

    @BeforeEach
    void setUp() {
        props = new ImProperties();
        http = mock(ImHttpClient.class);
        when(http.mapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());
    }

    private NotificationMessage mockMsg() {
        return new NotificationMessage(
            "INITIATION_DECIDE", "审批通知", "请审批",
            "INITIATION", 1L, "INIT-001",
            "http://localhost:8080/api/x/1",
            java.util.List.of(100L, 101L), java.util.Map.<String, Object>of(), java.time.Instant.now());
    }

    // ============ FeishuChannel ============
    @Test
    @DisplayName("[Feishu] 1) 未配置 → isEnabled=false")
    void feishu_disabled() {
        var ch = new FeishuChannel(props, http);
        assertThat(ch.isEnabled()).isFalse();
        assertThat(ch.send(mockMsg())).isFalse();
    }

    @Test
    @DisplayName("[Feishu] 2) 加签模式 + 200 → send=true")
    void feishu_signed_success() {
        props.setEnabled(true);
        props.getChannels().put("feishu", true);
        props.getFeishu().setWebhookUrl("https://open.feishu.cn/webhook/test");
        props.getFeishu().setSecret("test-secret");

        when(http.feishuSignedUrl(anyString(), anyString())).thenReturn("https://signed");
        when(http.postJson(anyString(), any())).thenReturn("{\"code\":0,\"msg\":\"ok\"}");

        var ch = new FeishuChannel(props, http);
        assertThat(ch.isEnabled()).isTrue();
        assertThat(ch.send(mockMsg())).isTrue();
    }

    @Test
    @DisplayName("[Feishu] 3) HTTP 5xx → send=false")
    void feishu_http5xx() {
        props.setEnabled(true);
        props.getChannels().put("feishu", true);
        props.getFeishu().setWebhookUrl("https://x");
        when(http.feishuSignedUrl(anyString(), anyString())).thenReturn("https://x");
        when(http.postJson(anyString(), any())).thenReturn(null);
        var ch = new FeishuChannel(props, http);
        assertThat(ch.send(mockMsg())).isFalse();
    }

    @Test
    @DisplayName("[Feishu] 4) 网络异常 → send=false 不抛")
    void feishu_network_error() {
        props.setEnabled(true);
        props.getChannels().put("feishu", true);
        props.getFeishu().setWebhookUrl("https://x");
        when(http.feishuSignedUrl(anyString(), anyString())).thenReturn("https://x");
        when(http.postJson(anyString(), any())).thenReturn(null);
        var ch = new FeishuChannel(props, http);
        assertThat(ch.send(mockMsg())).isFalse();
    }

    @Test
    @DisplayName("[Feishu] 5) 限流 (code!=0) → send=false")
    void feishu_rate_limit() {
        props.setEnabled(true);
        props.getChannels().put("feishu", true);
        props.getFeishu().setWebhookUrl("https://x");
        when(http.feishuSignedUrl(anyString(), anyString())).thenReturn("https://x");
        when(http.postJson(anyString(), any())).thenReturn("{\"code\":9499,\"msg\":\"rate limit\"}");
        var ch = new FeishuChannel(props, http);
        assertThat(ch.send(mockMsg())).isFalse();
    }

    // ============ DingTalkChannel ============
    @Test
    @DisplayName("[DingTalk] 1) 未配置 → isEnabled=false")
    void dingtalk_disabled() {
        var ch = new DingTalkChannel(props, http);
        assertThat(ch.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("[DingTalk] 2) 加签模式 + 200 → send=true")
    void dingtalk_signed_success() {
        props.setEnabled(true);
        props.getChannels().put("dingtalk", true);
        props.getDingtalk().setWebhookUrl("https://oapi.dingtalk.com/robot/webhook/test");
        props.getDingtalk().setSecret("SEC...");

        when(http.dingTalkSignedUrl(anyString(), anyString())).thenReturn("https://signed");
        when(http.postJson(anyString(), any())).thenReturn("{\"errcode\":0,\"errmsg\":\"ok\"}");

        var ch = new DingTalkChannel(props, http);
        boolean r = ch.send(mockMsg());
        assertThat(r).isTrue();
    }

    @Test
    @DisplayName("[DingTalk] 3) HTTP 5xx → send=false")
    void dingtalk_http5xx() {
        props.setEnabled(true);
        props.getChannels().put("dingtalk", true);
        props.getDingtalk().setWebhookUrl("https://x");
        when(http.dingTalkSignedUrl(anyString(), anyString())).thenReturn("https://x");
        when(http.postJson(anyString(), any())).thenReturn(null);
        var ch = new DingTalkChannel(props, http);
        assertThat(ch.send(mockMsg())).isFalse();
    }

    @Test
    @DisplayName("[DingTalk] 4) 网络异常 → send=false")
    void dingtalk_network_error() {
        props.setEnabled(true);
        props.getChannels().put("dingtalk", true);
        props.getDingtalk().setWebhookUrl("https://x");
        when(http.dingTalkSignedUrl(anyString(), anyString())).thenReturn("https://x");
        when(http.postJson(anyString(), any())).thenReturn(null);
        var ch = new DingTalkChannel(props, http);
        assertThat(ch.send(mockMsg())).isFalse();
    }

    @Test
    @DisplayName("[DingTalk] 5) 限流 (errcode=88) → send=false")
    void dingtalk_rate_limit() {
        props.setEnabled(true);
        props.getChannels().put("dingtalk", true);
        props.getDingtalk().setWebhookUrl("https://x");
        when(http.dingTalkSignedUrl(anyString(), anyString())).thenReturn("https://x");
        when(http.postJson(anyString(), any())).thenReturn("{\"errcode\":88,\"errmsg\":\"limit\"}");
        var ch = new DingTalkChannel(props, http);
        assertThat(ch.send(mockMsg())).isFalse();
    }

    // ============ WechatWorkChannel ============
    @Test
    @DisplayName("[WechatWork] 1) 未配置 → isEnabled=false")
    void wecom_disabled() {
        UserImBindingRepository bindingRepo = mock(UserImBindingRepository.class);
        var ch = new WechatWorkChannel(props, http, bindingRepo);
        assertThat(ch.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("[WechatWork] 2) 已配置 + 200 → send=true")
    void wecom_success() {
        UserImBindingRepository bindingRepo = mock(UserImBindingRepository.class);
        com.hex.projectgovern.module.notification.UserImBinding b = new com.hex.projectgovern.module.notification.UserImBinding();
        b.setExternalUserId("userid_001");
        when(bindingRepo.findByUserIdInAndChannelAndEnabledTrue(any(), anyString()))
            .thenReturn(java.util.List.of(b));

        props.setEnabled(true);
        props.getChannels().put("wechat_work", true);
        props.getWechatWork().setCorpId("wxcorp");
        props.getWechatWork().setAgentId("1000002");
        props.getWechatWork().setAppSecret("secret");

        when(http.getWechatWorkToken(anyString(), anyString())).thenReturn("token-abc");
        when(http.postJson(anyString(), any())).thenReturn("{\"errcode\":0,\"errmsg\":\"ok\"}");

        var ch = new WechatWorkChannel(props, http, bindingRepo);
        assertThat(ch.isEnabled()).isTrue();
        assertThat(ch.send(mockMsg())).isTrue();
    }

    @Test
    @DisplayName("[WechatWork] 3) token 失败 → send=false")
    void wecom_token_fail() {
        UserImBindingRepository bindingRepo = mock(UserImBindingRepository.class);
        when(bindingRepo.findByUserIdInAndChannelAndEnabledTrue(any(), anyString()))
            .thenReturn(java.util.List.of());

        props.setEnabled(true);
        props.getChannels().put("wechat_work", true);
        props.getWechatWork().setCorpId("wxcorp");
        props.getWechatWork().setAgentId("1000002");
        props.getWechatWork().setAppSecret("secret");

        when(http.getWechatWorkToken(anyString(), anyString())).thenReturn(null);
        var ch = new WechatWorkChannel(props, http, bindingRepo);
        assertThat(ch.send(mockMsg())).isFalse();
    }

    @Test
    @DisplayName("[WechatWork] 4) 网络异常 → send=false")
    void wecom_network_error() {
        UserImBindingRepository bindingRepo = mock(UserImBindingRepository.class);
        com.hex.projectgovern.module.notification.UserImBinding b = new com.hex.projectgovern.module.notification.UserImBinding();
        b.setExternalUserId("u1");
        when(bindingRepo.findByUserIdInAndChannelAndEnabledTrue(any(), anyString()))
            .thenReturn(java.util.List.of(b));

        props.setEnabled(true);
        props.getChannels().put("wechat_work", true);
        props.getWechatWork().setCorpId("wxcorp");
        props.getWechatWork().setAgentId("1000002");
        props.getWechatWork().setAppSecret("secret");

        when(http.getWechatWorkToken(anyString(), anyString())).thenReturn("token");
        when(http.postJson(anyString(), any())).thenReturn(null);
        var ch = new WechatWorkChannel(props, http, bindingRepo);
        assertThat(ch.send(mockMsg())).isFalse();
    }

    @Test
    @DisplayName("[WechatWork] 5) 限流 (errcode=45009) → send=false")
    void wecom_rate_limit() {
        UserImBindingRepository bindingRepo = mock(UserImBindingRepository.class);
        com.hex.projectgovern.module.notification.UserImBinding b = new com.hex.projectgovern.module.notification.UserImBinding();
        b.setExternalUserId("u1");
        when(bindingRepo.findByUserIdInAndChannelAndEnabledTrue(any(), anyString()))
            .thenReturn(java.util.List.of(b));

        props.setEnabled(true);
        props.getChannels().put("wechat_work", true);
        props.getWechatWork().setCorpId("wxcorp");
        props.getWechatWork().setAgentId("1000002");
        props.getWechatWork().setAppSecret("secret");

        when(http.getWechatWorkToken(anyString(), anyString())).thenReturn("token");
        when(http.postJson(anyString(), any())).thenReturn("{\"errcode\":45009,\"errmsg\":\"freq limit\"}");
        var ch = new WechatWorkChannel(props, http, bindingRepo);
        assertThat(ch.send(mockMsg())).isFalse();
    }
}
