package com.company.zhiyu.module.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 飞书群机器人通道(P2-A)。
 *
 * 官方文档:
 *  - 自定义机器人: https://open.feishu.cn/document/client-docs/bot-v3/add-custom-bot
 *  - 加签模式: timestamp + secret 算 sign
 *  - 消息类型: text/post/rich_text 等;此处用 interactive(消息卡片)支持更丰富
 *
 * 设计:
 *  - 用 interactive 类型(标题/内容/链接按钮)
 *  - 加签:timestamp(秒) + secret
 *  - 目标 = 群(user_im_binding 暂只作"绑定存在"标记)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FeishuChannel implements NotificationChannel {

    private final ImProperties props;
    private final ImHttpClient http;

    @Override public Type type() { return Type.FEISHU; }

    @Override
    public boolean isEnabled() {
        return props.isChannelEnabled("feishu") && props.getFeishu().isConfigured();
    }

    @Override
    public boolean send(NotificationMessage msg) {
        if (!isEnabled()) {
            log.debug("[Feishu] disabled, skip category={}", msg.category());
            return false;
        }

        // 1) 加签 URL
        String url;
        if (props.getFeishu().getSecret() != null && !props.getFeishu().getSecret().isBlank()) {
            url = http.feishuSignedUrl(props.getFeishu().getWebhookUrl(),
                    props.getFeishu().getSecret());
        } else {
            url = props.getFeishu().getWebhookUrl();
        }

        // 2) 构造 interactive 消息体
        Map<String, Object> body = new HashMap<>();
        body.put("msg_type", "interactive");

        Map<String, Object> card = new HashMap<>();
        card.put("header", buildHeader(msg));
        card.put("elements", buildElements(msg));
        body.put("card", card);

        // 3) 发送
        String resp = http.postJson(url, body);
        if (resp == null) return false;

        // 4) 校验
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> json = http.mapper().readValue(resp, Map.class);
            Object code = json.get("code");
            if (code != null && !code.equals(0)) {
                log.warn("[Feishu] send code={} msg={} category={}",
                        code, json.get("msg"), msg.category());
                return false;
            }
            log.info("[Feishu] sent category={} recipients={}",
                    msg.category(), msg.recipientUserIds().size());
            return true;
        } catch (Exception e) {
            log.warn("[Feishu] parse resp err: {}", e.getMessage());
            return false;
        }
    }

    private Map<String, Object> buildHeader(NotificationMessage msg) {
        Map<String, Object> title = new HashMap<>();
        title.put("tag", "plain_text");
        title.put("content", safe(msg.title()));

        Map<String, Object> header = new HashMap<>();
        header.put("title", title);
        header.put("template", "blue");
        return header;
    }

    private java.util.List<Map<String, Object>> buildElements(NotificationMessage msg) {
        java.util.List<Map<String, Object>> elements = new java.util.ArrayList<>();

        // 正文 markdown
        StringBuilder text = new StringBuilder();
        text.append(safe(msg.summary()));
        if (msg.resourceCode() != null) {
            text.append("\n\n编号: ").append(safe(msg.resourceCode()));
        }
        Map<String, Object> contentBody = new HashMap<>();
        contentBody.put("tag", "lark_md");
        contentBody.put("content", text.toString());
        Map<String, Object> div = new HashMap<>();
        div.put("tag", "div");
        div.put("text", contentBody);
        elements.add(div);

        // 操作按钮(若有链接)
        if (msg.linkUrl() != null && !msg.linkUrl().isBlank()) {
            Map<String, Object> btn = new HashMap<>();
            btn.put("tag", "button");
            btn.put("text", Map.of("tag", "plain_text", "content", "查看详情"));
            btn.put("type", "primary");
            btn.put("url", msg.linkUrl());
            Map<String, Object> action = new HashMap<>();
            action.put("tag", "action");
            action.put("actions", java.util.List.of(btn));
            elements.add(action);
        }

        return elements;
    }

    private String safe(String s) {
        if (s == null) return "";
        return s.replace("`", "'");
    }
}
