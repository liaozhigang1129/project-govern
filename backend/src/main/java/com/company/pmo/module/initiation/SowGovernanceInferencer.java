package com.company.pmo.module.initiation;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * V4.27 SOW Skill — 治理流程推导器 (Step ⑤)
 *
 * 从 SOW 文本里识别:
 *   - P1-P7 流程域 (process/processGroup 集合, 命名前缀 Process-ProcessGroup-N)
 *   - A1-A5 阶段 (stage/phase, 命名前缀 Process-Stage-N)
 *
 * 7 大流程域 (PMI / PMBOK 第 6 版 49 个过程归纳 + PMBOK 第 7 版 8 大性能域简化):
 *   - P1 启动 (Initiating)         charter / stakeholder / kickoff
 *   - P2 规划 (Planning)            plan / schedule / budget / risk plan / WBS / baseline
 *   - P3 执行 (Executing)           build / develop / implement / integrate / deploy
 *   - P4 监控 (Monitoring & Controlling)  monitor / review / status / EVM / change control
 *   - P5 收尾 (Closing)            acceptance / handover / lessons learned / archive
 *   - P6 治理 (Governance)         compliance / audit / stage-gate / steering committee
 *   - P7 变更 (Change Management)   change request / CCB / impact analysis / baseline change
 *
 * 5 个治理阶段 (Phase / Stage):
 *   - A1 立项评审 (Charter Review)
 *   - A2 方案评审 (Design Review)
 *   - A3 中期评审 (Mid-stage Review)
 *   - A4 投产评审 (Go-Live Review)
 *   - A5 验收评审 (Acceptance Review)
 *
 * 输入: SOW 原文 + 项目元信息 (type/industry/...)
 * 输出: GovernanceInference
 *   - processGroups: List<String> 命名前缀 ["P1-启动", ...], 顺序与 PMI 一致
 *   - stages:        List<String> 命名前缀 ["A1-立项评审", ...]
 *   - intervals:     Map<String, IntervalRule> 关键区间规则, 后续阶段校验用
 *
 * 关键设计:
 *   1) 流程域识别: 顺序按 PMI → set 顺序保证 P1..P7 固定
 *   2) 阶段识别:   按"评审" / "阶段评审" / "gateway" / "gate review" / "gate" 关键词 + 中文等价
 *   3) 区间防呆:   intervals 用于后续 governance-validate 步骤: 项目总时长 / 阶段间隔, 写在这里
 */
@Slf4j
public class SowGovernanceInferencer {

    /** 7 个流程域元数据 (id, 中文名, 触发关键词 regex) */
    private static final String[][] PROCESS_GROUP_RULES = new String[][]{
            {"P1", "启动 (Initiating)",         "立项|章程|启动|stakeholder|kick-off|kick off|charter|项目发起"},
            {"P2", "规划 (Planning)",           "计划|规划|排期|预算|wbs|baseline|基线|进度计划|资源计划|风险管理计划|沟通计划"},
            {"P3", "执行 (Executing)",          "执行|实施|开发|实现|搭建|集成|联调|建设|投产|部署|交付"},
            {"P4", "监控 (M&C)",               "监控|监控与控制|状态报告|EVM|挣值|偏差|风险监测|变更控制|质量保证|qa|qc"},
            {"P5", "收尾 (Closing)",            "收尾|验收|交接|总结|经验教训|归档|正式关闭|sign[- ]off|hand[- ]over"},
            {"P6", "治理 (Governance)",         "治理|合规|审计|门禁|stage[- ]?gate|gateway|steering|指导委员会|监管|监管机构|内控"},
            {"P7", "变更管理 (Change Mgmt)",    "变更申请|变更请求|CCB|变更评审|影响分析|基线变更|变更控制|cr[\\s-]?form|change request|change control"}
    };

    /** 5 个治理阶段元数据 */
    private static final String[][] STAGE_RULES = new String[][]{
            {"A1", "立项评审 (Charter Review)",     "立项评审|charter review|立项审查|立项批复"},
            {"A2", "方案评审 (Design Review)",      "方案评审|design review|概要设计评审|技术方案评审|架构评审|architecture review"},
            {"A3", "中期评审 (Mid-stage Review)",   "中期评审|中期汇报|中期审计|mid[- ]stage review|stage gate|阶段门禁"},
            {"A4", "投产评审 (Go-Live Review)",     "投产评审|上线评审|go[- ]?live(\\s+|$|review|gate)|launch review|cutover review|go-live"},
            {"A5", "验收评审 (Acceptance Review)",  "验收评审|终验|acceptance review|final review|交付评审"}
    };

    /** 区间防呆规则 (写死, 因为是从 PMO 经验来, 不依赖 SOW 文本) */
    private static final Map<String, IntervalRule> INTERVAL_RULES = new LinkedHashMap<>();
    static {
        // 项目总时长 (天数): 最短 30 天, 最长 730 天 (~ 2 年); 异常上下限
        INTERVAL_RULES.put("projectDurationDays", new IntervalRule(30, 730, 14, 1095));
        // 阶段间隔 (相邻 stage-gate 之间天数): 最短 7 天, 最长 365 天; 异常上下限
        INTERVAL_RULES.put("stageIntervalDays",  new IntervalRule(7, 365, 1, 730));
        // 各阶段周期 (单个阶段允许的最短/最长天数)
        INTERVAL_RULES.put("phaseDurationDays",  new IntervalRule(3, 365, 1, 730));
        // 总工期-正常参考: 6-18 个月
        INTERVAL_RULES.put("projectMonths",      new IntervalRule(2, 24, 1, 36));
    }

    public record IntervalRule(int minNormal, int maxNormal, int minAbsolute, int maxAbsolute) {
        public boolean isOutlier(double value) { return value < minAbsolute || value > maxAbsolute; }
        /** 警告 = 异常 (绝对外) OR 异常 (正常外但绝对内). 包含 isOutlier */
        public boolean isWarning(double value) {
            return isOutlier(value) || value < minNormal || value > maxNormal;
        }
    }

    /** 主入口 */
    public GovernanceInference infer(String sowText) {
        if (sowText == null) sowText = "";
        String lower = sowText.toLowerCase();

        // (1) 流程域
        List<String> processGroups = new ArrayList<>();
        for (String[] rule : PROCESS_GROUP_RULES) {
            String id = rule[0], name = rule[1], regex = rule[2];
            if (Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(lower).find()) {
                processGroups.add(id + "-" + name);
            }
        }
        // 至少要落到 P1..P5 (PMI 5 大过程组); P6/P7 视文本而定
        if (!containsId(processGroups, "P1")) processGroups.add(0, "P1-启动 (Initiating)");
        if (!containsId(processGroups, "P2")) processGroups.add(1, "P2-规划 (Planning)");
        if (!containsId(processGroups, "P3")) processGroups.add(2, "P3-执行 (Executing)");
        if (!containsId(processGroups, "P4")) processGroups.add(3, "P4-监控 (M&C)");
        if (!containsId(processGroups, "P5")) processGroups.add(4, "P5-收尾 (Closing)");

        // (2) 治理阶段
        List<String> stages = new ArrayList<>();
        for (String[] rule : STAGE_RULES) {
            String id = rule[0], name = rule[1], regex = rule[2];
            if (Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(lower).find()) {
                stages.add(id + "-" + name);
            }
        }
        // 兜底: A1+A5 强制 (立项 + 验收是最小治理闭环)
        if (!containsId(stages, "A1")) stages.add(0, "A1-立项评审 (Charter Review)");
        if (!containsId(stages, "A5")) stages.add(stages.size(), "A5-验收评审 (Acceptance Review)");

        log.info("[SowGovernanceInferencer] processGroups={} stages={}", processGroups, stages);
        return new GovernanceInference(processGroups, stages, INTERVAL_RULES);
    }

    private boolean containsId(List<String> items, String id) {
        for (String s : items) if (s.startsWith(id + "-")) return true;
        return false;
    }

    /** 推导结果 */
    public record GovernanceInference(
            List<String> processGroups,
            List<String> stages,
            Map<String, IntervalRule> intervalRules
    ) {}
}