package com.company.zhiyu.module.initiation;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * V4.27 SOW Skill — 需求提取器 (Step ① 文本预处理 + Step ② PMI 需求提取)
 *
 * 职责: 把 SOW 自然语言切成"可执行条款", 每条满足 SMART + MoSCoW, 给出:
 *   - id (REQ-NNN, 自增稳定: 按全文出现顺序)
 *   - title (< 15 词, 2-16 字名词短语)
 *   - description (完整需求描述)
 *   - priority (Must/Should/Could/Won't, 默认按章节推断, 可被 SOW 内"必须/应当/建议"等措辞覆盖)
 *   - type (functional/non-functional/management — *粗分类*, 9 维 NFR 细化走 SowNfrClassifier)
 *   - originalQuote (SOW 原文片段, 用于 RTM 反查)
 *
 * 触发模式 (按优先级从高到低):
 *   1) 编号条款: ^\s*(\d+(\.\d+)*)[\s.、)]\s* → 整段作为一条
 *   2) "shall/must/should" 触发句: 找到 shall 往回找最近句号/分号, 取整句
 *   3) 中文触发词: "应当/必须/须/不得/禁止/支持/提供/实现/对接/集成/完成" 整句
 *   4) 项目符号: ^\s*[-•·*]\s* 一条 (不强求 trigger, 因为 "- 必须实现 X" 没有句末标点)
 *   5) 段落兜底: 长度 30-300 字的整段作为一条 (避免把整页当一条)
 *
 * 设计取舍:
 *   - **不调 LLM**, 全部确定性正则 + 启发式。理由同 InitiationAiWbsService: 一致性 + 可解释
 *   - 不做"复合条款拆分": 太激进会把"客户管理和商机管理"这种并列误拆成 2 条孤儿,
 *     失去语义上下文。本类只识别"颗粒度 ≈ SOW 一段", 后续 WBS 横向展平规则会细化。
 *   - title 抽取: 优先取条款编号后的前 12 字, 二次压缩到 2-16 字名词短语 (抗污染规则 #3)
 */
@Slf4j
public class SowRequirementExtractor {

    /** 1) 编号条款: 1. / 1.1 / 1.1.2 等 */
    private static final Pattern NUMBERED_CLAUSE = Pattern.compile(
            "(?m)^\\s*(\\d+(?:\\.\\d+){0,3})[\\s.、)]+\\s*([^\\n]+(?:\\n(?!\\s*\\d+(?:\\.\\d+){0,3}[\\s.、)]).*)*)"
    );

    /** 2) 项目符号: - / • / · / * */
    private static final Pattern BULLET = Pattern.compile(
            "(?m)^\\s*[-•·*]\\s*([^\\n]+)"
    );

    /** 3a) 英文 shall/must/should 触发 (整句: 从句号/分号边界) */
    private static final Pattern ENGLISH_TRIGGER = Pattern.compile(
            "([^.；;\\n]*(?:\\b(?:shall|must|should|will|may not|shall not)\\b)[^.；;\\n]*[.；;])",
            Pattern.CASE_INSENSITIVE
    );

    /** 3b) 中文触发词 */
    private static final Pattern CHINESE_TRIGGER = Pattern.compile(
            "([^.。;；\\n]*(?:应当|必须|须|不得|禁止|支持|提供|实现|对接|集成|完成|交付|验收|上线|试运行|培训|满足|达到|确保|本期不做|选择|启用|建设|制定)[^.。;；\\n]*[.。;；])"
    );

    /** 4) MoSCoW 关键词 (覆盖优先级): [priority, regex] */
    private static final String[][] MOSCOW_RULES = new String[][]{
            {"Must",   "必须|强制|不得|禁止|shall|must|mandatory|critical|MVP|必备"},
            {"Should", "应当|应该|需|应|建议|should|recommend|important"},
            {"Could",  "可以|可选|若|若需要|could|may|optional|nice-to-have"},
            {"Won't",  "本期不做|不做|未来|out of scope|deferred"}
    };

    /** 5) 类型初判 (粗分类, 9 维细化走 NFR 分类器): [type, regex] */
    private static final String[][] TYPE_RULES = new String[][]{
            {"management",     "RACI|评审|变更|会议|报告|周报|月报|沟通|对接.*流程|治理|CCB|审批|文档管理|配置管理"},
            {"non-functional", "性能|并发|可用性|可靠性|安全|加密|脱敏|等保|GDPR|HIPAA|PCI|SLA|RTO|RPO|扩展|易用|可维护|可观测|可追溯|合规|审计|压测|限流|响应时间|时延|延迟|P99|TPS"}
            // functional 兜底走最后 return, 不入表
    };

    /** 单条 REQ 最大长度 (字符), 防止把整页当一条 */
    private static final int MAX_REQ_LEN = 600;
    /** 单条 REQ 最小长度 (字符), 太短视为噪声 */
    private static final int MIN_REQ_LEN = 6;

    /**
     * 主入口: 提取需求清单
     */
    public List<ExtractedRequirement> extract(String sowText) {
        if (sowText == null || sowText.isBlank()) return List.of();
        String normalized = normalize(sowText);

        List<ExtractedRequirement> reqs = new ArrayList<>();
        boolean[] occupied = new boolean[normalized.length()];

        extractNumbered(normalized, occupied, reqs);
        extractBullets(normalized, occupied, reqs);
        extractTriggerSentences(normalized, occupied, reqs);
        extractFallbackParagraphs(normalized, occupied, reqs);

        reqs = dedupAndAssignIds(reqs);
        log.info("[SowRequirementExtractor] extracted {} requirements from {} chars SOW",
                reqs.size(), normalized.length());
        return reqs;
    }

    /** 全半角统一 + 去 [SOW/xxx] marker + 多余空白压缩 */
    private String normalize(String s) {
        String t = s.replace('（', '(').replace('）', ')')
                    .replace('：', ':').replace('；', ';')
                    .replace('，', ',').replace('。', '.');
        t = t.replaceAll("\\[SOW/[^\\]]+\\]", "");
        t = t.replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n");
        return t.trim();
    }

    /** 阶段 A: 编号条款 */
    private void extractNumbered(String text, boolean[] occupied, List<ExtractedRequirement> out) {
        Matcher m = NUMBERED_CLAUSE.matcher(text);
        while (m.find()) {
            String clauseNo = m.group(1);
            String body = m.group(2).trim();
            if (!acceptLength(body)) continue;
            if (!hasTrigger(body)) continue; // 必须含触发词, 否则视为章节标题
            markOccupied(occupied, m.start(), m.end());
            out.add(buildReq("clause:" + clauseNo, body,
                    text.substring(m.start(), Math.min(m.end(), m.start() + 200))));
        }
    }

    /** 阶段 B: 项目符号 — 不强求 trigger, 因为 "- 必须实现 X" 通常没有句末标点 */
    private void extractBullets(String text, boolean[] occupied, List<ExtractedRequirement> out) {
        Matcher m = BULLET.matcher(text);
        while (m.find()) {
            if (isOccupied(occupied, m.start(), m.end())) continue;
            String body = m.group(1).trim();
            if (!acceptLength(body)) continue;
            markOccupied(occupied, m.start(), m.end());
            out.add(buildReq("bullet", body, body));
        }
    }

    /** 阶段 C: 触发句 (英文 shall + 中文触发词) */
    private void extractTriggerSentences(String text, boolean[] occupied, List<ExtractedRequirement> out) {
        for (Pattern p : List.of(ENGLISH_TRIGGER, CHINESE_TRIGGER)) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                if (isOccupied(occupied, m.start(), m.end())) continue;
                String body = m.group(1).trim();
                if (!acceptLength(body)) continue;
                markOccupied(occupied, m.start(), m.end());
                out.add(buildReq("trigger", body, body));
            }
        }
    }

    /** 阶段 D: 段落兜底 — 中等长度 (50-300 字) 且未被占据的段视为上下文需求 */
    private void extractFallbackParagraphs(String text, boolean[] occupied, List<ExtractedRequirement> out) {
        String[] paragraphs = text.split("\\n\\s*\\n");
        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.length() < 50 || trimmed.length() > 300) continue;
            int idx = text.indexOf(trimmed);
            if (idx < 0) continue;
            if (isOccupied(occupied, idx, idx + trimmed.length())) continue;
            if (!hasBusinessKeyword(trimmed)) continue;
            markOccupied(occupied, idx, idx + trimmed.length());
            out.add(buildReq("paragraph", trimmed, trimmed));
        }
    }

    private boolean hasBusinessKeyword(String s) {
        return s.matches(".*(系统|模块|功能|接口|平台|服务|业务|数据|用户|客户|流程|报表|界面).*");
    }

    private boolean hasTrigger(String s) {
        return ENGLISH_TRIGGER.matcher(s).find() || CHINESE_TRIGGER.matcher(s).find();
    }

    private boolean acceptLength(String s) {
        return s.length() >= MIN_REQ_LEN && s.length() <= MAX_REQ_LEN;
    }

    private boolean isOccupied(boolean[] occ, int start, int end) {
        for (int i = start; i < Math.min(end, occ.length); i++) if (occ[i]) return true;
        return false;
    }

    private void markOccupied(boolean[] occ, int start, int end) {
        for (int i = start; i < Math.min(end, occ.length); i++) occ[i] = true;
    }

    /** 构造 REQ, 算 title + priority + type (id 在 dedupAndAssignIds 阶段统一赋) */
    private ExtractedRequirement buildReq(String origin, String body, String quote) {
        String title = makeTitle(body);
        String priority = inferPriority(body);
        String type = inferType(body);
        return new ExtractedRequirement(null, origin, title, body, priority, type, quote);
    }

    /** title: 取条款前 12 字, 二次压缩到 2-16 字 (抗污染规则 #3) */
    private String makeTitle(String body) {
        String t = body.replaceFirst("^\\d+(?:\\.\\d+){0,3}[\\s.、)）]+", "")
                       .replaceFirst("^[-•·*]\\s*", "")
                       .trim();
        int cut = t.length();
        for (String sep : new String[]{".", "。", ";", "；", ",", "，"}) {
            int i = t.indexOf(sep);
            if (i > 0 && i < cut) cut = i;
        }
        t = t.substring(0, Math.min(cut, t.length())).trim();
        if (t.length() > 16) t = t.substring(0, 15) + "…";
        if (t.length() < 2) t = "未命名需求";
        return t;
    }

    /** MoSCoW 推断: 优先级 Must > Should > Could > Won't; SOW 里出现"必须/应当/建议"会覆盖 */
    private String inferPriority(String body) {
        for (String[] rule : MOSCOW_RULES) {
            if (Pattern.compile(rule[1], Pattern.CASE_INSENSITIVE).matcher(body).find()) return rule[0];
        }
        return "Should";
    }

    /** 粗分类: management > non-functional > functional(兜底) */
    private String inferType(String body) {
        for (String[] rule : TYPE_RULES) {
            if (Pattern.compile(rule[1], Pattern.CASE_INSENSITIVE).matcher(body).find()) return rule[0];
        }
        return "functional";
    }

    /** 去重 (按 title) + 自增 REQ-NNN */
    private List<ExtractedRequirement> dedupAndAssignIds(List<ExtractedRequirement> raw) {
        Map<String, ExtractedRequirement> byTitle = new LinkedHashMap<>();
        for (ExtractedRequirement r : raw) {
            ExtractedRequirement exist = byTitle.get(r.title());
            if (exist == null || r.description().length() > exist.description().length()) {
                byTitle.put(r.title(), r);
            }
        }
        List<ExtractedRequirement> result = new ArrayList<>();
        int n = 0;
        for (ExtractedRequirement r : byTitle.values()) {
            n++;
            result.add(r.withId(String.format("REQ-%03d", n)));
        }
        return result;
    }

    /** 单条需求 (不可变 record) */
    public record ExtractedRequirement(
            String id,
            String origin,
            String title,
            String description,
            String priority,    // Must | Should | Could | Won't
            String type,        // functional | non-functional | management
            String originalQuote
    ) {
        public ExtractedRequirement withId(String newId) {
            return new ExtractedRequirement(newId, origin, title, description, priority, type, originalQuote);
        }
    }
}
