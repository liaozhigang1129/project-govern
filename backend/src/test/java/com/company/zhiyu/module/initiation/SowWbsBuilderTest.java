package com.company.zhiyu.module.initiation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V4.27 SOW Skill — WBS 构建器单元测试
 */
class SowWbsBuilderTest {

    private final SowWbsBuilder builder = new SowWbsBuilder();

    @Test
    @DisplayName("★ 硬骨架 7 阶段 WBS-1..WBS-7 名字镜像 lifecyclePhases")
    void hardSkeleton_pmi7Phases() {
        var tree = builder.build(LocalDate.of(2026, 1, 1), List.of(), List.of());
        assertEquals(7, tree.phasesCount());
        assertEquals(7, tree.roots().size());
        // 严格顺序 + 严格名字
        assertEquals("WBS-1", tree.roots().get(0).wbsId());
        assertEquals("项目立项 (Initiation)", tree.roots().get(0).name());
        assertEquals("WBS-2", tree.roots().get(1).wbsId());
        assertEquals("需求分析 (Requirements)", tree.roots().get(1).name());
        assertEquals("WBS-3", tree.roots().get(2).wbsId());
        assertEquals("系统设计 (System Design)", tree.roots().get(2).name());
        assertEquals("WBS-4", tree.roots().get(3).wbsId());
        assertEquals("开发 (Development)", tree.roots().get(3).name());
        assertEquals("WBS-5", tree.roots().get(4).wbsId());
        assertEquals("测试 (Testing)", tree.roots().get(4).name());
        assertEquals("WBS-6", tree.roots().get(5).wbsId());
        assertEquals("投产 (Deployment)", tree.roots().get(5).name());
        assertEquals("WBS-7", tree.roots().get(6).wbsId());
        assertEquals("验收 (Acceptance)", tree.roots().get(6).name());
    }

    @Test
    @DisplayName("WBS-1/5/6 不允许 L4 (enforce 截止于 L3)")
    void nonL4Phases_stopAtL3() {
        // 构造大量 REQ, 强制每个 L1 都产生 L4
        var reqs = List.of(
                req("REQ-001", "账户开户 (开发 实现)", "Must"),
                req("REQ-002", "接口对接 (联调)", "Must"),
                req("REQ-003", "测试用例编写", "Should"),
                req("REQ-004", "上线部署", "Must"),
                req("REQ-005", "用户验收", "Must"),
                req("REQ-006", "业务需求调研", "Must"),
                req("REQ-007", "概要设计", "Must"),
                req("REQ-008", "UAT 培训", "Should")
        );
        var tree = builder.build(LocalDate.now(), reqs, List.of());
        // 验证 WBS-1 下不应有 L4
        long wbs1L4 = tree.flat().stream().filter(n -> "WBS-1".equals(n.wbs1Id()) && n.isL4()).count();
        assertEquals(0, wbs1L4, "WBS-1 不应有 L4");
        // WBS-5 不应有 L4
        long wbs5L4 = tree.flat().stream().filter(n -> "WBS-5".equals(n.wbs1Id()) && n.isL4()).count();
        assertEquals(0, wbs5L4, "WBS-5 不应有 L4");
        // WBS-6 不应有 L4
        long wbs6L4 = tree.flat().stream().filter(n -> "WBS-6".equals(n.wbs1Id()) && n.isL4()).count();
        assertEquals(0, wbs6L4, "WBS-6 不应有 L4");
        // 而 WBS-2/3/4/7 应当有 L4
        assertTrue(tree.flat().stream().anyMatch(n -> "WBS-2".equals(n.wbs1Id()) && n.isL4()), "WBS-2 应有 L4");
        assertTrue(tree.flat().stream().anyMatch(n -> "WBS-3".equals(n.wbs1Id()) && n.isL4()), "WBS-3 应有 L4");
        assertTrue(tree.flat().stream().anyMatch(n -> "WBS-4".equals(n.wbs1Id()) && n.isL4()), "WBS-4 应有 L4");
        assertTrue(tree.flat().stream().anyMatch(n -> "WBS-7".equals(n.wbs1Id()) && n.isL4()), "WBS-7 应有 L4");
    }

    @Test
    @DisplayName("REQ 路由 — '接口对接' → WBS-4 (开发)")
    void reqRouting_development() {
        var reqs = List.of(req("REQ-001", "接口对接���商银行", "Must"));
        var tree = builder.build(LocalDate.now(), reqs, List.of());
        var node = tree.flat().stream()
                .filter(n -> n.level() == 3 && n.requirementIds().contains("REQ-001"))
                .findFirst().orElseThrow();
        assertEquals("WBS-4", node.wbs1Id());
    }

    @Test
    @DisplayName("REQ 路由 — '用户验收' → WBS-7 (验收)")
    void reqRouting_acceptance() {
        var reqs = List.of(req("REQ-002", "用户验收 UAT", "Must"));
        var tree = builder.build(LocalDate.now(), reqs, List.of());
        var node = tree.flat().stream()
                .filter(n -> n.level() == 3 && n.requirementIds().contains("REQ-002"))
                .findFirst().orElseThrow();
        assertEquals("WBS-7", node.wbs1Id());
    }

    @Test
    @DisplayName("REQ 路由 — '测试用例' → WBS-5 (测试)")
    void reqRouting_testing() {
        var reqs = List.of(req("REQ-003", "测试用例设计", "Should"));
        var tree = builder.build(LocalDate.now(), reqs, List.of());
        var node = tree.flat().stream()
                .filter(n -> n.level() == 3 && n.requirementIds().contains("REQ-003"))
                .findFirst().orElseThrow();
        assertEquals("WBS-5", node.wbs1Id());
    }

    @Test
    @DisplayName("REQ 路由 — '上线部署' → WBS-6 (投产)")
    void reqRouting_deployment() {
        var reqs = List.of(req("REQ-004", "上线部署到生产", "Must"));
        var tree = builder.build(LocalDate.now(), reqs, List.of());
        var node = tree.flat().stream()
                .filter(n -> n.level() == 3 && n.requirementIds().contains("REQ-004"))
                .findFirst().orElseThrow();
        assertEquals("WBS-6", node.wbs1Id());
    }

    @Test
    @DisplayName("REQ 路由 — '业务需求调研' → WBS-2 (需求分析)")
    void reqRouting_requirements() {
        var reqs = List.of(req("REQ-005", "业务需求调研", "Must"));
        var tree = builder.build(LocalDate.now(), reqs, List.of());
        var node = tree.flat().stream()
                .filter(n -> n.level() == 3 && n.requirementIds().contains("REQ-005"))
                .findFirst().orElseThrow();
        assertEquals("WBS-2", node.wbs1Id());
    }

    @Test
    @DisplayName("MoSCoW 排序 — Must > Should > Could > Won't 优先进入 WBS")
    void moscowSort_mustFirst() {
        var reqs = List.of(
                req("REQ-W", "接口对接", "Won't"),
                req("REQ-C", "接口对接", "Could"),
                req("REQ-S", "接口对接", "Should"),
                req("REQ-M", "接口对接", "Must")
        );
        var tree = builder.build(LocalDate.now(), reqs, List.of());
        var node = tree.flat().stream()
                .filter(n -> n.level() == 3 && n.wbs1Id().equals("WBS-4"))
                .toList();
        // 4 个 REQ 都该进入 WBS-4 (接口对接), 排序按 MoSCoW
        assertEquals(4, node.size());
        // 第一条应该是 Must
        assertTrue(node.get(0).requirementIds().contains("REQ-M"));
    }

    @Test
    @DisplayName("Risk 挂载 — security 风险挂到 WBS-3 (设计), technical 风险挂到 WBS-4 (开发)")
    void riskAttachment_goesToPhase() {
        var reqs = List.of(req("REQ-001", "接口对接", "Must"));
        var risks = List.of(
                new SowRiskIdentifier.RiskEntry("RISK-001", SowRiskIdentifier.Category.security, "安全", "数据泄露", "DevSecOps", 5),
                new SowRiskIdentifier.RiskEntry("RISK-002", SowRiskIdentifier.Category.technical, "技术", "新技术栈", "Spike 预研", 5)
        );
        var tree = builder.build(LocalDate.now(), reqs, risks);
        // RISK-001 (security) → WBS-3
        var secRisk = tree.flat().stream()
                .filter(n -> n.riskIds().contains("RISK-001"))
                .findFirst().orElseThrow();
        assertEquals("WBS-3", secRisk.wbs1Id());
        // RISK-002 (technical) → WBS-4
        var techRisk = tree.flat().stream()
                .filter(n -> n.riskIds().contains("RISK-002"))
                .findFirst().orElseThrow();
        assertEquals("WBS-4", techRisk.wbs1Id());
    }

    @Test
    @DisplayName("工期自动分配 — 项目总工期 = 7 个阶段之和")
    void projectDuration_isSumOfPhases() {
        var tree = builder.build(LocalDate.of(2026, 1, 1), List.of(), List.of());
        int sumDays = tree.roots().stream().mapToInt(SowWbsBuilder.WbsNode::estimatedDays).sum();
        assertEquals(7 + 21 + 21 + 60 + 21 + 14 + 14, sumDays);
        // 起止日期衔接
        LocalDate cursor = LocalDate.of(2026, 1, 1);
        for (var n : tree.roots()) {
            assertEquals(cursor, n.plannedStart());
            cursor = n.plannedEnd().plusDays(1);
        }
    }

    private SowRequirementExtractor.ExtractedRequirement req(String id, String title, String priority) {
        return new SowRequirementExtractor.ExtractedRequirement(
                id, "clause", title, title, priority, "functional", title);
    }
}