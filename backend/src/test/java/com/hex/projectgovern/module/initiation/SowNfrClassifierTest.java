package com.hex.projectgovern.module.initiation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V4.27 SOW Skill — 9 维 NFR 分类器单元测试
 */
class SowNfrClassifierTest {

    private final SowNfrClassifier classifier = new SowNfrClassifier();

    @Test
    @DisplayName("性能 REQ → primaryDimension = performance")
    void performance_primary() {
        var req = new SowRequirementExtractor.ExtractedRequirement(
                "REQ-001", "clause", "接口性能",
                "接口响应时间不得大于 300ms, P99 < 500ms",
                "Must", "non-functional",
                "接口响应时间不得大于 300ms (P99)");
        var c = classifier.classify(req);
        assertEquals(SowNfrClassifier.Dimension.performance, c.primaryDimension());
        assertTrue(c.dimensions().get(SowNfrClassifier.Dimension.performance));
    }

    @Test
    @DisplayName("安全/脱敏 REQ → primaryDimension = security")
    void security_primary() {
        var req = new SowRequirementExtractor.ExtractedRequirement(
                "REQ-002", "clause", "数据脱敏",
                "通话内容必须支持自动脱敏 (身份证/手机号/银行卡)",
                "Must", "non-functional",
                "通话内容必须支持自动脱敏 (身份证/手机号/银行卡)");
        var c = classifier.classify(req);
        assertEquals(SowNfrClassifier.Dimension.security, c.primaryDimension());
    }

    @Test
    @DisplayName("SLA 可用性 REQ → primaryDimension = availability")
    void availability_primary() {
        var req = new SowRequirementExtractor.ExtractedRequirement(
                "REQ-003", "clause", "SLA 99.95",
                "系统可用性应当达到 99.95%, RTO < 30min, RPO < 5min",
                "Must", "non-functional",
                "系统可用性应当达到 99.95%");
        var c = classifier.classify(req);
        assertEquals(SowNfrClassifier.Dimension.availability, c.primaryDimension());
    }

    @Test
    @DisplayName("等保合规 REQ → primaryDimension = compliance (不被 maintainability 抢权)")
    void compliance_strongKwSuppressesMaintainability() {
        var req = new SowRequirementExtractor.ExtractedRequirement(
                "REQ-004", "clause", "等保三级",
                "所有数据存储必须满足等保三级要求, 通过外部审计",
                "Must", "non-functional",
                "所有数据存储必须满足等保三级要求");
        var c = classifier.classify(req);
        assertEquals(SowNfrClassifier.Dimension.compliance, c.primaryDimension());
    }

    @Test
    @DisplayName("事务一致性 REQ → primaryDimension = dataIntegrity")
    void dataIntegrity_primary() {
        var req = new SowRequirementExtractor.ExtractedRequirement(
                "REQ-005", "clause", "事务",
                "账户余额扣减必须使用 ACID 事务, 幂等",
                "Must", "functional",
                "账户余额扣减必须使用 ACID 事务");
        var c = classifier.classify(req);
        assertEquals(SowNfrClassifier.Dimension.dataIntegrity, c.primaryDimension());
    }

    @Test
    @DisplayName("纯功能 REQ → primaryDimension = null (9 维全 false)")
    void pureFunctional_noNfrPrimary() {
        var req = new SowRequirementExtractor.ExtractedRequirement(
                "REQ-006", "clause", "账户开户",
                "系统必须支持账户开户流程",
                "Must", "functional",
                "系统必须支持账户开户流程");
        var c = classifier.classify(req);
        assertNull(c.primaryDimension(), "纯功能 REQ 应当无 primaryDimension");
        assertTrue(c.dimensions().values().stream().noneMatch(Boolean.TRUE::equals),
                "纯功能 REQ 9 维应当全 false");
    }

    @Test
    @DisplayName("多维 REQ (接口 + 安全 + 高并发) → primary 取权重最高 performance")
    void multiDim_pickHighestWeight() {
        var req = new SowRequirementExtractor.ExtractedRequirement(
                "REQ-007", "clause", "API 网关",
                "API 接口支持 OAuth2 鉴权, P99 < 100ms, 高并发 1 万 QPS",
                "Must", "non-functional",
                "API 接口支持 OAuth2 鉴权, P99 < 100ms, 高并发 1 万 QPS");
        var c = classifier.classify(req);
        // performance(10) + security(9) + interoperability(5) 都命中
        assertEquals(SowNfrClassifier.Dimension.performance, c.primaryDimension());
        assertTrue(c.dimensions().get(SowNfrClassifier.Dimension.performance));
        assertTrue(c.dimensions().get(SowNfrClassifier.Dimension.security));
    }

    @Test
    @DisplayName("批量分类 classifyAll → 与单条 classify 等价")
    void classifyAll_matchesClassify() {
        var reqs = List.of(
                new SowRequirementExtractor.ExtractedRequirement("REQ-A", "clause", "X", "性能 P99 < 200ms", "Must", "non-functional", "性能 P99 < 200ms"),
                new SowRequirementExtractor.ExtractedRequirement("REQ-B", "clause", "Y", "账户开户", "Must", "functional", "账户开户")
        );
        var all = classifier.classifyAll(reqs);
        assertEquals(2, all.size());
        assertEquals(SowNfrClassifier.Dimension.performance, all.get(0).primaryDimension());
        assertNull(all.get(1).primaryDimension());
    }

    @Test
    @DisplayName("toJson 序列化 9 维 bool 字典 + primaryDimension (string or null)")
    void toJson_serializesAllNineDims() {
        var req = new SowRequirementExtractor.ExtractedRequirement(
                "REQ-008", "clause", "审计日志",
                "须提供完整审计日志, 等保三级, 保留 180 天",
                "Must", "non-functional", "");
        var json = classifier.classify(req).toJson();
        var dims = (java.util.Map<?, ?>) json.get("dimensions");
        // 9 维都应出现 (即使 false)
        for (SowNfrClassifier.Dimension d : SowNfrClassifier.Dimension.values()) {
            assertTrue(dims.containsKey(d.name()), "missing dim: " + d.name());
        }
        // 等保 + 审计 → primary = compliance (被强关键词 override)
        assertEquals("compliance", json.get("primaryDimension"));
    }
}
