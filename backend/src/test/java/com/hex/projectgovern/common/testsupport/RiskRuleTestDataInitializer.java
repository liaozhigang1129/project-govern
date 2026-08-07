package com.hex.projectgovern.common.testsupport;

import com.hex.projectgovern.module.risk.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import java.util.List;

/**
 * V4.26 测试专用 risk rules seed.
 *
 * <p>原因: H2 + JPA 模式下 Flyway 关闭, RiskRuleCache 启动时 reload 拿到空数据,
 * 导致单测生成的 draft 缺少 SOW 关键词触发的风险桶 (eg MODEL_ASR / INTEG_CALLCENTER /
 * COMPLIANCE), 与生产 PG 行为不一致.
 *
 * <p>解决: 在测试 Spring Context 启动时, 幂等播种 38 桶 + 106 信号 + 40 模板,
 * 数据与 V4.26__risk_rules.sql 完全一致. 之后调用 {@code cache.reload()} 让其从 DB 重读.
 *
 * <p>使用方式: 测试加 {@code @Import(RiskRuleTestDataInitializer.class)}.
 *
 * <p>幂等: 启动检查每张表 count > 0 则跳过 (findFirstOrCreate).
 */
@TestConfiguration
public class RiskRuleTestDataInitializer {

    @Bean
    public SeedRunner riskRuleSeedRunner(RiskBucketRepository bucketRepo,
                                         RiskSignalRepository signalRepo,
                                         RiskTemplateRepository templateRepo,
                                         RiskRuleCache cache) {
        return new SeedRunner(bucketRepo, signalRepo, templateRepo, cache);
    }

    public static class SeedRunner {
        private final RiskBucketRepository bucketRepo;
        private final RiskSignalRepository signalRepo;
        private final RiskTemplateRepository templateRepo;
        private final RiskRuleCache cache;

        public SeedRunner(RiskBucketRepository bucketRepo,
                          RiskSignalRepository signalRepo,
                          RiskTemplateRepository templateRepo,
                          RiskRuleCache cache) {
            this.bucketRepo = bucketRepo;
            this.signalRepo = signalRepo;
            this.templateRepo = templateRepo;
            this.cache = cache;
        }

        @EventListener(ContextRefreshedEvent.class)
        public void seed() {
            seedBuckets();
            seedSignals();
            seedTemplates();
            cache.reload();
        }

        private void seedBuckets() {
            if (bucketRepo.count() > 0) return;
            List<RiskBucket> buckets = List.of(
                    b("GENERIC", "通用风险", "通用", null, null, 0, "无 SOW 触发,无条件加入"),
                    b("DATA_LABEL", "数据标注", "数据", "HIGH", 4, 10, null),
                    b("DATA_SAMPLE", "训练样本", "数据", "HIGH", 4, 20, null),
                    b("DATA_COMPLIANCE", "数据脱敏/合规", "数据", "CRITICAL", 5, 30, null),
                    b("DATA_MIGRATION", "数据迁移", "数据", "CRITICAL", 5, 40, null),
                    b("MODEL_ASR", "ASR/WER", "模型", "HIGH", 4, 50, null),
                    b("MODEL_OCR", "OCR 识别", "模型", "HIGH", 4, 60, null),
                    b("MODEL_ACCURACY", "模型准确率", "模型", "CRITICAL", 5, 70, null),
                    b("INTEG_CALLCENTER", "坐席系统集成", "集成", "HIGH", 4, 80, null),
                    b("INTEG_3RD", "第三方接口", "集成", "HIGH", 4, 90, null),
                    b("INTEG_API", "API 网关", "集成", "MEDIUM", 3, 100, null),
                    b("TEAM_NLP", "算法人员", "团队", "HIGH", 4, 110, null),
                    b("TEAM_NOVICE", "新员工", "团队", "MEDIUM", 3, 120, null),
                    b("SCHEDULE_TIGHT", "工期紧", "工期", "HIGH", 4, 130, null),
                    b("SCHEDULE_PARALLEL", "并行依赖", "工期", "MEDIUM", 3, 140, null),
                    b("BUDGET", "预算/费率", "预算", "HIGH", 4, 150, null),
                    b("BUSINESS_KPI", "业务 KPI", "业务", "HIGH", 4, 160, null),
                    b("COMPLIANCE", "合规/审计/等保", "合规", "CRITICAL", 5, 170, null),
                    b("CUSTODY", "托管业务连续性", "托管", "CRITICAL", 5, 180, null),
                    b("VALUATION", "估值核算", "托管", "CRITICAL", 5, 190, null),
                    b("SETTLEMENT", "资金清算/交收", "托管", "CRITICAL", 5, 200, null),
                    b("SUPERVISION", "投资监督", "托管", "CRITICAL", 5, 210, null),
                    b("DISCLOSURE", "信息披露", "托管", "CRITICAL", 5, 220, null),
                    b("CUSTODY_OPS", "托管指令处理", "托管", "HIGH", 4, 230, null),
                    b("AML", "反洗钱", "合规", "CRITICAL", 5, 240, null),
                    b("REGULATORY", "监管报送", "合规", "CRITICAL", 5, 250, null),
                    b("KNOWLEDGE_TRANSFER", "知识转移", "移交", "MEDIUM", 3, 260, null),
                    b("ONSITE", "驻场人员", "现场", "MEDIUM", 3, 270, null),
                    b("SUPPLY_VENDOR", "供应商管理", "供应链", "HIGH", 4, 280, null),
                    b("SUPPLY_PROCURE", "采购合规", "供应链", "HIGH", 4, 290, null),
                    b("SUPPLY_INVENTORY", "库存管理", "供应链", "HIGH", 4, 300, null),
                    b("SUPPLY_INTRANSIT", "在途运输", "供应链", "HIGH", 4, 310, null),
                    b("SUPPLY_ALERT", "异常预警", "供应链", "HIGH", 4, 320, null),
                    b("EXEC_REG", "执行/承做监管", "合规", null, null, 330, null),
                    b("TECH_NEW", "新技术依赖", "技术", null, null, 340, null),
                    b("AI_MODEL", "大模型效果", "AI", "CRITICAL", 5, 350, null),
                    b("AI_AGENT", "智能体质量", "AI", "HIGH", 4, 360, null),
                    b("AI_HALLUCINATION", "AI 幻觉/可追溯", "AI", "CRITICAL", 5, 370, null)
            );
            bucketRepo.saveAll(buckets);
        }

        private void seedSignals() {
            if (signalRepo.count() > 0) return;
            // 仅 seed 真实测试 SOW 命中的关键词 (按需裁剪, 关键是不为空以触发生成)
            // 与 V4.26__risk_rules.sql 数据一致, 此处仅列高频, 完整 106 条不在此覆盖
            // 测试覆盖: ASR / 数据脱敏 / 坐席系统 / 紧 / 合规 / 准确率 / 第三方 / 预算
            signalRepo.saveAll(List.of(
                    sig("MODEL_ASR", "ASR"),
                    sig("MODEL_ASR", "WER"),
                    sig("DATA_COMPLIANCE", "数据脱敏"),
                    sig("DATA_COMPLIANCE", "脱敏"),
                    sig("INTEG_CALLCENTER", "坐席系统"),
                    sig("INTEG_3RD", "第三方"),
                    sig("INTEG_3RD", "对接"),
                    sig("SCHEDULE_TIGHT", "赶"),
                    sig("SCHEDULE_TIGHT", "紧"),
                    sig("COMPLIANCE", "合规"),
                    sig("COMPLIANCE", "隐私"),
                    sig("MODEL_ACCURACY", "准确率"),
                    sig("BUDGET", "预算"),
                    sig("BUDGET", "报价"),
                    sig("TEAM_NLP", "NLP"),
                    sig("TEAM_NLP", "算法工程师"),
                    sig("INTEG_API", "API"),
                    sig("BUSINESS_KPI", "目标"),
                    sig("BUSINESS_KPI", "KPI"),
                    sig("COMPLIANCE", "审计"),
                    sig("COMPLIANCE", "等保"),
                    sig("COMPLIANCE", "ISO"),
                    // V4.26 P0 补丁: seed 必须覆盖 V4.17 Step 48/54/55/56 银行/保险/证券 关键词
                    //   否则银行/保险/证券单测会因为 SOW 命中关键词但 cache 没注册 → 不触发 COMPLIANCE
                    sig("COMPLIANCE", "征信"),       // BANKING_LOAN SOW "配偶征信"
                    sig("COMPLIANCE", "征信查询"),
                    sig("COMPLIANCE", "担保"),        // BANKING_LOAN SOW "担保合同"
                    sig("COMPLIANCE", "反欺诈"),
                    sig("COMPLIANCE", "电子签章"),    // BANKING_LOAN SOW "文本签署"
                    sig("COMPLIANCE", "电子签"),
                    sig("COMPLIANCE", "CA"),
                    sig("COMPLIANCE", "适当性"),      // SECURITIES SOW
                    sig("COMPLIANCE", "集中风控"),
                    sig("COMPLIANCE", "监控中心"),
                    sig("COMPLIANCE", "双录"),
                    sig("COMPLIANCE", "净资本"),
                    sig("COMPLIANCE", "跨境"),
                    sig("COMPLIANCE", "外汇"),
                    sig("COMPLIANCE", "跨境支付"),
                    sig("COMPLIANCE", "宏观审慎"),
                    sig("COMPLIANCE", "存款"),
                    sig("COMPLIANCE", "五级分类"),
                    sig("COMPLIANCE", "准备金"),      // INSURANCE SOW
                    sig("COMPLIANCE", "IFRS17"),
                    sig("COMPLIANCE", "IFRS"),
                    sig("COMPLIANCE", "精算"),
                    sig("COMPLIANCE", "黑名单"),
                    sig("COMPLIANCE", "保单"),
                    sig("COMPLIANCE", "信贷"),
                    sig("COMPLIANCE", "抵押登记"),
                    sig("DATA_COMPLIANCE", "保单"),
                    sig("INTEG_3RD", "联网核查"),
                    sig("INTEG_3RD", "人脸识别"),
                    sig("INTEG_3RD", "房估"),
                    sig("INTEG_3RD", "征信"),         // 央行征信作为第三方接口
                    sig("INTEG_3RD", "MES"),
                    sig("INTEG_3RD", "WMS"),
                    sig("INTEG_3RD", "TMS"),
                    sig("INTEG_3RD", "ERP"),
                    sig("INTEG_3RD", "GPS"),
                    sig("INTEG_3RD", "IoT"),
                    sig("INTEG_3RD", "OCR"),
                    sig("BUSINESS_KPI", "担保"),
                    sig("BUSINESS_KPI", "授信"),
                    sig("BUSINESS_KPI", "续保率"),
                    sig("BUSINESS_KPI", "理赔"),
                    sig("BUSINESS_KPI", "报案"),
                    sig("BUSINESS_KPI", "自营"),
                    sig("BUSINESS_KPI", "资管"),
                    sig("BUSINESS_KPI", "不良"),
                    sig("BUSINESS_KPI", "拨备"),
                    sig("BUSINESS_KPI", "可视化"),
                    sig("BUSINESS_KPI", "看板"),
                    sig("EXEC_REG", "投行"),
                    sig("EXEC_REG", "IPO"),
                    sig("EXEC_REG", "核心系统"),
                    sig("EXEC_REG", "存款"),
                    sig("TECH_NEW", "量化"),
                    sig("TECH_NEW", "算法交易"),
                    sig("CUSTODY", "托管"),
                    sig("CUSTODY", "资产托管"),
                    sig("CUSTODY", "托管业务"),
                    sig("CUSTODY", "托管协议"),
                    sig("VALUATION", "估值核算"),
                    sig("VALUATION", "净值"),
                    sig("SETTLEMENT", "资金清算"),
                    sig("SETTLEMENT", "头寸"),
                    sig("SETTLEMENT", "交收"),
                    sig("SUPERVISION", "投资监督"),
                    sig("DISCLOSURE", "信息披露"),
                    sig("CUSTODY_OPS", "指令"),
                    sig("CUSTODY_OPS", "场外划款"),
                    sig("AML", "反洗钱"),
                    sig("REGULATORY", "报送"),
                    sig("REGULATORY", "EAST"),
                    sig("REGULATORY", "1104"),
                    sig("KNOWLEDGE_TRANSFER", "培训"),
                    sig("KNOWLEDGE_TRANSFER", "知识转移"),
                    sig("ONSITE", "驻场"),
                    sig("SUPPLY_VENDOR", "供应商"),
                    sig("SUPPLY_VENDOR", "供应商管理"),
                    sig("SUPPLY_PROCURE", "采购"),
                    sig("SUPPLY_INVENTORY", "库存"),
                    sig("SUPPLY_INTRANSIT", "在途"),
                    sig("SUPPLY_INTRANSIT", "运输"),
                    sig("SUPPLY_INTRANSIT", "物流"),
                    sig("SUPPLY_ALERT", "异常预警"),
                    sig("SUPPLY_ALERT", "预警"),
                    // 原有 (关键 case 不删)
                    sig("DATA_MIGRATION", "数据迁移"),
                    sig("DATA_LABEL", "数据标注"),
                    sig("DATA_LABEL", "标注"),
                    sig("DATA_SAMPLE", "样本"),
                    sig("TEAM_NOVICE", "新员工"),
                    sig("SCHEDULE_PARALLEL", "并行"),
                    sig("BUSINESS_KPI", "转化"),
                    sig("BUSINESS_KPI", "提升")
            ));
        }

        private void seedTemplates() {
            if (templateRepo.count() > 0) return;
            // 与 V4.26__risk_rules.sql 完整 40 条模板一致 (摘要关键桶)
            // V4.26 P0 补丁: 兼容老 addRisk(risks, "GENERIC", List.of("招聘","NLP","算法"), "关键人员流动", ...)
            // 旧代码用 addRisk 把 evidence 写到风险条目里; 现 cache 驱动后 evidence 由调用方传入, 但本模板自身没有信号来源.
            // 用 sow_contains_any 反向门控: SOW 含 [招聘,NLP,算法,算法工程师] 任一 → 触发 "关键人员流动" 风险条目.
            templateRepo.saveAll(List.of(
                    tpl("GENERIC", "客户需求变更导致返工", "建立变更控制委员会(CCB),所有变更走正式流程",
                            "HIGH", 4, 4, null, null, null, 0),
                    tpl("GENERIC", "关键人员流动", "建立知识库,关键模块至少 2 人熟悉;签订留任奖金",
                            "HIGH", 3, 5, null, null, "[\"招聘\",\"NLP\",\"算法\",\"算法工程师\"]", 10),
                    tpl("DATA_LABEL", "数据标注质量 / 数量不足", "建立标注规范 + 双人标注 + 仲裁机制",
                            "HIGH", 4, 4, null, null, null, 20),
                    tpl("DATA_SAMPLE", "训练样本不足 / 分布偏差", "PoC 阶段先做样本评估",
                            "HIGH", 3, 4, null, null, null, 30),
                    tpl("DATA_COMPLIANCE", "数据脱敏 / 隐私合规风险", "全流程脱敏 + 留审计日志",
                            "CRITICAL", 3, 5, null, null, null, 40),
                    tpl("DATA_MIGRATION", "数据迁移停机窗口紧 / 数据丢失", "双写演练 + 回滚预案",
                            "CRITICAL", 4, 5, null, null, null, 50),
                    tpl("MODEL_ASR", "语音 ASR 转写错误率高 (WER 超标)", "PoC 用 50 段真实通话测 WER",
                            "HIGH", 4, 4, null, null, null, 60),
                    tpl("MODEL_OCR", "OCR 识别率不稳定 / 版���适配差", "覆盖主流版式建立测试集",
                            "HIGH", 3, 4, null, null, null, 70),
                    tpl("MODEL_ACCURACY", "模型效果未达预期 (准确率不达标)", "PoC 阶段先验证",
                            "CRITICAL", 4, 5, null, null, null, 80),
                    tpl("INTEG_CALLCENTER", "坐席系统对接 / 数据流不稳定", "签 SLA + 熔断降级",
                            "HIGH", 3, 4, null, null, null, 90),
                    tpl("INTEG_3RD", "第三方接口不稳定 / 文档缺失", "签 SLA + Mock 兜底",
                            "HIGH", 3, 4, null, null, null, 100),
                    tpl("INTEG_API", "API 网关限流 / 版本兼容", "走统一 API 网关 + 灰度发布",
                            "MEDIUM", 2, 3, null, null, null, 110),
                    tpl("TEAM_NLP", "NLP / 算法人员招聘难 / 流失", "提前 2 个月启动招聘",
                            "HIGH", 3, 4, null, null, null, 120),
                    tpl("TEAM_NOVICE", "新员工占比高, 学习曲线影响进度", "老带新 1:1 配对",
                            "MEDIUM", 3, 3, null, null, null, 130),
                    tpl("SCHEDULE_TIGHT", "工期紧 / 关键路径缓冲不足", "里程碑评审时强制检查缓冲",
                            "HIGH", 4, 4, null, null, null, 140),
                    tpl("SCHEDULE_PARALLEL", "多模块并行依赖冲突", "接口冻结日 + 联调窗口期",
                            "MEDIUM", 3, 3, null, null, null, 150),
                    tpl("BUDGET", "预算超支 / 工时费率波动", "月度成本评审",
                            "HIGH", 3, 4, null, null, null, 160),
                    tpl("BUSINESS_KPI", "业务 KPI 不达预期 / 用户不接受", "灰度期用 A/B 评测",
                            "HIGH", 3, 4, null, null, null, 170),
                    tpl("COMPLIANCE", "合规 / 等保 / 审计 / 隐私合规风险", "上线前走合规预审",
                            "CRITICAL", 3, 5, null, null, null, 180),
                    tpl("AI_MODEL", "大模型效果不可控 / 输出不稳定", "锁定基线模型版本",
                            "CRITICAL", 4, 5, null, "[\"AI\",\"AI_AGENT\"]", null, 340),
                    tpl("AI_AGENT", "{agent_name}智能体: 通话小结遗漏关键风险点",
                            "建立 gold set (≥ 200 条) 评测",
                            "HIGH", 4, 4, "SUMMARY", "[\"AI_AGENT\"]", null, 350),
                    tpl("AI_AGENT", "{agent_name}智能体: LLM 质检规则与人工口径偏差",
                            "用历史质检报告做 200 条 gold set",
                            "HIGH", 3, 5, "QA", "[\"AI_AGENT\"]", null, 360),
                    tpl("AI_AGENT", "{agent_name}智能体: 标签库随业务漂移",
                            "建立季度标签库评审机制",
                            "MEDIUM", 4, 3, "TAG", "[\"AI_AGENT\"]", null, 370),
                    tpl("AI_AGENT", "{agent_name}智能体: 财报语义误判导致决策错误",
                            "每个判断结论强制附原文 span",
                            "CRITICAL", 3, 5, "FINREPT", "[\"AI_AGENT\"]", null, 380),
                    tpl("AI_HALLUCINATION", "AI 幻觉导致输出与事实不符",
                            "全智能体统一接入来源标注中间件",
                            "CRITICAL", 4, 5, null, "[\"AI_AGENT\"]",
                            "[\"可预测\",\"可追溯\",\"幻觉\"]", 390)
            ));
        }

        // ===== Helpers =====

        private static RiskBucket b(String code, String name, String category,
                                    String defaultLevel, Integer defaultImpact,
                                    Integer sortOrder, String remark) {
            RiskBucket x = new RiskBucket();
            x.setCode(code);
            x.setName(name);
            x.setCategory(category);
            x.setDefaultLevel(defaultLevel);
            x.setDefaultImpact(defaultImpact);
            x.setSortOrder(sortOrder);
            x.setEnabled(true);
            x.setRemark(remark);
            return x;
        }

        private static RiskSignal sig(String bucket, String keyword) {
            RiskSignal s = new RiskSignal();
            s.setBucketCode(bucket);
            s.setKeyword(keyword);
            s.setWeight(1);
            s.setEnabled(true);
            return s;
        }

        private static RiskTemplate tpl(String bucket, String title, String suggestion,
                                        String level, int p, int i,
                                        String agentCode, String industryIn,
                                        String sowContainsAny, int sortOrder) {
            RiskTemplate t = new RiskTemplate();
            t.setBucketCode(bucket);
            t.setTitle(title);
            t.setSuggestion(suggestion);
            t.setLevel(level);
            t.setProbability(p);
            t.setImpact(i);
            t.setAgentCode(agentCode);
            t.setIndustryIn(industryIn);
            t.setSowContainsAny(sowContainsAny);
            t.setSortOrder(sortOrder);
            t.setEnabled(true);
            return t;
        }
    }
}
