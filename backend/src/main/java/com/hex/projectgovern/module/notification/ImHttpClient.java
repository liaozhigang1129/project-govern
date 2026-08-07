package com.hex.projectgovern.module.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用 HTTP 客户端工具 + 企业微信 access_token 缓存。
 *
 * 抽出来避免 3 个 IM 通道各写一遍 RestTemplate / JSON 序列化 / 错误处理。
 *
 * 关键设计:
 *  - access_token 内存缓存 7000s(企业微信官方有效期 7200s,留 200s 余量)
 *  - 4xx/5xx 全部吞 → 返回 false,打 warn log
 *  - connectTimeout/readTimeout 3s(避免 IM 故障拖垮业务)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImHttpClient {

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();
    /** 注入用于读 pmo.im.wechat-work.gettoken-url(可被 env 覆盖) */
    private final com.hex.projectgovern.module.notification.ImProperties props;

    /** wechat-work access_token 缓存: key=corpId, value=(token, expiresAt) */
    private final Map<String, TokenEntry> wechatTokenCache = new ConcurrentHashMap<>();
    private static final long WECHAT_TOKEN_TTL_SECONDS = 7000L;

    private record TokenEntry(String token, Instant expiresAt) {}

    /**
     * POST JSON 到 url。返回 HTTP body 字符串;非 2xx 返回 null(已 log warn)。
     */
    public String postJson(String url, Object body) {
        try {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> resp = rest.postForEntity(url, new HttpEntity<>(body, h), String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                log.warn("[IM] POST {} -> HTTP {}: {}", url, resp.getStatusCode().value(), resp.getBody());
                return null;
            }
            return resp.getBody();
        } catch (RestClientException e) {
            log.warn("[IM] POST {} failed: {}", url, e.getMessage());
            return null;
        }
    }

    /** GET,返回 body;非 2xx 返回 null */
    public String get(String url) {
        try {
            ResponseEntity<String> resp = rest.getForEntity(url, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                log.warn("[IM] GET {} -> HTTP {}", url, resp.getStatusCode().value());
                return null;
            }
            return resp.getBody();
        } catch (RestClientException e) {
            log.warn("[IM] GET {} failed: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * 拿企业微信 access_token(带缓存)。
     * @return token 或 null(失败)
     */
    public String getWechatWorkToken(String corpId, String appSecret) {
        TokenEntry cached = wechatTokenCache.get(corpId);
        if (cached != null && cached.expiresAt.isAfter(Instant.now())) {
            return cached.token;
        }
        String url = props.getWechatWork().getGettokenUrl()
                + "?corpid=" + URLEncoder.encode(corpId, StandardCharsets.UTF_8)
                + "&corpsecret=" + URLEncoder.encode(appSecret, StandardCharsets.UTF_8);
        String body = get(url);
        if (body == null) return null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> json = mapper.readValue(body, Map.class);
            Object code = json.get("errcode");
            if (code != null && !code.equals(0)) {
                log.warn("[WechatWork] gettoken errcode={} errmsg={}", code, json.get("errmsg"));
                return null;
            }
            String token = (String) json.get("access_token");
            if (token == null || token.isBlank()) {
                log.warn("[WechatWork] gettoken no access_token in body: {}", body);
                return null;
            }
            wechatTokenCache.put(corpId, new TokenEntry(token,
                    Instant.now().plusSeconds(WECHAT_TOKEN_TTL_SECONDS)));
            log.info("[WechatWork] gettoken OK (cached 7000s)");
            return token;
        } catch (Exception e) {
            log.warn("[WechatWork] gettoken parse err: {}", e.getMessage());
            return null;
        }
    }

    /** 给钉钉/飞书加签:url 后追加 timestamp + sign */
    public String appendSign(String baseUrl, String secret, long timestamp, String stringToSign) {
        try {
            String sign = HmacSha1Util.hmacSha1(stringToSign, secret);
            String sep = baseUrl.contains("?") ? "&" : "?";
            return baseUrl + sep + "timestamp=" + timestamp + "&sign=" + sign;
        } catch (Exception e) {
            log.warn("[IM] hmacSha1 failed: {}", e.getMessage());
            return baseUrl;
        }
    }

    public ObjectMapper mapper() { return mapper; }

    /** 给钉钉加签(整体) */
    public String dingTalkSignedUrl(String webhook, String secret) {
        long ts = System.currentTimeMillis();
        String stringToSign = ts + "\n" + secret;
        return appendSign(webhook, secret, ts, stringToSign);
    }

    /** 给飞书加签 */
    public String feishuSignedUrl(String webhook, String secret) {
        long ts = System.currentTimeMillis() / 1000L;
        String stringToSign = ts + "\n" + secret;
        return appendSign(webhook, secret, ts, stringToSign);
    }
}
