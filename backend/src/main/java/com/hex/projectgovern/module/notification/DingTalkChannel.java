package com.hex.projectgovern.module.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 钉钉群机器人通道(P2-A)。
 *
 * 官方文档:
 *  - 群机器人: https://open.dingtalk.com/document/orgapp/custom-robot-access
 *  - 加签模式: 用 secret 时必须用 timestamp+sign
 *  - 消息类型: 支持 text/link/markdown/actionCard/feedCard
 *
 * 设计:
 *  - 用 markdown 消息类型(支持标题/字体加粗/链接)
 *  - 加签:timestamp + secret 算 sign
 *  - 目标 = 群(@全体或 @指定人)
 *  - 收件人映射:此版本只推到群,user_im_binding.channel='dingtalk' 仅作"此用户已绑定"标记
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DingTalkChannel implements NotificationChannel {

    private final ImProperties props;
    private final ImHttpClient http;

    @Override public Type type() { return Type.DINGTALK; }

    @Override
    public boolean isEnabled() {
        return props.isChannelEnabled("dingtalk") && props.getDingtalk().isConfigured();
    }

    @Override
    public boolean send(NotificationMessage msg) {
        if (!isEnabled()) {
            log.debug("[DingTalk] disabled, skip category={}", msg.category());
            return false;
        }

        // 1) 加签 URL
        String url;
        if (props.getDingtalk().getSecret() != null && !props.getDingtalk().getSecret().isBlank()) {
            url = http.dingTalkSignedUrl(props.getDingtalk().getWebhookUrl(),
                    props.getDingtalk().getSecret());
        } else {
            url = props.getDingtalk().getWebhookUrl();
        }

        // 2) 构造 markdown 消息体
        Map<String, Object> body = new HashMap<>();
        body.put("msgtype", "markdown");

        Map<String, Object> markdown = new HashMap<>();
        markdown.put("title", truncate(msg.title(), 64));
        markdown.put("text", buildMarkdownText(msg));
        body.put("markdown", markdown);

        // 3) @全员(可选;灰度期间默认不开,避免扰民)
        Map<String, Object> at = new HashMap<>();
        at.put("isAtAll", false);
        body.put("at", at);

        // 4) 发送
        String resp = http.postJson(url, body);
        if (resp == null) return false;

        // 5) 校验
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> json = http.mapper().readValue(resp, Map.class);
            Object code = json.get("errcode");
            if (code != null && !code.equals(0)) {
                log.warn("[DingTalk] send errcode={} errmsg={} category={}",
                        code, json.get("errmsg"), msg.category());
                return false;
            }
            log.info("[DingTalk] sent category={} recipients={}",
                    msg.category(), msg.recipientUserIds().size());
            return true;
        } catch (Exception e) {
            log.warn("[DingTalk] parse resp err: {}", e.getMessage());
            return false;
        }
    }

    /** 钉钉 markdown 支持字体加粗、链接、换行 */
    private String buildMarkdownText(NotificationMessage msg) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(safe(msg.title())).append("\n\n");
        sb.append(safe(msg.summary())).append("\n\n");
        if (msg.resourceCode() != null) {
            sb.append("> 编号: ").append(safe(msg.resourceCode())).append("\n");
        }
        if (msg.linkUrl() != null && !msg.linkUrl().isBlank()) {
            sb.append("\n[查看详情](").append(safe(msg.linkUrl())).append(")");
        }
        return sb.toString();
    }

    private String safe(String s) {
        if (s == null) return "";
        return s.replace("`", "'");
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
