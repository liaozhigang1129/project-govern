package com.company.pmo.module.initiation;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * V4.27 SOW Skill — WBS 构建器 (Step ⑦ 核心, 1:1 镜像 lifecyclePhases)
 *
 * ★★★ 硬骨架 (PMI 7 阶段, 不允许用户改 L1 名字) ★★★
 *
 *   L1 ID  | 阶段名 (lifecycleName)              | Lifecycle 语义                       | 允许 L4?
 *   --------|-------------------------------------|--------------------------------------|------------
 *   WBS-1   | 项目立项 (Initiation)               | Project Initiation / Charter         | ❌ (L3 截止)
 *   WBS-2   | 需求分析 (Requirements)             | Requirements Analysis                | ✅ (可下钻 L4)
 *   WBS-3   | 系统设计 (System Design)            | System Design                        | ✅ (可下钻 L4)
 *   WBS-4   | 开发 (Development)                  | Development / Construction           | ✅ (可下钻 L4)
 *   WBS-5   | 测试 (Testing)                      | Testing / QA                         | ❌ (L3 截止)
 *   WBS-6   | 投产 (Deployment)                   | Deployment / Go-Live                 | ❌ (L3 截止)
 *   WBS-7   | 验收 (Acceptance)                   | Acceptance / Sign-Off                | ✅ (可下钻 L4)
 *
 * 算法:
 *   1) 先建 7 个 L1, 顺序固定
 *   2) 按工期自动分配每个 L1 的计划起止时间
 *   3) REQ 列表按 priority (Must>Should>Could>Won't) + type (functional>management>non-functional) 排序
 *   4) 把 REQ 投递到对应 L1 (按 type 映射到 PMI 阶段):
 *      - functional + management → WBS-2 (Requirements)
 *      - non-functional 部分 → WBS-2 (需求阶段定义 NFR)
 *      - 跟"接口对接/集成/联调/开发"相关 → WBS-4 (Development)
 *      - 跟"测试"相关 → WBS-5 (Testing)
 *      - 跟"投产/上线"相关 → WBS-6 (Deployment)
 *      - 跟"验收"相关 → WBS-7 (Acceptance)
 *   5) WBS-2/3/4/7 允许 L4, 默认给每个 L3 拆出 2-3 个 L4 工作包 (基于模板 + REQ 内容)
 *   6) WBS-1/5/6 不允许 L4 (enforce: 如果传进来的 hasL4ForL1=true, 也强制转为 L3 截止)
 *
 * 输出: WbsTree (扁平 + 树状)
 *   - flat: List<WbsNode> 按 wbsId 排序
 *   - roots: List<WbsNode> 7 个 L1 根
 *   - phasesCount: 7
 */
@Slf4j
public class SowWbsBuilder {

    /** PMI 7 阶段硬骨架定义 (id, name, days, allowL4) */
    public static final List<WbsSkeleton> PMI_SKELETON = List.of(
            new WbsSkeleton("WBS-1", "项目立项 (Initiation)",    7,  false),
            new WbsSkeleton("WBS-2", "需求分析 (Requirements)",  21, true ),
            new WbsSkeleton("WBS-3", "系统设计 (System Design)", 21, true ),
            new WbsSkeleton("WBS-4", "开发 (Development)",        60, true ),
            new WbsSkeleton("WBS-5", "测试 (Testing)",            21, false),
            new WbsSkeleton("WBS-6", "投产 (Deployment)",         14, false),
            new WbsSkeleton("WBS-7", "验收 (Acceptance)",         14, true )
    );

    /** MoSCoW 排序权重 (越小越靠前) */
    private static final Map<String, Integer> PRIORITY_WEIGHT = Map.of(
            "Must", 1, "Should", 2, "Could", 3, "Won't", 4
    );

    /** WBS 节点 */
    public record WbsNode(
            String wbsId,           // "WBS-1", "WBS-2.1.1", ...
            String parentId,        // null for L1
            String name,            // "需求分析"
            int level,              // 1, 2, 3, 4
            String wbs1Id,          // 所属 L1 (用于跨节点快速检索, 全部节点都填)
            int estimatedDays,
            LocalDate plannedStart,
            LocalDate plannedEnd,
            List<String> requirementIds,    // 关联 REQ ID
            List<String> riskIds,           // 关联 RISK ID
            String description
    ) {
        public boolean isL1() { return level == 1; }
        public boolean isL4() { return level == 4; }
    }

    /** PMI 骨架定义 */
    public record WbsSkeleton(String id, String name, int defaultDays, boolean allowL4) {}

    /** 整棵 WBS 树 */
    public record WbsTree(
            int phasesCount,
            List<WbsNode> flat,
            List<WbsNode> roots
    ) {}

    /** L4 工作包模板 (按 L1 维度) */
    private static final Map<String, List<String>> L4_TEMPLATES = new LinkedHashMap<>();
    static {
        L4_TEMPLATES.put("WBS-2", List.of(
                "业务需求调研与确认",
                "接口需求定义",
                "数据需求定义"
        ));
        L4_TEMPLATES.put("WBS-3", List.of(
                "概要设计",
                "详细设计",
                "数据库设计"
        ));
        L4_TEMPLATES.put("WBS-4", List.of(
                "后端开发",
                "前端开发",
                "联调测试"
        ));
        L4_TEMPLATES.put("WBS-7", List.of(
                "UAT 用户验收",
                "文档与培训",
                "经验教训总结"
        ));
    }

    /** REQ → L1 路由规则 (顺序重要: 测试/部署/验收 优先于通用词) */
    private static final String[][] ROUTE_RULES = new String[][]{
            // [L1-ID, regex] — 更具体的关键词放前面
            {"WBS-6", "投产|部署|上线|发布|cutover|go[- ]live"},
            {"WBS-5", "测试|压测|qa|qc|缺陷|单元测试|集成测试"},
            {"WBS-7", "验收|交接|sign[- ]off|培训|经验|交付"},
            {"WBS-4", "开发|联调|集成|对接|编码|后端|前端|搭建|实现"},
            {"WBS-3", "概要设计|详细设计|架构设计|数据库设计|schema|架构|算法设计"},
            {"WBS-2", "需求|调研|确认|章程|访谈|stakeholder|数据需求|业务规则"}
    };

    public WbsTree build(LocalDate projectStart,
                         List<SowRequirementExtractor.ExtractedRequirement> reqs,
                         List<SowRiskIdentifier.RiskEntry> risks) {
        if (projectStart == null) projectStart = LocalDate.now();
        if (reqs == null) reqs = List.of();
        if (risks == null) risks = List.of();

        List<WbsNode> flat = new ArrayList<>();
        List<WbsNode> roots = new ArrayList<>();
        LocalDate cursor = projectStart;

        // (1) 先建 7 个 L1
        for (WbsSkeleton sk : PMI_SKELETON) {
            LocalDate start = cursor;
            LocalDate end = cursor.plusDays(sk.defaultDays() - 1);
            WbsNode l1 = new WbsNode(
                    sk.id(), null, sk.name(), 1,
                    sk.id(), sk.defaultDays(),
                    start, end,
                    new ArrayList<>(), new ArrayList<>(),
                    sk.name().split(" ")[0] + " 阶段"
            );
            flat.add(l1);
            roots.add(l1);
            cursor = end.plusDays(1);
        }

        // (2) REQ 排序 (Must first + functional first)
        List<SowRequirementExtractor.ExtractedRequirement> sortedReqs = new ArrayList<>(reqs);
        sortedReqs.sort(Comparator
                .comparingInt((SowRequirementExtractor.ExtractedRequirement r) ->
                        PRIORITY_WEIGHT.getOrDefault(r.priority(), 99))
                .thenComparing(r -> "non-functional".equals(r.type()) ? 1 : 0));

        // (3) 把每个 REQ 路由到对应 L1 (按 ROUTE_RULES 第一命中)
        Map<String, List<String>> reqByL1 = new LinkedHashMap<>();
        for (WbsSkeleton sk : PMI_SKELETON) reqByL1.put(sk.id(), new ArrayList<>());
        for (var req : sortedReqs) {
            String route = "WBS-2"; // 默认落入需求分析
            String hay = (req.title() + " " + req.description()).toLowerCase();
            for (String[] rule : ROUTE_RULES) {
                if (java.util.regex.Pattern.compile(rule[1], java.util.regex.Pattern.CASE_INSENSITIVE)
                        .matcher(hay).find()) { route = rule[0]; break; }
            }
            reqByL1.get(route).add(req.id());
        }

        // (4) 为每个 L1 构建 L3 → L4 (允许 L4 的阶段)
        int counter = 1;
        for (WbsNode l1 : roots) {
            List<String> l1Reqs = reqByL1.get(l1.wbsId());
            if (l1Reqs.isEmpty()) continue;
            counter = buildL3L4(l1, l1Reqs, flat, counter, isAllowL4(l1.wbsId()));
        }

        // (5) 风险挂载 (按 RISK category 关联到合适 L1)
        Map<SowRiskIdentifier.Category, String> RISK_TO_L1 = Map.of(
                SowRiskIdentifier.Category.technical, "WBS-4",
                SowRiskIdentifier.Category.schedule,  "WBS-2",
                SowRiskIdentifier.Category.cost,      "WBS-1",
                SowRiskIdentifier.Category.external,  "WBS-2",
                SowRiskIdentifier.Category.quality,   "WBS-5",
                SowRiskIdentifier.Category.security,  "WBS-3"
        );
        for (var risk : risks) {
            String wbs1 = RISK_TO_L1.getOrDefault(risk.category(), "WBS-2");
            // 找到该 L1 下的第一个 L3 节点 (或 L1 本身)
            WbsNode attach = flat.stream()
                    .filter(n -> n.wbs1Id().equals(wbs1) && n.level() >= 3)
                    .findFirst()
                    .orElse(flat.stream().filter(n -> n.wbs1Id().equals(wbs1)).findFirst().orElse(null));
            if (attach != null) {
                flat.replaceAll(n -> n.wbsId().equals(attach.wbsId())
                        ? new WbsNode(n.wbsId(), n.parentId(), n.name(), n.level(), n.wbs1Id(),
                                n.estimatedDays(), n.plannedStart(), n.plannedEnd(),
                                n.requirementIds(),
                                appendUnique(n.riskIds(), risk.id()),
                                n.description())
                        : n);
            }
        }

        log.info("[SowWbsBuilder] built WBS: 7 phases, {} total nodes (start={})", flat.size(), projectStart);
        return new WbsTree(7, flat, roots);
    }

    private boolean isAllowL4(String wbsId) {
        return PMI_SKELETON.stream()
                .filter(s -> s.id().equals(wbsId))
                .findFirst().map(WbsSkeleton::allowL4).orElse(false);
    }

    /** 给 L1 节点生成 L3 工作包 (每个 REQ 一个 L3), 允许 L4 的再分 L4 */
    private int buildL3L4(WbsNode l1, List<String> reqIds, List<WbsNode> flat, int startCounter, boolean allowL4) {
        int counter = startCounter;
        int l3Idx = 1;
        int daysPerReq = Math.max(1, l1.estimatedDays() / Math.max(1, reqIds.size()));
        LocalDate cursor = l1.plannedStart();
        for (String reqId : reqIds) {
            String l3Id = l1.wbsId() + "." + (l3Idx++);
            WbsNode l3 = new WbsNode(
                    l3Id, l1.wbsId(),
                    "工作包 " + reqId,
                    3, l1.wbsId(),
                    daysPerReq,
                    cursor,
                    cursor.plusDays(daysPerReq - 1),
                    List.of(reqId), new ArrayList<>(),
                    "实现 REQ: " + reqId
            );
            flat.add(l3);
            cursor = cursor.plusDays(daysPerReq);

            if (allowL4) {
                List<String> templates = L4_TEMPLATES.getOrDefault(l1.wbsId(), List.of("实现", "验证", "交付"));
                int l4Days = Math.max(1, daysPerReq / templates.size());
                int l4Idx = 1;
                LocalDate sub = l3.plannedStart();
                for (String tpl : templates) {
                    String l4Id = l3Id + "." + (l4Idx++);
                    flat.add(new WbsNode(
                            l4Id, l3Id,
                            tpl, 4, l1.wbsId(),
                            l4Days,
                            sub,
                            sub.plusDays(l4Days - 1),
                            List.of(reqId), new ArrayList<>(),
                            tpl + " (L4 工作包)"
                    ));
                    sub = sub.plusDays(l4Days);
                    counter++;
                }
            }
            counter++;
        }
        return counter;
    }

    private List<String> appendUnique(List<String> list, String item) {
        if (list.contains(item)) return list;
        List<String> out = new ArrayList<>(list);
        out.add(item);
        return out;
    }
}