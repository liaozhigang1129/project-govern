package com.company.pmo.module.initiation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V4.27 SOW Skill — RTM 构建器单元测试
 */
class SowRtmBuilderTest {

    private final SowRequirementExtractor extractor = new SowRequirementExtractor();
    private final SowNfrClassifier classifier = new SowNfrClassifier();
    private final SowWbsBuilder wbsBuilder = new SowWbsBuilder();
    private final SowRtmBuilder rtmBuilder = new SowRtmBuilder();
    private final SowRiskIdentifier riskIdentifier = new SowRiskIdentifier();

    @Test
    @DisplayName("RTM 行数 = REQ 数 (一对一)")
    void rtmRowCount_matchesRequirementCount() {
        var reqs = extractor.extract("系统必须支持账户开户. 接口响应时间不得大于 300ms.");
        var nfrs = classifier.classifyAll(reqs);
        var wbs = wbsBuilder.build(LocalDate.now(), reqs, List.of());
        var rtm = rtmBuilder.build(reqs, nfrs, wbs, List.of());
        assertEquals(reqs.size(), rtm.size());
    }

    @Test
    @DisplayName("Must → Test, Could → Demonstration, Won't → Inspection")
    void verificationDependsOnPriority() {
        var reqs = List.of(
                req("REQ-A", "账户开户", "Must"),
                req("REQ-B", "智能推荐", "Could"),
                req("REQ-C", "区块链接入", "Won't")
        );
        var wbs = wbsBuilder.build(LocalDate.now(), reqs, List.of());
        var rtm = rtmBuilder.build(reqs, List.of(), wbs, List.of());
        assertEquals("Test", rtm.get(0).verification());
        assertEquals("Demonstration", rtm.get(1).verification());
        assertEquals("Inspection", rtm.get(2).verification());
    }

    @Test
    @DisplayName("NFR REQ → 增加 1 条 TC-{id}-NFR 测试用例")
    void nfrReq_addsExtraNfrTestCase() {
        var reqs = List.of(req("REQ-NFR", "接口响应时间不得大于 300ms", "Must"));
        var nfrs = classifier.classifyAll(reqs);
        var wbs = wbsBuilder.build(LocalDate.now(), reqs, List.of());
        var rtm = rtmBuilder.build(reqs, nfrs, wbs, List.of());
        var row = rtm.get(0);
        assertEquals(2, row.testCases().size());
        assertTrue(row.testCases().stream().anyMatch(t -> t.endsWith("-NFR")));
        assertEquals("performance", row.nfrPrimary());
    }

    @Test
    @DisplayName("纯功能 REQ (无 NFR 维度) → nfrPrimary = null, 仅 1 条 TC")
    void functionalReq_singleTestCase() {
        var reqs = List.of(req("REQ-FN", "账户开户", "Must"));
        var nfrs = classifier.classifyAll(reqs);
        var wbs = wbsBuilder.build(LocalDate.now(), reqs, List.of());
        var rtm = rtmBuilder.build(reqs, nfrs, wbs, List.of());
        assertNull(rtm.get(0).nfrPrimary());
        assertEquals(1, rtm.get(0).testCases().size());
    }

    @Test
    @DisplayName("RTM 行携带 wbsNodeIds (来自 WBS 反查)")
    void rtmRow_carriesWbsNodeIds() {
        var reqs = List.of(req("REQ-001", "接口对接 (联调)", "Must"));
        var nfrs = classifier.classifyAll(reqs);
        var wbs = wbsBuilder.build(LocalDate.now(), reqs, List.of());
        var rtm = rtmBuilder.build(reqs, nfrs, wbs, List.of());
        var row = rtm.get(0);
        assertFalse(row.wbsNodeIds().isEmpty(), "RTM row 应关联 WBS 节点");
        // 至少有一个 WBS-4.* 节点
        assertTrue(row.wbsNodeIds().stream().anyMatch(id -> id.startsWith("WBS-4")),
                "REQ 接口对接 应在 WBS-4 下");
    }

    @Test
    @DisplayName("security NFR REQ → 关联 security 风险")
    void securityReq_attachesSecurityRisks() {
        var reqs = List.of(req("REQ-SEC", "通话内容必须支持自动脱敏 (身份证/手机号/银行卡)", "Must"));
        var nfrs = classifier.classifyAll(reqs);
        var wbs = wbsBuilder.build(LocalDate.now(), reqs, List.of());
        var risks = List.of(
                new SowRiskIdentifier.RiskEntry("RISK-001", SowRiskIdentifier.Category.security,
                        "安全风险", "数据泄露", "DevSecOps", 5)
        );
        var rtm = rtmBuilder.build(reqs, nfrs, wbs, risks);
        assertTrue(rtm.get(0).riskIds().contains("RISK-001"));
    }

    @Test
    @DisplayName("rationale 字段说明 verification 与 NFR 推导依据")
    void rationaleContainsDerivation() {
        var reqs = List.of(req("REQ-A", "账户开户", "Must"));
        var nfrs = classifier.classifyAll(reqs);
        var wbs = wbsBuilder.build(LocalDate.now(), reqs, List.of());
        var rtm = rtmBuilder.build(reqs, nfrs, wbs, List.of());
        var r = rtm.get(0);
        assertTrue(r.rationale().contains("Must"));
        assertTrue(r.rationale().contains("Test"));
    }

    @Test
    @DisplayName("TC id 格式严格 TC-{reqId}-NN, 跨 REQ 计数器递增")
    void tcIdFormatIsStrict() {
        var reqs = List.of(
                req("REQ-001", "X", "Must"),
                req("REQ-002", "Y", "Must")
        );
        var wbs = wbsBuilder.build(LocalDate.now(), reqs, List.of());
        var rtm = rtmBuilder.build(reqs, List.of(), wbs, List.of());
        // TC-REQ-001-01, TC-REQ-002-02
        assertEquals("TC-REQ-001-01", rtm.get(0).testCases().get(0));
        assertEquals("TC-REQ-002-02", rtm.get(1).testCases().get(0));
    }

    private SowRequirementExtractor.ExtractedRequirement req(String id, String title, String priority) {
        return new SowRequirementExtractor.ExtractedRequirement(
                id, "clause", title, title, priority, "functional", title);
    }
}