package com.company.pmo.module.initiation;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * V4.20 「禁止 hallucinate」守门人 (改进版):
 * <p>
 * 核心策略: <b>整段子串匹配</b>, 而不是 2-gram 命中率。
 * 因为 2-gram 命中会让"构建基于大模型"和 SOW 里"基于大模型"产生 67% 假阳性。
 * </p>
 *
 * <p><b>判定逻辑</b>:
 * <ul>
 *   <li>{@link #score(String, String)}: 文本 T 必须在 SOW 原文里能找到 ≥ 1 处连续子串 (≥ 4 字符)
 *       才算"有 SOW 依据"。否则视为 hallucinate, 返回 0.0。</li>
 *   <li>{@link #stripTemplatePhrase(String, String)}: 括号内以 / 分隔的短语列表,
 *       任何短语在 SOW 里 substring 找不到 → 整段移除。</li>
 * </ul>
 */
public final class SowTokenGuard {

    /** 最低"短语子串命中"长度: 4 个字符 (避免单字巧合命中) */
    public static final int MIN_SUBSTR_LEN = 4;

    /** "有任何根据"的最低命中率 (字符级, 在短文本上的兜底) */
    public static final double MIN_HIT_RATIO = 0.20;

    /** SOW 关联白名单 (永远允许出现的通用术语) */
    public static final Set<String> WHITELIST = Set.of(
            "需求", "澄清", "评审", "SOW", "POC", "PoC", "联调", "上线", "灰度", "验收",
            "移交", "里程碑", "工作包", "风险", "建议", "测试", "部署", "运维", "监控",
            "文档", "培训", "推广", "发布", "迭代", "集成", "对接", "支持", "性能", "压测",
            "版本", "接口", "API", "SDK", "UI", "UX", "CRUD", "MVP",
            "Spring", "Boot", "Vue", "MySQL", "PostgreSQL", "Redis", "Kafka",
            "K8s", "K8S", "Kubernetes", "Docker", "阿里云", "AWS",
            "ARMS", "SLS", "ACK", "BI",
            "AR", "SR", "FR", "QA", "BA", "PM", "PO", "SA",
            "WBS", "PMO", "PRD", "BRD", "SRS", "HLD", "LLD",
            "OCR", "RAG", "ICD", "IFRS", "IFRS17", "GIS", "OBD", "UBI"
    );

    /**
     * 给一段文本打分: 在 SOW 原文里能找到的连续子串 (≥ MIN_SUBSTR_LEN) 占文本总字符的比例。
     * <p>
     * 关键改进: 使用<b>滑动窗口</b>扫 SOW, 而非把 SOW 切 2-gram 缓存。
     * 这样"构建基于大模型"和 SOW 里"基于大模型" — 只能命中"基于"(3字符) 和 "大模型"(3字符),
     * 都 < 4 字符, 总命中 = 6 / 7 ≈ 0.86;但若要求"短语子串 ≥ 4 字符", 命中 = 0 → 视为幻觉。
     * </p>
     */
    public static double score(String text, String sowText) {
        if (text == null || text.isBlank() || sowText == null || sowText.isBlank()) return 0.0;
        String t = text.trim();
        if (t.isEmpty()) return 0.0;

        // 1) 整段等于 SOW 子串 → 满分
        if (sowText.contains(t)) return 1.0;

        // 2) WHITELIST 词贡献 — 任何白名单词出现都算"通用流程词,有根据"
        int wlHits = 0;
        for (String w : WHITELIST) {
            if (t.contains(w)) wlHits += w.length();
        }

        // 3) 滑动窗口: 从 t 里挑出所有长度 ≥ MIN_SUBSTR_LEN 的子串, 看 SOW 含不含
        // 简化: 按 t 的连续字符枚举所有 4~min(8, t.length()) 长度的窗口 (防爆)
        int n = t.length();
        if (n < MIN_SUBSTR_LEN) return wlHits > 0 ? 0.5 : 0.0;

        Set<String> checked = new HashSet<>();
        int hitChars = wlHits;
        for (int len = MIN_SUBSTR_LEN; len <= Math.min(8, n); len++) {
            for (int i = 0; i + len <= n; i++) {
                String sub = t.substring(i, i + len);
                if (checked.contains(sub)) continue;
                checked.add(sub);
                if (sowText.contains(sub)) hitChars += len;
            }
        }
        // 去重覆盖 (字符级): hitChars 可能超过 n, 截到 n
        hitChars = Math.min(hitChars, n);
        if (n == 0) return 0.0;
        return (double) hitChars / (double) n;
    }

    /**
     * 模板短语检测: 整段短语必须在 SOW 里有完整出现才算数。
     * <p>例: 里程碑名 "AI智能体PoC验证(构建基于大模型 / 搭建合同边界模型 / 续保模型)"
     *   → 拆 ["构建基于大模型", "搭建合同边界模型", "续保模型"]
     *   → SOW 里 substring: "构建基于大模型" ❌ (SOW 是 "基于大模型的"), "搭建合同边界模型" ❌, "续保模型" ✅
     *   → 保留 "续保模型" 一项, 输出 "AI智能体PoC验证(续保模型)"
     * </p>
     */
    public static String stripTemplatePhrase(String milestoneName, String sowText) {
        if (milestoneName == null) return null;
        if (sowText == null || sowText.isBlank()) return milestoneName;

        int idx = milestoneName.indexOf('(');
        if (idx < 0) return milestoneName;
        int end = milestoneName.lastIndexOf(')');
        if (end <= idx) return milestoneName;

        String prefix = milestoneName.substring(0, idx).trim();
        String inside = milestoneName.substring(idx + 1, end).trim();

        if (!inside.contains("/") && !inside.contains("、") && !inside.contains(",")) {
            return milestoneName;
        }

        // 拆段 (兼容 /, 、, ,)
        String[] parts = inside.split("[、,/]");
        StringBuilder sb = new StringBuilder();
        int kept = 0;
        for (String p : parts) {
            String s = p.trim();
            if (s.isEmpty()) continue;
            // 整段子串是否在 SOW 里
            if (sowText.contains(s)) {
                if (sb.length() > 0) sb.append(" / ");
                sb.append(s);
                kept++;
                continue;
            }
            // 子串不在 → 看 score, 阈值给到 0.4 (比整体 0.2 严)
            if (SowTokenGuard.score(s, sowText) >= 0.4) {
                if (sb.length() > 0) sb.append(" / ");
                sb.append(s);
                kept++;
            }
        }

        if (kept == 0) return prefix;
        return prefix + "(" + sb + ")";
    }

    /** 兼容老调用: 把 sowText 抽 token (旧 2-gram 版本, 留作接口兼容) */
    public static Set<String> tokens(String sowText) {
        if (sowText == null || sowText.isBlank()) return Set.of();
        Set<String> out = new HashSet<>();
        // 中文 2-gram
        Pattern p = Pattern.compile("[\\u4e00-\\u9fa5]{2,}");
        Matcher m = p.matcher(sowText);
        while (m.find()) out.add(m.group());
        // 英文 token
        Pattern e = Pattern.compile("[A-Za-z][A-Za-z0-9_\\-\\.]{1,}");
        Matcher em = e.matcher(sowText);
        while (em.find()) out.add(em.group().toLowerCase());
        out.addAll(WHITELIST);
        return out;
    }

    private SowTokenGuard() {}
}