package com.company.pmo.module.initiation;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * V4.27 SOW Skill — 风险识别器 (Step ⑥)
 *
 * 6 大类风险 (PMI 风险类别 + 软件行业扩充):
 *   - technical        技术: 新技术栈/接口复杂度/性能/兼容性
 *   - schedule         进度: 工期紧/并行依赖/关键路径/资源冲突
 *   - cost             成本: 预算超支/汇率/人力溢价
 *   - external         外部: 供应商/法规/标准/市场变化
 *   - quality          质量: 缺陷率/可维护性/技术债
 *   - security         安全: 数据泄露/合规审计/越权/漏洞
 *
 * 输入: SOW 原文 + 已识别的 REQ 列表 (NFR classifier 提供维度信息, 可作加权)
 * 输出: RiskIdentification
 *   - risks: List<RiskEntry>
 *   - scoreMap: Map<category, score> (各类风险打分, 0-10)
 *
 * 算法:
 *   1) 对每一类, 用关键词 regex 在 SOW 文本里扫描, 每命中 +1 分
 *   2) 阈值 ≥ 2 分 → 加入 risks 列表, 推荐 PMI 标准缓解措施
 *   3) NFR classifier 命中维度加权: 例如 primaryDimension=security 时 security 类 +2, performance 时 technical +1
 *   4) 同时附 PMI 标准缓解 (mitigation) 建议
 */
@Slf4j
public class SowRiskIdentifier {

    /** 风险类别枚举 */
    public enum Category {
        technical, schedule, cost, external, quality, security
    }

    /** 6 类关键词表 (中英文 + 行业常见写法) */
    private static final String[][] RISK_RULES = new String[][]{
            {"technical", "技术风险|新技术|架构复杂|复杂度高|技术难点|技术债|接口复杂|性能瓶颈|兼容性|性能压测|性能测试|新技术栈|新框架|微服务化|容器化|算法复杂度|migration|legacy|技术调研"},
            {"schedule",  "工期紧|进度紧|时间紧迫|deadline|里程碑多|并行任务|关键路径|资源冲突|人力不足|排期|倒排|crunch|加班|延期|进度滞后"},
            {"cost",      "预算超支|成本超支|汇率波动|人力溢价|外包费用|license|许可证费|采购|budget overrun|over budget|license cost"},
            {"external",  "供应商|外包|第三方|法规变化|标准变更|监管要求|政策变化|不可抗力|市场变化|vendor|third[- ]party|regulator|regulation|policy change"},
            {"quality",   "缺陷率|可维护性差|技术债|代码质量|测试覆盖|技术债务|bug|defect|rework|code quality|tech debt|maintainability"},
            {"security",  "数据泄露|合规审计|越权|漏洞|攻击|合规风险|安全审计|等保|GDPR|HIPAA|PCI|breach|vulnerability|exploit|compliance audit"}
    };

    /** PMI 标准缓解模板 (按类别) */
    private static final Map<Category, String> MITIGATION = new LinkedHashMap<>();
    static {
        MITIGATION.put(Category.technical, "采用原型验证 + Spike 预研; 设立技术评审 Gate; 关键技术点双人备份");
        MITIGATION.put(Category.schedule,  "关键路径资源倾斜; 设定 Stage Gate 锁定; 提前 2 周启动风险缓冲");
        MITIGATION.put(Category.cost,      "预算分阶段审批 + EVM 监控; 汇率波动预留 5% 缓冲; 提前锁定 license");
        MITIGATION.put(Category.external,  "主备供应商策略; 法规跟踪周会; 合同条款含变更触发条款");
        MITIGATION.put(Category.quality,   "测试左移 + 自动化覆盖率 > 60%; Code Review 必走; 缺陷密度阈值卡点");
        MITIGATION.put(Category.security,  "安全左移 + DevSecOps; 等保/GDPR 早期对标; 红蓝对抗演练季度化");
    }

    public record RiskEntry(
            String id,              // RISK-001
            Category category,
            String title,
            String evidence,        // 原文引用 (≤ 80 字符)
            String mitigation,      // PMI 缓解措施
            int score              // 该风险加权打分
    ) {}

    public record RiskIdentification(
            List<RiskEntry> risks,
            Map<Category, Integer> scoreMap
    ) {}

    public RiskIdentification identify(String sowText,
                                       List<SowRequirementExtractor.ExtractedRequirement> reqs,
                                       List<SowNfrClassifier.ClassifiedRequirement> nfrs) {
        if (sowText == null) sowText = "";
        String lower = sowText.toLowerCase();

        // (1) 基础打分
        Map<Category, Integer> scoreMap = new EnumMap<>(Category.class);
        for (Category c : Category.values()) scoreMap.put(c, 0);

        for (String[] rule : RISK_RULES) {
            Category cat = Category.valueOf(rule[0]);
            String regex = rule[1];
            int count = countMatches(lower, regex);
            scoreMap.put(cat, scoreMap.get(cat) + count);
        }

        // (2) NFR 维度加权
        if (nfrs != null) {
            for (var n : nfrs) {
                if (n.primaryDimension() == null) continue;
                switch (n.primaryDimension()) {
                    case security -> scoreMap.merge(Category.security, 2, Integer::sum);
                    case performance -> scoreMap.merge(Category.technical, 1, Integer::sum);
                    case availability -> scoreMap.merge(Category.technical, 1, Integer::sum);
                    case compliance -> scoreMap.merge(Category.security, 1, Integer::sum);
                    case dataIntegrity -> scoreMap.merge(Category.quality, 1, Integer::sum);
                    case scalability -> scoreMap.merge(Category.technical, 1, Integer::sum);
                    default -> { /* 其他维度不加分 */ }
                }
            }
        }

        // (3) 生成 risks (阈值 ≥ 2, 按分数排序)
        List<RiskEntry> risks = new ArrayList<>();
        int id = 1;
        List<Map.Entry<Category, Integer>> sorted = new ArrayList<>(scoreMap.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        for (var entry : sorted) {
            if (entry.getValue() < 2) continue;
            Category cat = entry.getKey();
            String title = buildRiskTitle(cat, sowText);
            String evidence = extractEvidence(sowText, RISK_RULES[idx(cat)][1]);
            String mitigation = MITIGATION.getOrDefault(cat, "监控 + 例行汇报");
            risks.add(new RiskEntry(
                    String.format("RISK-%03d", id++),
                    cat,
                    title,
                    evidence,
                    mitigation,
                    entry.getValue()
            ));
        }

        log.info("[SowRiskIdentifier] identified {} risks (scoreMap={})", risks.size(), scoreMap);
        return new RiskIdentification(risks, scoreMap);
    }

    private int idx(Category cat) {
        for (int i = 0; i < RISK_RULES.length; i++) {
            if (RISK_RULES[i][0].equals(cat.name())) return i;
        }
        return 0;
    }

    private int countMatches(String text, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text);
        int c = 0;
        while (m.find()) c++;
        return c;
    }

    private String buildRiskTitle(Category cat, String sowText) {
        return switch (cat) {
            case technical -> "技术风险: 新技术栈/接口复杂度/性能瓶颈";
            case schedule -> "进度风险: 工期紧/并行任务多/关键路径";
            case cost -> "成本风险: 预算超支/汇率波动/许可证";
            case external -> "外部风险: 供应商依赖/法规变更/第���方对接";
            case quality -> "质量风险: 缺陷率/技术债/可维护性";
            case security -> "安全风险: 数据泄露/合规审计/越权";
        };
    }

    private String extractEvidence(String sowText, String regex) {
        try {
            Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(sowText);
            if (m.find()) {
                int start = Math.max(0, m.start() - 10);
                int end = Math.min(sowText.length(), m.end() + 30);
                String e = sowText.substring(start, end).replaceAll("\\s+", " ").trim();
                if (e.length() > 80) e = e.substring(0, 77) + "...";
                return e;
            }
        } catch (Exception ignored) {}
        return "";
    }
}