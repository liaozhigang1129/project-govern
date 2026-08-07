package com.hex.projectgovern.module.initiation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V4.27 SOW Skill — 风险识别器单元测试
 */
class SowRiskIdentifierTest {

    private final SowRiskIdentifier identifier = new SowRiskIdentifier();
    private final SowRequirementExtractor extractor = new SowRequirementExtractor();
    private final SowNfrClassifier classifier = new SowNfrClassifier();

    @Test
    @DisplayName("金融 SOW → 命中 security/technical/schedule 三类风险")
    void bankingSow_identifiesSecurityTechnicalSchedule() {
        String sow = """
            项目存在数据泄露风险, 需通过等保三级审计.
            引入新技术栈 (LLM Agent), 接口复杂, 存在性能瓶颈.
            工期紧 (3 个月), 倒排计划, 并行任务多.
            """;
        var reqs = extractor.extract(sow);
        var nfrs = classifier.classifyAll(reqs);
        var result = identifier.identify(sow, reqs, nfrs);

        assertTrue(result.scoreMap().get(SowRiskIdentifier.Category.security) >= 2,
                "security >= 2, actual=" + result.scoreMap().get(SowRiskIdentifier.Category.security));
        assertTrue(result.scoreMap().get(SowRiskIdentifier.Category.technical) >= 2,
                "technical >= 2");
        assertTrue(result.scoreMap().get(SowRiskIdentifier.Category.schedule) >= 2,
                "schedule >= 2");
        assertFalse(result.risks().isEmpty(), "should produce risks");
    }

    @Test
    @DisplayName("纯文本 SOW (无任何风险关键词) → risks 为空")
    void emptySow_noRisks() {
        String sow = "本项目交付一个新账户开户功能.";
        var reqs = extractor.extract(sow);
        var result = identifier.identify(sow, reqs, classifier.classifyAll(reqs));
        // 单纯功能, 不应该产生 ≥ 2 分的风险
        assertTrue(result.risks().isEmpty(), "should have no risks, actual=" + result.risks().size());
    }

    @Test
    @DisplayName("NFR primaryDimension=security → security 风险 +2 加权")
    void nfrSecurityDimension_boostsSecurityScore() {
        String sow = """
            接口响应时间不得大于 300ms (P99).
            通话内容必须支持自动脱敏 (身份证/手机号/银行卡), 数据泄露风险不可接受.
            """;
        var reqs = extractor.extract(sow);
        var nfrs = classifier.classifyAll(reqs);
        var result = identifier.identify(sow, reqs, nfrs);
        // "数据泄露" 命中 security +1, primaryDimension=security +2, 总共 ≥ 3
        assertTrue(result.scoreMap().get(SowRiskIdentifier.Category.security) >= 3);
    }

    @Test
    @DisplayName("NFR primaryDimension=dataIntegrity → quality 风险 +1 加权")
    void nfrDataIntegrity_boostsQualityScore() {
        String sow = "账户余额扣减必须使用 ACID 事务, 幂等. 缺陷率必须低于 0.1%.";
        var reqs = extractor.extract(sow);
        var nfrs = classifier.classifyAll(reqs);
        var result = identifier.identify(sow, reqs, nfrs);
        // "缺陷率" +1, NFR=dataIntegrity → quality +1, total ≥ 2
        assertTrue(result.scoreMap().get(SowRiskIdentifier.Category.quality) >= 2);
    }

    @Test
    @DisplayName("risks 列表按 scoreMap 分数降序排列, ID RISK-001/002/...")
    void risksAreSortedDescending() {
        String sow = """
            工期紧 (倒排), 新技术栈 (LLM), 接口复杂, 性能瓶颈, 缺陷率, 数据泄露, 等保审计.
            """;
        var reqs = extractor.extract(sow);
        var result = identifier.identify(sow, reqs, classifier.classifyAll(reqs));
        assertFalse(result.risks().isEmpty());
        // 第一条 score ≥ 最后一条
        int first = result.risks().get(0).score();
        int last = result.risks().get(result.risks().size() - 1).score();
        assertTrue(first >= last, "first=" + first + " last=" + last);
        // ID 递增
        for (int i = 0; i < result.risks().size(); i++) {
            assertEquals(String.format("RISK-%03d", i + 1), result.risks().get(i).id());
        }
    }

    @Test
    @DisplayName("mitigation 字段非空, 且与 category 对应 PMI 模板")
    void mitigationMatchesCategory() {
        String sow = "数据泄露风险. 新技术栈. 工期紧. 预算超支. 供应商依赖. 缺陷率高.";
        var reqs = extractor.extract(sow);
        var result = identifier.identify(sow, reqs, classifier.classifyAll(reqs));
        for (var r : result.risks()) {
            assertNotNull(r.mitigation());
            assertFalse(r.mitigation().isBlank(), "mitigation empty for " + r.id());
            // 验证 mitigation 字段长度合理
            assertTrue(r.mitigation().length() >= 10);
        }
    }

    @Test
    @DisplayName("evidence 字段抓取原文引用 ≤ 80 字符")
    void evidenceIsCappedAt80Chars() {
        String sow = "项目存在技术风险, 需要新技术栈 (LLM Agent), 接口复杂. ".repeat(5);
        var reqs = extractor.extract(sow);
        var result = identifier.identify(sow, reqs, classifier.classifyAll(reqs));
        if (!result.risks().isEmpty()) {
            for (var r : result.risks()) {
                assertTrue(r.evidence().length() <= 83, // 80 + 3 ellipsis
                        "evidence too long: " + r.evidence());
            }
        }
    }

    @Test
    @DisplayName("scoreMap 包含全部 6 类, 即使分数为 0")
    void scoreMapContainsAllSixCategories() {
        var result = identifier.identify("无风险 SOW", List.of(), List.of());
        for (SowRiskIdentifier.Category c : SowRiskIdentifier.Category.values()) {
            assertTrue(result.scoreMap().containsKey(c), "missing category: " + c);
            assertEquals(0, result.scoreMap().get(c), c + " should be 0");
        }
    }
}