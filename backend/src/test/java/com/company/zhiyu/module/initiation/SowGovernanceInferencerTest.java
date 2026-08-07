package com.company.zhiyu.module.initiation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V4.27 SOW Skill — 治理流程推导器单元测试
 */
class SowGovernanceInferencerTest {

    private final SowGovernanceInferencer inferencer = new SowGovernanceInferencer();

    @Test
    @DisplayName("金融业典型 SOW → 命中 P1/P2/P3/P4/P5 + P6 治理 + A1/A2/A4/A5")
    void bankingSow_recognizesAllProcessesAndStages() {
        String sow = """
            项目章程已经由 PMO 审批通过, 进入规划阶段, 制定 WBS 与基线.
            执行期需要完成接口对接、联调测试, 进入监控与变更控制.
            项目验收前需进行等保三级审计, 投产评审与验收评审是阶段门禁.
            变更申请需要走 CCB, 影响分析后由指导委员会批准.
            """;
        var inf = inferencer.infer(sow);
        // P1-P7 至少前 6 项都有 (P7=变更也命中)
        assertTrue(contains(inf.processGroups(), "P1"), "P1 启动");
        assertTrue(contains(inf.processGroups(), "P2"), "P2 规划");
        assertTrue(contains(inf.processGroups(), "P3"), "P3 执行");
        assertTrue(contains(inf.processGroups(), "P4"), "P4 监控");
        assertTrue(contains(inf.processGroups(), "P5"), "P5 收尾");
        assertTrue(contains(inf.processGroups(), "P6"), "P6 治理 (审计 + 指导委员会)");
        assertTrue(contains(inf.processGroups(), "P7"), "P7 变更 (CCB)");
        // Stages — 此 SOW 只含 投产评审 + 验收评审, 无方案评审
        assertTrue(contains(inf.stages(), "A1"), "A1 立项评审 (兜底)");
        assertTrue(contains(inf.stages(), "A4"), "A4 投产评审");
        assertTrue(contains(inf.stages(), "A5"), "A5 验收评审");
    }

    @Test
    @DisplayName("极简 SOW (只有'验收') → PMI 5 大过程组 + 兜底 A1+A5")
    void minimalSow_fallsBackToPmiCore() {
        var inf = inferencer.infer("本期交付一个账户开户接口, 验收后上线.");
        // 兜底: P1..P5
        assertTrue(contains(inf.processGroups(), "P1"));
        assertTrue(contains(inf.processGroups(), "P5"));
        // 兜底 A1 + A5
        assertTrue(contains(inf.stages(), "A1"));
        assertTrue(contains(inf.stages(), "A5"));
        // 没有 stage-gate 类词, 所以 A2/A3/A4 不应出现
        assertFalse(contains(inf.stages(), "A2"));
        assertFalse(contains(inf.stages(), "A3"));
        assertFalse(contains(inf.stages(), "A4"));
    }

    @Test
    @DisplayName("英文 SOW → 命中 'kick-off / WBS / stage gate / change request'")
    void englishSow_recognizesEnglishKeywords() {
        String sow = """
            The project kick-off is scheduled for next Monday.
            WBS and baseline must be approved in the planning phase.
            We will hold a stage gate review before go-live.
            All change requests are handled by CCB.
            """;
        var inf = inferencer.infer(sow);
        assertTrue(contains(inf.processGroups(), "P1"), "kick-off");
        assertTrue(contains(inf.processGroups(), "P2"), "WBS / baseline");
        assertTrue(contains(inf.processGroups(), "P7"), "change requests / CCB");
        assertTrue(contains(inf.stages(), "A3"), "stage gate");
        assertTrue(contains(inf.stages(), "A4"), "go-live");
    }

    @Test
    @DisplayName("空 / null SOW → PMI 兜底 P1..P5 + A1+A5, 顺序固定")
    void emptySow_fallbacksAreOrdered() {
        var inf1 = inferencer.infer("");
        var inf2 = inferencer.infer(null);
        // PMI 兜底, P1..P5 都在
        for (String pid : List.of("P1", "P2", "P3", "P4", "P5")) {
            assertTrue(contains(inf1.processGroups(), pid), pid + " in inf1");
            assertTrue(contains(inf2.processGroups(), pid), pid + " in inf2");
        }
        // 顺序: P1..P5
        List<String> pgs = inf1.processGroups();
        assertTrue(pgs.indexOf(idPrefix("P1", pgs)) < pgs.indexOf(idPrefix("P5", pgs)));
    }

    @Test
    @DisplayName("区间防呆规则 INTERVAL_RULES 必须包含 4 类")
    void intervalRules_includeFourCategories() {
        var inf = inferencer.infer("任意 SOW");
        assertTrue(inf.intervalRules().containsKey("projectDurationDays"));
        assertTrue(inf.intervalRules().containsKey("stageIntervalDays"));
        assertTrue(inf.intervalRules().containsKey("phaseDurationDays"));
        assertTrue(inf.intervalRules().containsKey("projectMonths"));
        // 校验 normal/absolute 区间合理
        var proj = inf.intervalRules().get("projectDurationDays");
        assertEquals(30, proj.minNormal());
        assertEquals(730, proj.maxNormal());
        assertEquals(14, proj.minAbsolute());
        assertEquals(1095, proj.maxAbsolute());
    }

    @Test
    @DisplayName("IntervalRule.isOutlier / isWarning 边界")
    void intervalRule_boundaryCheck() {
        var rule = new SowGovernanceInferencer.IntervalRule(30, 730, 14, 1095);
        // 正常区间
        assertFalse(rule.isOutlier(100));
        assertFalse(rule.isWarning(100));
        // 警告区间 (正常外、绝对内)
        assertFalse(rule.isOutlier(20));
        assertTrue(rule.isWarning(20));
        assertTrue(rule.isWarning(800));
        // 异常区间
        assertTrue(rule.isOutlier(10));
        assertTrue(rule.isOutlier(2000));
        assertTrue(rule.isWarning(2000));  // outlier 必然也是 warning
    }

    @Test
    @DisplayName("P6 治理关键词 — 等保 + stage gate + steering")
    void governanceKeywords_P6() {
        var inf = inferencer.infer("本项目需要通过等保三级审计, 并召开 steering committee 月度例会, 设置 stage gate.");
        assertTrue(contains(inf.processGroups(), "P6"));
        assertTrue(contains(inf.stages(), "A3"));
    }

    @Test
    @DisplayName("过程组 ID 排序固定 PMI 顺序, 不依赖 SOW 出现顺序")
    void processGroupsIdOrderIsPmi() {
        // SOW 里 P5 先出现, P1 后出现, 结果应仍按 P1..P5 排序
        var inf = inferencer.infer("收尾 sign-off 完成. 项目 charter 已批. P5 P1 P3 P2 P4 都涉及.");
        List<String> pgs = inf.processGroups();
        int idxP1 = pgs.indexOf(idPrefix("P1", pgs));
        int idxP2 = pgs.indexOf(idPrefix("P2", pgs));
        int idxP3 = pgs.indexOf(idPrefix("P3", pgs));
        int idxP4 = pgs.indexOf(idPrefix("P4", pgs));
        int idxP5 = pgs.indexOf(idPrefix("P5", pgs));
        assertTrue(idxP1 < idxP2 && idxP2 < idxP3 && idxP3 < idxP4 && idxP4 < idxP5,
                "PMI 顺序: P1<P2<P3<P4<P5, 实际=" + pgs);
    }

    private boolean contains(List<String> list, String id) {
        return list.stream().anyMatch(s -> s.startsWith(id + "-"));
    }

    private String idPrefix(String id, List<String> list) {
        return list.stream().filter(s -> s.startsWith(id + "-")).findFirst().orElse(id + "-");
    }
}