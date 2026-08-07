package com.hex.projectgovern.module.notification;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * HMAC-SHA1 加签工具(钉钉/飞书 Webhook 用)。
 *
 * - 钉钉:stringToSign = `${timestamp}\n${secret}` → sign 整体 URL-encode
 * - 飞书:stringToSign = `${timestamp}\n${secret}` → sign 整体 URL-encode
 *
 * 抽出来让 2 个通道都复用。
 */
final class HmacSha1Util {
    private HmacSha1Util() {}

    static String hmacSha1(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        String base64 = Base64.getEncoder().encodeToString(raw);
        return URLEncoder.encode(base64, StandardCharsets.UTF_8);
    }
}
