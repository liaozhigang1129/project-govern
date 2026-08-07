package com.company.zhiyu.module.initiation;

import java.util.*;

/**
 * V4.21 SOW 溯源工具: 给每个 WP / Milestone 附"来源 SOW 在哪一段、命中了哪些关键词、原话片段"
 * <p>
 * 6 个字段:
 * <ul>
 *   <li>{@code sectionHint}        段号 (如 "1" / "2" / "3.2"), 从命中关键词的最近前导段号推断; 找不到则 null</li>
 *   <li>{@code matchedKeywordSpans} 命中关键词 → 在 SOW 里的 [start, end) 偏移列表 (Map&lt;kw, List&lt;[start,end]&gt;&gt;)</li>
 *   <li>{@code matchedKeywords}     命中关键词列表 (去重, 保留顺序)</li>
 *   <li>{@code evidenceSnippets}    命中处周围 ±20 字的原文片段 (Map&lt;kw, snippet&gt;)</li>
 *   <li>{@code sourceType}          "REQUIRED_KW" | "MODULE_CONTEXT" | "DELIVERABLE" | "TECH_STACK" | "GENERIC" | "WHITELIST"</li>
 *   <li>{@code confidence}          0.0~1.0, 综合 (命中数 / requiredKws.size) + WHITELIST 命中加分</li>
 * </ul>
 * <p>
 * 设计原则: 给前端面板展示"这条 WP 来自 SOW 哪一段, 原文怎么说", 也给 Step 3 apply 审计留痕。
 */
public final class SowTraceUtil {

    private SowTraceUtil() {}

    /** snippet 上下文窗口大小 (前后各 N 个字符) */
    public static final int SNIPPET_RADIUS = 20;

    /** 段号识别: SOW 里 1. 2. 3. / 1) 2) 3) / (1) (2) / 1.1 1.2 形式 */
    private static final java.util.regex.Pattern SECTION_HEAD = java.util.regex.Pattern.compile(
            "(?m)^\\s*(?:[（(]?\\s*)?(\\d+(?:\\.\\d+)?)\\s*[)、.)]\\s*");

    /**
     * 给一个 WP 构造 sowTrace 字段
     *
     * @param wpName         WP 名 (deliv 也可一起用, 用 name+ " " + deliv 拼起来)
     * @param wpDeliv        WP deliverable 描述 (可空)
     * @param requiredKws    模板里声明的关键词 (SOW 命中依据)
     * @param sourceType     来源类型 (REQUIRED_KW / MODULE_CONTEXT / DELIVERABLE / TECH_STACK / GENERIC)
     * @param sowText        完整 SOW 原文 (已 normalizeForAi 过的)
     * @return sowTrace Map; 永远非 null, 缺数据时字段为 null/空
     */
    public static Map<String, Object> build(
            String wpName, String wpDeliv, List<String> requiredKws,
            String sourceType, String sowText) {

        Map<String, Object> trace = new LinkedHashMap<>();
        if (sowText == null || sowText.isBlank()) {
            trace.put("sectionHint", null);
            trace.put("matchedKeywordSpans", Map.of());
            trace.put("matchedKeywords", List.of());
            trace.put("evidenceSnippets", Map.of());
            trace.put("sourceType", sourceType);
            trace.put("confidence", 0.0);
            return trace;
        }

        String needle = ((wpName == null ? "" : wpName) + " " + (wpDeliv == null ? "" : wpDeliv)).trim();

        // 1) 匹配关键词: requiredKws 在 SOW 里 substring 找, 同时也找 needle 里出现的 SOW 关键词
        Map<String, List<int[]>> spans = new LinkedHashMap<>();
        Set<String> matched = new LinkedHashSet<>();
        Map<String, String> snippets = new LinkedHashMap<>();

        if (requiredKws != null) {
            for (String kw : requiredKws) {
                if (kw == null || kw.isBlank()) continue;
                if (sowText.contains(kw)) {
                    matched.add(kw);
                    addAllOccurrences(spans, kw, sowText);
                    snippets.put(kw, snippetAround(sowText, sowText.indexOf(kw), kw.length()));
                }
            }
        }

        // 2) 没 requiredKws 也能从 needle 里抽 token 匹配, 给出次级 trace
        if (matched.isEmpty() && !needle.isBlank()) {
            // 从 needle 抽 2~6 字符的 SOW 子串, 找到 SOW 里的偏移
            for (int len = Math.min(6, needle.length()); len >= 2; len--) {
                for (int i = 0; i + len <= needle.length(); i++) {
                    String sub = needle.substring(i, i + len);
                    int idx = sowText.indexOf(sub);
                    if (idx >= 0) {
                        matched.add(sub);
                        addAllOccurrences(spans, sub, sowText);
                        snippets.put(sub, snippetAround(sowText, idx, len));
                    }
                }
                if (!matched.isEmpty()) break; // 至少命中一档
            }
        }

        // 3) 推 sectionHint: 取第一个命中关键词的偏移, 找它前面最近的段号
        String sectionHint = null;
        if (!matched.isEmpty()) {
            int firstPos = Integer.MAX_VALUE;
            for (List<int[]> occs : spans.values()) {
                for (int[] o : occs) {
                    if (o[0] < firstPos) firstPos = o[0];
                }
            }
            if (firstPos < Integer.MAX_VALUE) {
                sectionHint = nearestSectionBefore(sowText, firstPos);
            }
        } else {
            // 没命中任何关键词 → 拿 needle 第一次出现的位置 (如果有)
            int idx = sowText.indexOf(needle);
            if (idx >= 0) sectionHint = nearestSectionBefore(sowText, idx);
        }

        // 4) confidence: (matched / requiredKws.size) 上限 0.7, WHITELIST 命中 +0.2, 兜底 0.1
        double confidence;
        if (requiredKws != null && !requiredKws.isEmpty()) {
            confidence = Math.min(0.7, (double) matched.size() / requiredKws.size());
        } else if (!matched.isEmpty()) {
            confidence = 0.5;
        } else {
            confidence = 0.1;
        }
        if (matched.stream().anyMatch(SowTokenGuard.WHITELIST::contains)) {
            confidence = Math.min(1.0, confidence + 0.2);
        }

        // 5) spans 转成 [start, end] 形式 (list of [s,e]), 不用 Map.of 避免后续序列化繁琐
        Map<String, List<List<Integer>>> spanList = new LinkedHashMap<>();
        for (var e : spans.entrySet()) {
            List<List<Integer>> list = new ArrayList<>();
            for (int[] o : e.getValue()) {
                list.add(List.of(o[0], o[1]));
            }
            spanList.put(e.getKey(), list);
        }

        trace.put("sectionHint", sectionHint);
        trace.put("matchedKeywordSpans", spanList);
        trace.put("matchedKeywords", new ArrayList<>(matched));
        trace.put("evidenceSnippets", snippets);
        trace.put("sourceType", sourceType == null ? "GENERIC" : sourceType);
        trace.put("confidence", Math.round(confidence * 100.0) / 100.0);
        return trace;
    }

    /**
     * 给 milestone 用的简化版 trace (不依赖 requiredKws, 只看拼接在 name 里的 SOW 模块)
     */
    public static Map<String, Object> buildMilestone(String milestoneName, String sowText) {
        return build(milestoneName, null, List.of(), "MILESTONE_NAME", sowText);
    }

    /**
     * 命中处前后 ±SNIPPET_RADIUS 字片段
     */
    private static String snippetAround(String text, int idx, int kwLen) {
        if (idx < 0) return "";
        int s = Math.max(0, idx - SNIPPET_RADIUS);
        int e = Math.min(text.length(), idx + kwLen + SNIPPET_RADIUS);
        StringBuilder sb = new StringBuilder();
        if (s > 0) sb.append("…");
        sb.append(text, s, e);
        if (e < text.length()) sb.append("…");
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private static void addAllOccurrences(Map<String, List<int[]>> spans, String kw, String text) {
        List<int[]> occs = new ArrayList<>();
        int from = 0;
        while (true) {
            int i = text.indexOf(kw, from);
            if (i < 0) break;
            occs.add(new int[]{i, i + kw.length()});
            from = i + Math.max(1, kw.length());
            if (occs.size() >= 5) break; // 上限 5 个, 避免过长
        }
        spans.put(kw, occs);
    }

    /**
     * 在 text[0..pos] 区间里, 找最近的段号 (返回 "1" / "2.1" 这种字符串)
     * <p>实现: 用 SECTION_HEAD 找 pos 之前所有 match, 取最后一个 (最近的段号)</p>
     */
    private static String nearestSectionBefore(String text, int pos) {
        if (text == null || pos <= 0) return null;
        String prefix = text.substring(0, pos);
        java.util.regex.Matcher m = SECTION_HEAD.matcher(prefix);
        String last = null;
        while (m.find()) {
            last = m.group(1);
        }
        return last;
    }
}
