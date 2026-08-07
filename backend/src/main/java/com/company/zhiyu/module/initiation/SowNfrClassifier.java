package com.company.zhiyu.module.initiation;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Pattern;

/**
 * V4.27 SOW Skill — 9 维 NFR 分类器 (Step ④)
 *
 * 9 维框架 (PMI / ISO 25010 简化版):
 *   - performance        性能 / 响应时间 / TPS / 并发 / 吞吐量
 *   - security           安全 / 加密 / 脱敏 / 越权 / 攻击
 *   - availability       可用性 / SLA / 故障恢复 / RTO / RPO
 *   - scalability        可扩展 / 横向扩展 / 弹性 / 容量
 *   - usability          易用 / UX / 学习成本 / 可访问性
 *   - maintainability    可维护 / 可观测 / 模块化 / 文档化
 *   - compliance         合规 / 等保 / GDPR / HIPAA / PCI / 审计
 *   - interoperability   互操作 / 集成 / 对接 / 接口 / 协议
 *   - dataIntegrity      数据一致性 / 事务 / 准确性 / 唯一性 / 幂等
 *
 * 输入: SowRequirementExtractor.ExtractedRequirement (含 description + originalQuote)
 * 输出: ClassifiedRequirement
 *   - dimensions: Map<Dimension, Boolean> (9 项 true/false)
 *   - primaryDimension: 权重最高的维度; functional 需求可能全 false, 此时 primaryDimension = null
 *
 * 算法:
 *   1) 把 description + title + originalQuote 拼成"匹配文本" (避免漏掉 title 中的关键词)
 *   2) 对 9 个维度各跑一遍正则, 命中即标 true
 *   3) 计算 primaryDimension: 按 DIM_WEIGHT 加权, 取最高; 全 false → null
 *   4) 不命中任何维度时, dimensions 全 false, 表示该 REQ 是纯功能 (无 NFR 约束)
 */
@Slf4j
public class SowNfrClassifier {

    /** 9 维枚举 */
    public enum Dimension {
        performance, security, availability, scalability,
        usability, maintainability, compliance, interoperability, dataIntegrity
    }

    /** 主维度权重: performance/security/availability 在业务里优先级最高, 排在加权时优先 */
    private static final Map<Dimension, Integer> DIM_WEIGHT = Map.of(
            Dimension.performance,      10,
            Dimension.security,         9,
            Dimension.availability,     8,
            Dimension.dataIntegrity,    7,
            Dimension.compliance,       6,
            Dimension.interoperability, 5,
            Dimension.scalability,      4,
            Dimension.maintainability,  3,
            Dimension.usability,        2
    );

    /** 9 维关键词表: [dimension, regex] — 关键词尽量覆盖中英文 + 行业常见写法 */
    private static final String[][] DIM_RULES = new String[][]{
            {"performance",      "性能|响应时间|时延|延迟|P99|P95|并发|TPS|吞吐量|throughput|latency|response time|QPS|RT\\d"},
            {"security",         "安全|加密|脱敏|越权|注入|XSS|CSRF|鉴权|授权|防泄漏|密钥|凭据|secret|password|encryption|auth"},
            {"availability",     "可用性|SLA|RTO|RPO|容灾|故障转移|双活|多活|主备|7x24|99\\.9|uptime|disaster recovery|高可用"},
            {"scalability",      "可扩展|横向扩展|纵向扩容|弹性伸缩|容量规划|扩容|分布式|集群|scalable|elastic|horizontal scale"},
            {"usability",        "易用|用户体验|UX|UI|无障碍|学习成本|操作便捷|友好|可访问|accessibility|usability"},
            {"maintainability",  "可维护|可观测|可监控|日志|告警|链路追踪|tracing|模块化|文档化|可测试|maintainable|observable"},
            {"compliance",       "等保|GDPR|HIPAA|PCI|SOX|合规|法规|审计|监管|司法链|备案|compliance|audit|regulator"},
            {"interoperability", "对接|集成|接口|协议|API|联调|互通|适配|interoperable|integrate|interface|OpenAPI|REST|SOAP|gRPC"},
            {"dataIntegrity",    "数据一致|事务|幂等|唯一约束|准确性|完整性|精确度|对账|数据质量|transactional|consistent|idempotent|ACID|atomic"}
    };

    /** 反向避免"匹配蔓延": 比如 "审计日志" → 不要同时算 compliance 和 maintainability。
     *  规则: 一旦被 compliance 命中, 且合规关键词是核心词 (审计/等保/GDPR/PCI/HIPAA), 抑制 maintainability。
     */
    private static final List<String> COMPLIANCE_STRONG_KW =
            List.of("等保", "GDPR", "HIPAA", "PCI", "SOX", "合规", "司法链", "监管", "审计");

    /** 反向避免: "性能压测" → 不要算 maintainability (压测) 抢 primary */
    private static final List<String> PERFORMANCE_STRONG_KW =
            List.of("P99", "P95", "TPS", "QPS", "并发", "响应时间", "时延", "throughput", "latency");

    /** 反向避免: "事务一致性" → 不要算 performance */
    private static final List<String> DATA_INTEGRITY_STRONG_KW =
            List.of("事务", "ACID", "幂等", "对账", "唯一约束", "数据一致");

    /** 主入口 */
    public ClassifiedRequirement classify(SowRequirementExtractor.ExtractedRequirement req) {
        if (req == null) return null;

        // 1) 拼匹配文本: description + title + originalQuote (title 短但密度高)
        String text = (req.description() + " " + req.title() + " " + req.originalQuote()).toLowerCase();

        // 2) 9 维打标
        Map<Dimension, Boolean> dims = new EnumMap<>(Dimension.class);
        for (Dimension d : Dimension.values()) dims.put(d, false);
        for (String[] rule : DIM_RULES) {
            Dimension dim = Dimension.valueOf(rule[0]);
            Pattern p = Pattern.compile(rule[1], Pattern.CASE_INSENSITIVE);
            if (p.matcher(text).find()) dims.put(dim, true);
        }

        // 3) 反向抑制: 强 NFR 关键词锁定 primary, 不让弱匹配抢权
        Dimension primary = computePrimary(dims, text);

        return new ClassifiedRequirement(req, dims, primary);
    }

    public List<ClassifiedRequirement> classifyAll(List<SowRequirementExtractor.ExtractedRequirement> reqs) {
        List<ClassifiedRequirement> out = new ArrayList<>();
        if (reqs == null) return out;
        for (var r : reqs) out.add(classify(r));
        log.info("[SowNfrClassifier] classified {} requirements", out.size());
        return out;
    }

    /** primary 选择: 强关键词优先, 否则按 DIM_WEIGHT 取最高, 全 false → null */
    private Dimension computePrimary(Map<Dimension, Boolean> dims, String text) {
        // (a) 强关键词 override
        if (dims.get(Dimension.compliance) && hasAny(text, COMPLIANCE_STRONG_KW))
            return Dimension.compliance;
        if (dims.get(Dimension.performance) && hasAny(text, PERFORMANCE_STRONG_KW))
            return Dimension.performance;
        if (dims.get(Dimension.dataIntegrity) && hasAny(text, DATA_INTEGRITY_STRONG_KW))
            return Dimension.dataIntegrity;

        // (b) 按权重选
        Dimension best = null;
        int bestW = -1;
        for (var e : dims.entrySet()) {
            if (!Boolean.TRUE.equals(e.getValue())) continue;
            int w = DIM_WEIGHT.get(e.getKey());
            if (w > bestW) { bestW = w; best = e.getKey(); }
        }
        return best;
    }

    private boolean hasAny(String text, List<String> kws) {
        for (String kw : kws) if (text.contains(kw.toLowerCase())) return true;
        return false;
    }

    /** 分类结果 record */
    public record ClassifiedRequirement(
            SowRequirementExtractor.ExtractedRequirement req,
            Map<Dimension, Boolean> dimensions,
            Dimension primaryDimension
    ) {
        /** 转 Map 方便 Jackson 序列化 */
        public Map<String, Object> toJson() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("req", Map.of("id", req.id()));
            Map<String, Boolean> dimJson = new LinkedHashMap<>();
            for (Dimension d : Dimension.values()) dimJson.put(d.name(), dimensions.get(d));
            out.put("dimensions", dimJson);
            out.put("primaryDimension", primaryDimension == null ? null : primaryDimension.name());
            return out;
        }
    }
}
