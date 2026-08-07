package com.hex.projectgovern.module.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IM 通道配置(P2-A)。
 *
 * 示例 application.yml:
 * <pre>
 * pmo:
 *   im:
 *     enabled: true                                # 总开关
 *     channels:                                    # 各通道独立开关
 *       wechat_work: false
 *       dingtalk: false
 *       feishu: false
 *     wechat-work:
 *       corp-id: ${PMO_WECOM_CORP_ID:}
 *       agent-id: ${PMO_WECOM_AGENT_ID:}
 *       app-secret: ${PMO_WECOM_APP_SECRET:}
 *     dingtalk:
 *       webhook-url: ${PMO_DINGTALK_WEBHOOK:}
 *       secret: ${PMO_DINGTALK_SECRET:}            # 群机器人加签 secret
 *     feishu:
 *       webhook-url: ${PMO_FEISHU_WEBHOOK:}
 *       secret: ${PMO_FEISHU_SECRET:}
 *     routing:                                     # 路由策略
 *       default: [email]                           # 没绑定 IM 的用户走哪些通道
 *       bound-user: [email, im]                    # 绑定过 IM 的用户走哪些通道
 * </pre>
 *
 * 设计:
 *  - 默认全部关闭 → 零侵入(灰度期间不上 IM)
 *  - 三平台都靠 Webhook/自建应用 → 无需长连接/无状态
 *  - 路由 default 与 bound-user 完全独立配置
 */
@ConfigurationProperties(prefix = "pmo.im")
public class ImProperties {

    /** 总开关 */
    private boolean enabled = false;

    /** 各通道细粒度开关(覆盖 enabled) */
    private Map<String, Boolean> channels = new HashMap<>();

    /** 企业微信 */
    private WechatWork wechatWork = new WechatWork();
    /** 钉钉 */
    private DingTalk dingtalk = new DingTalk();
    /** 飞书 */
    private Feishu feishu = new Feishu();
    /** 路由策略 */
    private Routing routing = new Routing();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Map<String, Boolean> getChannels() { return channels; }
    public void setChannels(Map<String, Boolean> channels) { this.channels = channels; }
    public WechatWork getWechatWork() { return wechatWork; }
    public void setWechatWork(WechatWork w) { this.wechatWork = w; }
    public DingTalk getDingtalk() { return dingtalk; }
    public void setDingtalk(DingTalk d) { this.dingtalk = d; }
    public Feishu getFeishu() { return feishu; }
    public void setFeishu(Feishu f) { this.feishu = f; }
    public Routing getRouting() { return routing; }
    public void setRouting(Routing r) { this.routing = r; }

    public boolean isChannelEnabled(String code) {
        if (!enabled) return false;
        Boolean v = channels.get(code);
        return v == null ? false : v;
    }

    // ===== 子配置 =====
    public static class WechatWork {
        private String corpId;
        private String agentId;
        private String appSecret;
        /**
         * gettoken URL(可被 env 覆盖;用于 mock 测试场景)。
         * 默认: <a href="https://qyapi.weixin.qq.com/cgi-bin/gettoken">...</a>
         */
        private String gettokenUrl = "https://qyapi.weixin.qq.com/cgi-bin/gettoken";
        /**
         * 应用消息发送 URL(可被 env 覆盖;用于 mock 测试场景)。
         * 默认: <a href="https://qyapi.weixin.qq.com/cgi-bin/message/send">...</a>
         */
        private String sendUrl = "https://qyapi.weixin.qq.com/cgi-bin/message/send";
        public boolean isConfigured() {
            return notBlank(corpId) && notBlank(agentId) && notBlank(appSecret);
        }
        public String getCorpId() { return corpId; }
        public void setCorpId(String c) { this.corpId = c; }
        public String getAgentId() { return agentId; }
        public void setAgentId(String a) { this.agentId = a; }
        public String getAppSecret() { return appSecret; }
        public void setAppSecret(String s) { this.appSecret = s; }
        public String getGettokenUrl() { return gettokenUrl; }
        public void setGettokenUrl(String u) { this.gettokenUrl = u; }
        public String getSendUrl() { return sendUrl; }
        public void setSendUrl(String u) { this.sendUrl = u; }
    }
    public static class DingTalk {
        private String webhookUrl;
        private String secret;
        public boolean isConfigured() { return notBlank(webhookUrl); }
        public String getWebhookUrl() { return webhookUrl; }
        public void setWebhookUrl(String w) { this.webhookUrl = w; }
        public String getSecret() { return secret; }
        public void setSecret(String s) { this.secret = s; }
    }
    public static class Feishu {
        private String webhookUrl;
        private String secret;
        public boolean isConfigured() { return notBlank(webhookUrl); }
        public String getWebhookUrl() { return webhookUrl; }
        public void setWebhookUrl(String w) { this.webhookUrl = w; }
        public String getSecret() { return secret; }
        public void setSecret(String s) { this.secret = s; }
    }
    public static class Routing {
        /** 无 IM 绑定的用户走哪些通道(默认 [email]) */
        private List<String> defaultRoute = new ArrayList<>(List.of("email"));
        /** 有 IM 绑定的用户走哪些通道(默认 [email, im] → 实际通道由 binding.channel 决定) */
        private List<String> boundUserRoute = new ArrayList<>(List.of("email", "im"));
        public List<String> getDefaultRoute() { return defaultRoute; }
        public void setDefaultRoute(List<String> d) { this.defaultRoute = d; }
        public List<String> getBoundUserRoute() { return boundUserRoute; }
        public void setBoundUserRoute(List<String> b) { this.boundUserRoute = b; }
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
