package com.hex.projectgovern.module.approval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 跳过条件默认实现:
 * <ul>
 *   <li>解析 JSON bizPayload 顶层 KEY
 *   <li>支持 KEY&lt;NUM / KEY&gt;NUM / KEY==NUM / KEY!=NUM
 *   <li>返回 true = 跳过该 step
 * </ul>
 *
 * <p>e.g. skipWhen="amount&lt;1000" + payload={amount:500} → true
 */
@Slf4j
@Component
public class DefaultSkipConditionEvaluator implements SkipConditionEvaluator {

    private static final Pattern EXPR = Pattern.compile("(\\w+)\\s*(<|>|==|!=)\\s*(-?\\d+(?:\\.\\d+)?)");

    @Override
    public boolean shouldSkip(String skipWhen, String bizPayload) {
        if (skipWhen == null || skipWhen.isBlank()) return false;
        if (bizPayload == null || bizPayload.isBlank()) return false;

        Matcher m = EXPR.matcher(skipWhen.trim());
        if (!m.matches()) {
            log.warn("[SkipConditionEvaluator] 跳过条件格式不识别: '{}'", skipWhen);
            return false;
        }
        String key = m.group(1);
        String op = m.group(2);
        double threshold = Double.parseDouble(m.group(3));

        Double actual = extractNumber(bizPayload, key);
        if (actual == null) return false;

        return switch (op) {
            case "<"  -> actual <  threshold;
            case ">"  -> actual >  threshold;
            case "==" -> Math.abs(actual - threshold) < 1e-9;
            case "!=" -> Math.abs(actual - threshold) >= 1e-9;
            default   -> false;
        };
    }

    /** 从 JSON 字符串里提取 KEY:NUMBER (不依赖 JSON 库) */
    private Double extractNumber(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
        Matcher m = p.matcher(json);
        if (m.find()) {
            try { return Double.parseDouble(m.group(1)); }
            catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}