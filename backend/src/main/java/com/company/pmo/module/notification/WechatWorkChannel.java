package com.company.pmo.module.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 企业微信自建应用通道(P2-A)。
 *
 * 官方文档:
 *  - access_token: https://developer.work.weixin.qq.com/document/path/91039
 *  - 应用消息推送: https://developer.work.weixin.qq.com/document/path/90236
 *
 * 设计:
 *  - 收件人通过 user_im_binding(WECHAT_WORK.external_user_id)查得
 *  - touser 用 '|' 分隔多个 userid
 *  - 卡片:markdown 类型(支持链接、加粗、彩色)
 *  - 安全:appSecret 通过 properties 注入,不打 log
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WechatWorkChannel implements NotificationChannel {

    private final ImProperties props;
    private final ImHttpClient http;
    private final UserImBindingRepository bindingRepo;

    @Override public Type type() { return Type.WECHAT_WORK; }

    @Override
    public boolean isEnabled() {
        return props.isChannelEnabled("wechat_work") && props.getWechatWork().isConfigured();
    }

    @Override
    public boolean send(NotificationMessage msg) {
        if (!isEnabled()) {
            log.debug("[WechatWork] disabled, skip category={}", msg.category());
            return false;
        }
        // 1) 拿目标 userid 列表
        List<String> userIds = bindingRepo
                .findByUserIdInAndChannelAndEnabledTrue(msg.recipientUserIds(),
                        NotificationChannel.Type.WECHAT_WORK.code())
                .stream()
                .map(UserImBinding::getExternalUserId)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            log.info("[WechatWork] no bound users for recipients={} category={}",
                    msg.recipientUserIds(), msg.category());
            return false;
        }

        // 2) 拿 access_token
        String token = http.getWechatWorkToken(
                props.getWechatWork().getCorpId(), props.getWechatWork().getAppSecret());
        if (token == null) return false;

        // 3) 构造消息体(markdown 类型)
        Map<String, Object> body = new HashMap<>();
        body.put("touser", String.join("|", userIds));
        body.put("msgtype", "markdown");
        body.put("agentid", Integer.parseInt(props.getWechatWork().getAgentId()));

        Map<String, Object> markdown = new HashMap<>();
        markdown.put("content", buildMarkdownContent(msg));
        body.put("markdown", markdown);

        // 4) 发送
        String url = props.getWechatWork().getSendUrl() + "?access_token=" + token;
        String resp = http.postJson(url, body);
        if (resp == null) return false;

        // 5) 校验 errcode
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> json = http.mapper().readValue(resp, Map.class);
            Object code = json.get("errcode");
            if (code != null && !code.equals(0)) {
                log.warn("[WechatWork] send errcode={} errmsg={} category={}",
                        code, json.get("errmsg"), msg.category());
                return false;
            }
            log.info("[WechatWork] sent to {} users category={}", userIds.size(), msg.category());
            return true;
        } catch (Exception e) {
            log.warn("[WechatWork] parse resp err: {}", e.getMessage());
            return false;
        }
    }

    /** 渲染 markdown 正文 */
    private String buildMarkdownContent(NotificationMessage msg) {
        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(safe(msg.title())).append("\n\n");
        sb.append(safe(msg.summary())).append("\n\n");
        if (msg.resourceCode() != null) {
            sb.append("> 编号: `").append(safe(msg.resourceCode())).append("`\n");
        }
        if (msg.linkUrl() != null && !msg.linkUrl().isBlank()) {
            sb.append("\n[查看详情](").append(safe(msg.linkUrl())).append(")");
        }
        return sb.toString();
    }

    /** 防 markdown 注入(只防 ` [` < 等字符) */
    private String safe(String s) {
        if (s == null) return "";
        return s.replace("`", "'")
                .replace("[", "【")
                .replace("]", "】");
    }
}
