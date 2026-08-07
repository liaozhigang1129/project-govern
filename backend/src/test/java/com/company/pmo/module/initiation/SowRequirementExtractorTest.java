package com.company.pmo.module.initiation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V4.27 SOW Skill — SowRequirementExtractor 单元测试
 *
 * 覆盖:
 *  - 中文 SOW (智能客服 NLP 升级) → 8 条以上 REQ
 *  - 英文 SOW (shall/must 触发句)
 *  - 项目符号 SOW
 *  - 短文本/空文本兜底
 *  - MoSCoW 优先级推断
 *  - 三分类 (functional/non-functional/management) 初判
 *  - 标题 2-16 字约束
 */
class SowRequirementExtractorTest {

    private final SowRequirementExtractor extractor = new SowRequirementExtractor();

    @Test
    @DisplayName("中文 SOW (智能客服 NLP) → ≥8 条 REQ, 含 Must/SShould 优先级, 含 management/non-functional/functional")
    void chineseSow_extractsEightPlusReqs() {
        String sow = """
                智能客服 NLP 升级项目 SOW

                1. 项目背景
                客服工单 NLP 准确率仅 71%, 客户投诉率高, 坐席日均处理 120 单。

                2. 业务目标
                NLP 准确率必须提升到 92% 以上; 智能派单覆盖率从 0% 提升至 60%。

                3. 功能范围
                3.1 应当提供工单分类模型, 支持 6 大业务线自动分流。
                3.2 应当实现意图识别, 覆盖 200 个高频意图。
                3.3 须实现坐席小结, 自动生成通话纪要。
                3.4 应当提供语音质检, 支持违规话术识别。
                3.5 须支持 ASR 转写, 准确率 ≥ 95%。

                4. 性能与可用性
                4.1 接口响应时间不得大于 300ms (P99)。
                4.2 系统可用性应当达到 99.95%。
                4.3 必须支持横向扩展, 单实例 TPS ≥ 50。

                5. 数据安全
                5.1 通话内容必须支持自动脱敏 (身份证/手机号/银行卡)。
                5.2 所有数据存储必须满足等保三级要求。
                5.3 须提供完整的审计日志, 保留 180 天。

                6. 项目管理
                6.1 项目周会应当每周召开, 提交周报。
                6.2 变更应当通过 CCB 评审, 重大变更需书面协议。
                6.3 RACI 矩阵必须在项目立项后 5 个工作日内确认。
                6.4 必须提供架构蓝图和数据库 ER 图。

                7. 工期与验收
                工期 6 个月, 不得延期。
                UAT 通过率应当达到 100%, 否则按合同违约金条款执行。
                """;
        List<SowRequirementExtractor.ExtractedRequirement> reqs = extractor.extract(sow);

        System.out.println("Extracted " + reqs.size() + " REQs:");
        reqs.forEach(r -> System.out.printf("  %s [%s/%s] %s%n", r.id(), r.priority(), r.type(), r.title()));

        // 1) 数量 ≥ 8
        assertTrue(reqs.size() >= 8, "should extract ≥8 REQs, got " + reqs.size());

        // 2) ID 格式严格
        assertTrue(reqs.get(0).id().matches("REQ-\\d{3}"), "id format");
        assertEquals("REQ-001", reqs.get(0).id());

        // 3) 至少一条 Must
        assertTrue(reqs.stream().anyMatch(r -> "Must".equals(r.priority())), "should have ≥1 Must");

        // 4) 至少一条 non-functional (性能/可用性/安全)
        assertTrue(reqs.stream().anyMatch(r -> "non-functional".equals(r.type())),
                "should have ≥1 non-functional");

        // 5) 至少一条 management (RACI/周会/CCB)
        assertTrue(reqs.stream().anyMatch(r -> "management".equals(r.type())),
                "should have ≥1 management");

        // 6) 至少一条 functional (业务功能)
        assertTrue(reqs.stream().anyMatch(r -> "functional".equals(r.type())),
                "should have ≥1 functional");

        // 7) title 长度 2-16 字
        for (var r : reqs) {
            assertTrue(r.title().length() >= 2 && r.title().length() <= 16,
                    "title '" + r.title() + "' length " + r.title().length() + " out of [2,16]");
        }

        // 8) originalQuote 非空
        for (var r : reqs) {
            assertNotNull(r.originalQuote());
            assertFalse(r.originalQuote().isBlank());
        }
    }

    @Test
    @DisplayName("英文 SOW (shall/must 触发) → 提取 3 条以上")
    void englishSow_extractsShallSentences() {
        String sow = """
                Customer Service AI System SOW

                1. The vendor shall provide an intent recognition model with at least 200 intents.
                2. The system must support automatic ticket classification.
                3. All PII must be masked before storage.
                4. RACI matrix shall be confirmed within 5 business days after kickoff.
                5. Weekly status reports must be submitted every Friday.
                """;
        List<SowRequirementExtractor.ExtractedRequirement> reqs = extractor.extract(sow);
        System.out.println("English SOW extracted " + reqs.size() + " REQs:");
        reqs.forEach(r -> System.out.printf("  %s [%s] %s%n", r.id(), r.priority(), r.title()));

        assertTrue(reqs.size() >= 3, "should extract ≥3 from English SOW, got " + reqs.size());
        assertTrue(reqs.stream().anyMatch(r -> "Must".equals(r.priority())),
                "English 'must' should yield Must priority");
    }

    @Test
    @DisplayName("项目符号 SOW → 提取 bullet 项")
    void bulletSow_extractsBullets() {
        String sow = """
                项目范围
                - 必须实现账户开户
                - 应当支持指令推送
                - 须实现明细查询
                - 提供对账平台接口适配与联调
                - 实现流水数据转换与映射机制
                - 实现对账差异异常处理服务
                """;
        List<SowRequirementExtractor.ExtractedRequirement> reqs = extractor.extract(sow);
        System.out.println("Bullet SOW extracted " + reqs.size() + " REQs:");
        reqs.forEach(r -> System.out.printf("  %s [%s] %s%n", r.id(), r.priority(), r.title()));
        assertTrue(reqs.size() >= 3, "bullet SOW should yield ≥3");
    }

    @Test
    @DisplayName("空/短文本 → 返回空列表,不抛异常")
    void emptyAndShortText_returnsEmpty() {
        assertEquals(0, extractor.extract(null).size());
        assertEquals(0, extractor.extract("").size());
        assertEquals(0, extractor.extract("   ").size());
        assertEquals(0, extractor.extract("太短").size());
    }

    @Test
    @DisplayName("MoSCoW 推断: 出现'禁止/不得'→ Must;'应当'→ Should;'可以/可选'→ Could;'本期不做'→ Won't")
    void moscowInference_priorityOverride() {
        String sow = """
                1. 禁止明文存储密码。
                2. 应当提供 SSO 单点登录。
                3. 可以选择启用多因素认证 (MFA)。
                4. 本期不做智能推荐功能。
                """;
        List<SowRequirementExtractor.ExtractedRequirement> reqs = extractor.extract(sow);
        System.out.println("MoSCoW test:");
        reqs.forEach(r -> System.out.printf("  %s [%s] %s%n", r.id(), r.priority(), r.title()));
        assertTrue(reqs.stream().anyMatch(r -> "Must".equals(r.priority())),
                "'禁止' should be Must");
        assertTrue(reqs.stream().anyMatch(r -> "Should".equals(r.priority())),
                "'应当' should be Should");
        assertTrue(reqs.stream().anyMatch(r -> "Could".equals(r.priority())),
                "'可以' should be Could");
        assertTrue(reqs.stream().anyMatch(r -> "Won't".equals(r.priority())),
                "'本期不做' should be Won't");
    }

    @Test
    @DisplayName("三分类: RACI/CCB→management;性能/安全→non-functional;开户/查询→functional")
    void typeClassification_threeCategories() {
        String sow = """
                1. 系统必须支持账户开户。
                2. 接口响应时间不得大于 300ms。
                3. RACI 矩阵必须在 5 个工作日内确认。
                4. 须支持对账差异异常处理服务。
                """;
        List<SowRequirementExtractor.ExtractedRequirement> reqs = extractor.extract(sow);
        System.out.println("Type classification:");
        reqs.forEach(r -> System.out.printf("  %s [%s] %s%n", r.id(), r.type(), r.title()));
        assertTrue(reqs.stream().anyMatch(r -> "management".equals(r.type())),
                "RACI line should be management");
        assertTrue(reqs.stream().anyMatch(r -> "non-functional".equals(r.type())),
                "'响应时间' should be non-functional");
        assertTrue(reqs.stream().anyMatch(r -> "functional".equals(r.type())),
                "'账户开户' should be functional");
    }

    @Test
    @DisplayName("去重: 同 title 重复出现 → 只保留 1 条")
    void dedup_sameTitleKeptOnce() {
        String sow = """
                1.1 须实现账户开户。
                1.2 须实现账户开户。
                1.3 须实现账户开户。
                """;
        List<SowRequirementExtractor.ExtractedRequirement> reqs = extractor.extract(sow);
        long count = reqs.stream().filter(r -> r.title().contains("账户开户")).count();
        assertEquals(1, count, "duplicate '账户开户' should be deduped to 1");
    }

    @Test
    @DisplayName("去 marker: SOW 含 [SOW/file.pdf] 等 marker 应被剥掉")
    void normalize_stripsSowMarker() {
        String sow = "[SOW/file.pdf]\n\n1. 系统必须支持账户开户。";
        List<SowRequirementExtractor.ExtractedRequirement> reqs = extractor.extract(sow);
        assertFalse(reqs.isEmpty());
        assertFalse(reqs.get(0).originalQuote().contains("[SOW/"), "marker should be stripped");
    }
}
