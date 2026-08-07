package com.company.zhiyu.module.initiation;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * V4.27 SOW Skill — RTM (Requirements Traceability Matrix) 构建器 (Step ⑧)
 *
 * RTM 行 (Requirement-Test Case):
 *   - requirementId     REQ-001
 *   - requirementTitle  "接口响应时间不得大于 300ms"
 *   - priority          Must / Should / Could / Won't
 *   - nfrPrimary        security / performance / null
 *   - testCases         List<String> 测试用例名 (从 REQ 推导)
 *   - wbsNodeIds        关联 WBS 节点 (L3 / L4)
 *   - riskIds           关联风险
 *   - verification      "Test" / "Inspection" / "Demonstration" / "Analysis"
 *
 * 测试用例命名约定: TC-{reqId}-{counter}  e.g. TC-REQ-001-01
 *
 * 算法:
 *   1) 每个 REQ 至少产生 1 个测试用例
 *   2) NFR REQ (primaryDimension != null) → 额外生成 1 个 NFR-specific 用例 (TC-{reqId}-NFR)
 *   3) Must / Should → 选 "Test" (必须执行)
 *      Could → "Demonstration" (可选演示)
 *      Won't → "Inspection" (本期不做)
 *   4) 关联 WBS 节点: 取所有 wbs1Id 对应阶段的 L3/L4 节点
 *   5) 关联风险: 按 category 找 RISK 列表
 */
@Slf4j
public class SowRtmBuilder {

    /** RTM 行 */
    public record RtmRow(
            String requirementId,
            String requirementTitle,
            String priority,
            String type,             // functional / non-functional / management
            String nfrPrimary,       // security / performance / ... / null
            String verification,     // Test / Inspection / Demonstration / Analysis
            List<String> testCases,
            List<String> wbsNodeIds,
            List<String> riskIds,
            String rationale         // 推导理由
    ) {}

    public List<RtmRow> build(
            List<SowRequirementExtractor.ExtractedRequirement> reqs,
            List<SowNfrClassifier.ClassifiedRequirement> nfrs,
            SowWbsBuilder.WbsTree wbs,
            List<SowRiskIdentifier.RiskEntry> risks
    ) {
        if (reqs == null) reqs = List.of();
        if (nfrs == null) nfrs = List.of();
        if (risks == null) risks = List.of();

        // NFR map: reqId → primary
        Map<String, SowNfrClassifier.Dimension> nfrMap = new LinkedHashMap<>();
        for (var n : nfrs) nfrMap.put(n.req().id(), n.primaryDimension());

        // WBS 节点 map: reqId → 关联节点
        Map<String, List<String>> wbsByReq = new LinkedHashMap<>();
        for (var node : wbs.flat()) {
            for (String rid : node.requirementIds()) {
                wbsByReq.computeIfAbsent(rid, k -> new ArrayList<>()).add(node.wbsId());
            }
        }
        // Risk map: by category → list
        Map<SowRiskIdentifier.Category, List<String>> riskByCat = new LinkedHashMap<>();
        for (var r : risks) {
            riskByCat.computeIfAbsent(r.category(), k -> new ArrayList<>()).add(r.id());
        }
        // Risk map: by REQ → list (REQ 在 description 中提到对应关键词)
        // 简化: 把 security risk 关联到所有 security 维度 REQ
        Map<String, List<String>> riskByReq = new LinkedHashMap<>();
        for (var n : nfrs) {
            var p = n.primaryDimension();
            if (p == SowNfrClassifier.Dimension.security) {
                riskByReq.computeIfAbsent(n.req().id(), k -> new ArrayList<>())
                        .addAll(riskByCat.getOrDefault(SowRiskIdentifier.Category.security, List.of()));
            } else if (p == SowNfrClassifier.Dimension.performance || p == SowNfrClassifier.Dimension.availability) {
                riskByReq.computeIfAbsent(n.req().id(), k -> new ArrayList<>())
                        .addAll(riskByCat.getOrDefault(SowRiskIdentifier.Category.technical, List.of()));
            }
        }

        // RTM 行
        List<RtmRow> rows = new ArrayList<>();
        int tcCounter = 1;
        for (var req : reqs) {
            // (a) 测试用例
            List<String> tcs = new ArrayList<>();
            tcs.add(String.format("TC-%s-%02d", req.id(), tcCounter++));
            SowNfrClassifier.Dimension primary = nfrMap.get(req.id());
            if (primary != null) {
                tcs.add(String.format("TC-%s-NFR", req.id()));
            }

            // (b) verification
            String verification = switch (req.priority()) {
                case "Must" -> "Test";
                case "Should" -> "Test";
                case "Could" -> "Demonstration";
                case "Won't" -> "Inspection";
                default -> "Analysis";
            };

            // (c) rationale
            StringBuilder rat = new StringBuilder();
            rat.append("REQ 优先级 ").append(req.priority())
                    .append(" → ").append(verification);
            if (primary != null) {
                rat.append("; NFR ").append(primary.name()).append(" → 增加 NFR 测试用例");
            }

            rows.add(new RtmRow(
                    req.id(),
                    req.title(),
                    req.priority(),
                    req.type(),
                    primary == null ? null : primary.name(),
                    verification,
                    tcs,
                    wbsByReq.getOrDefault(req.id(), List.of()),
                    riskByReq.getOrDefault(req.id(), List.of()),
                    rat.toString()
            ));
        }

        log.info("[SowRtmBuilder] built RTM with {} rows", rows.size());
        return rows;
    }
}