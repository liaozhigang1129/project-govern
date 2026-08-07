package com.hex.projectgovern.module.initiation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SowExtractor 单元测试 (V4.17)
 *
 * 覆盖 3 个真实 SOW 场景:
 *  1) 智能客服 NLP 升级
 *  2) 银行风控数据迁移
 *  3) 制造业 ERP 上线
 *
 * 每个场景断言: industry / modules / techStack / timeline / budget / riskSignals
 * 抽得对不对, 是 WBS / 风险生成质量的根本。
 */
class SowExtractorTest {

    // ===== 场景 1: 智能客服 NLP =====
    @Test
    void extract_customerServiceNlp() {
        String sow = """
                智能客服 NLP 升级项目 SOW
                工期 6 个月, 预算 38 万。

                业务背景: 客服工单 NLP 准确率仅 71%, 客户投诉率高。
                目标: NLP 准确率提升到 92%, 智能派单覆盖率 0% → 60%。

                范围 (5 个 NLP 模型):
                1) 工单分类模型
                2) 意图识别模型
                3) 情绪分析模型
                4) 自动派单智能体
                5) 客户画像模型

                技术栈: Qwen 大模型 + RAG + Embedding + 微服务 (Spring Cloud) + MySQL + Redis + Kafka

                集成: 坐席系统 (东信) + 工单系统 (自研) + 钉钉 IM

                团队: 1 NLP 算法工程师 + 2 后端 + 1 前端 + 1 QA

                交付物:
                1) 50k 标注样本数据集
                2) 5 个微调后 NLP 模型
                3) NLP 准确率评估报告
                4) 智能派单系统
                5) 运维 SOP

                风险提示: ASR 准确率, 数据脱敏合规, 工单系统对接 SLA 紧。
                """;

        SowExtractor.Extraction e = SowExtractor.extract(sow);

        assertEquals("AI_AGENT", e.industry(), "应识别为 AI_AGENT (大模型+智能体+微服务+坐席)");
        assertTrue(e.modules().contains("工单分类模型") || e.modules().contains("分类模型"),
                "应抽到业务模块, 实际: " + e.modules());
        assertTrue(e.modules().stream().anyMatch(m -> m.contains("意图识别")),
                "应抽到意图识别, 实际: " + e.modules());
        assertTrue(e.modules().stream().anyMatch(m -> m.contains("派单")),
                "应抽到派单, 实际: " + e.modules());
        assertTrue(e.techStack().contains("Qwen"), "应抽到 Qwen");
        assertTrue(e.techStack().contains("RAG") || e.techStack().contains("Embedding"),
                "应抽到 RAG/Embedding");
        assertTrue(e.techStack().contains("Spring Cloud") || e.techStack().contains("微服务"),
                "应抽到微服务");
        assertTrue(e.techStack().contains("MySQL"), "应抽到 MySQL");
        assertEquals(24, e.durationWeeks(), "6 个月 → 24 周");
        assertEquals("6 个月", e.durationRaw());
        assertEquals(38_000_000L, e.budgetCents().longValue(), "38 万 → 38,000,000 分 (1 元=100 分), 实际: " + e.budgetCents());
        assertTrue(e.deliverables().size() >= 4, "交付物至少 4 条, 实际: " + e.deliverables().size());
        assertTrue(e.teamHints().contains("NLP"), "团队应有 NLP");
        assertTrue(e.integrations().stream().anyMatch(s -> s.contains("坐席")),
                "集成应有坐席系统, 实际: " + e.integrations());
        // 风险信号
        assertTrue(e.riskSignals().containsKey("MODEL_ASR"), "应有 ASR 风险");
        assertTrue(e.riskSignals().containsKey("DATA_COMPLIANCE"), "应有合规风险");
        assertTrue(e.riskSignals().containsKey("BUDGET"), "应有预算风险");
        assertTrue(e.riskSignals().containsKey("INTEG_CALLCENTER"), "应有坐席系统集成风险");
    }

    // ===== 场景 2: 银行风控数据迁移 =====
    @Test
    void extract_bankDataMigration() {
        String sow = """
                银行风控数据中台迁移项目
                工期 12 周, 报价 280 万。

                范围: 把 5 个核心系统 (信贷/征信/支付) 的历史数据迁到数据湖仓一体平台。
                ETL + 数据质量监控 + 合规审计 + 等保三级。

                团队: 2 数据分析师 + 1 架构师 + 3 后端 Java + 1 SRE。

                交付物:
                1) 数据迁移 ETL 脚本 (Spark + Flink)
                2) 数据质量报告
                3) 审计日志
                4) 等保测评报告
                """;

        SowExtractor.Extraction e = SowExtractor.extract(sow);

        assertEquals("DATA", e.industry(), "应识别为 DATA");
        assertTrue(e.techStack().contains("ETL"), "应抽到 ETL");
        assertTrue(e.techStack().stream().anyMatch(s -> s.contains("Spark") || s.contains("Flink")),
                "应抽到 Spark/Flink");
        assertEquals(12, e.durationWeeks(), "12 周");
        assertEquals(280_000_000L, e.budgetCents().longValue(), "280 万 → 2.8 亿分 (1 元=100 分), 实际: " + e.budgetCents());
        assertTrue(e.teamHints().contains("架构师"), "团队应有架构师");
        assertTrue(e.riskSignals().containsKey("DATA_MIGRATION"), "应有数据迁移风险");
        assertTrue(e.riskSignals().containsKey("COMPLIANCE"), "应有合规风险");
    }

    // ===== 场景 3: 制造业 ERP 上线 =====
    @Test
    void extract_manufacturingErp() {
        String sow = """
                制造业 ERP (SAP) 上线项目
                工期 9 个月, 合同金额 150 万。

                业务: 财务 + 供应链 + 生产 三大模块并行上线。
                预算紧, 计划 9 月底前完成财务模块灰度。

                团队: 1 售前 + 1 方案经理 + 2 实施 + 1 配置 + 1 培训。

                交付物:
                1) 业务蓝图
                2) 系统配置
                3) 数据迁移
                4) 用户培训
                5) 并行上线
                6) 项目验收
                """;

        SowExtractor.Extraction e = SowExtractor.extract(sow);

        assertEquals("ERP", e.industry(), "应识别为 ERP");
        assertEquals(36, e.durationWeeks(), "9 个月 → 36 周");
        assertEquals(150_000_000L, e.budgetCents().longValue(), "150 万 → 1.5 亿分 (1 元=100 分), 实际: " + e.budgetCents());
        assertTrue(e.teamHints().contains("售前"), "团队应有售前");
        assertTrue(e.teamHints().contains("方案") || e.teamHints().stream().anyMatch(s -> s.contains("实施")),
                "团队应有方案/实施");
        assertTrue(e.deliverables().size() >= 4, "交付物至少 4 条");
        assertTrue(e.riskSignals().containsKey("SCHEDULE_TIGHT") || e.riskSignals().containsKey("BUDGET"),
                "应有工期紧/预算风险, 实际: " + e.riskSignals().keySet());
    }

    // ===== 场景 4: 银行资产托管 (V4.18 — 苏州银行资产托管) =====
    @Test
    void extract_bankAssetCustody() {
        String sow = """
                苏州银行资产托管综合管理服务项目 SOW
                工期 16 周, 预算 240 万。

                业务背景: 苏州银行拟通过采购建设资产托管综合管理服务, 实现托管业务全流程线上化。
                业务范围: 客户服务 + 托管业务 + 监督 + 估值 + 清算 + 信息披露。

                模块:
                1) 账户与头寸管理 (账户层级 / 余额对账)
                2) 净值估值核算 (净值核算 / 单位净值)
                3) 资金清算与交收 (场外划款 / 日终批量)
                4) 投资监督��信息披露 (合规监督 / 比例监督 / 信息披露)
                5) 机构服务平台 (管理人入口 / 委托人入口)
                6) 监管报送 (EAST / 1104)

                风险: 资金清算失败 / 估值核算错误 / 投资监督规则未触发 / 托管业务连续性中断 /
                      信息披露延迟 / 数据脱敏合规 / 监管报送延迟。

                交付物: 托管系统 + 信息披露文件 + 监管报送数据 + 联调测试报告 + 培训课件。
                """;

        SowExtractor.Extraction e = SowExtractor.extract(sow);

        // 关键断言: 必须识别为 BANKING_CUSTODY, 而不是 BANKING_CORE / SECURITIES
        assertEquals("BANKING_CUSTODY", e.industry(),
                "苏州 SOW 大量出现 '托管/估值/清算/监督/披露' 应识别为 BANKING_CUSTODY, 实际: " + e.industry());
        assertTrue(e.modules().stream().anyMatch(m -> m.contains("托管")),
                "应有托管模块, 实际: " + e.modules());
        assertTrue(e.modules().stream().anyMatch(m -> m.contains("估值")),
                "应有估值模块, 实际: " + e.modules());
        assertTrue(e.modules().stream().anyMatch(m -> m.contains("清算")),
                "应有清算模块, 实际: " + e.modules());
        // 风险信号 (新增的 4 个托管专属 + 通用合规) — 桶名以 RISK_SIGNAL_TO_BUCKET 为准
        assertTrue(e.riskSignals().containsKey("CUSTODY"),
                "应有托管业务连续性风险, 实际: " + e.riskSignals().keySet());
        assertTrue(e.riskSignals().containsKey("VALUATION"),
                "应有估值核算错误风险, 实际: " + e.riskSignals().keySet());
        assertTrue(e.riskSignals().containsKey("SETTLEMENT"),
                "应有资金清算失败风险, 实际: " + e.riskSignals().keySet());
        assertTrue(e.riskSignals().containsKey("SUPERVISION"),
                "应有投资监督未触发风险, 实际: " + e.riskSignals().keySet());
        assertTrue(e.riskSignals().containsKey("DISCLOSURE"),
                "应有信息披露延迟风险, 实际: " + e.riskSignals().keySet());
        assertTrue(e.riskSignals().containsKey("REGULATORY"),
                "应有监管报送风险, 实际: " + e.riskSignals().keySet());
    }

    // ===== 场景 5: 供应链可视化 (V4.18 — 制造业供应链可视化平台) =====
    @Test
    void extract_supplyChainVisibility() {
        String sow = """
                制造业供应链可视化平台 SOW
                工期 20 周, 合同 180 万。

                业务背景: 制造业供应链上下游协同效率低, 供应商主数据分散, 在途运输不可见。
                拟通过采购建设供应链可视化平台, 实现供应商/采购/库存/在途 全链路透明。

                模块:
                1) 供应商主数据 (准入 / 分级 / 绩效)
                2) 采购订单管理 (PO 创建 / 审批 / 跟踪)
                3) 库存可视化 (VMI / 安全库存 / 库存积压预警)
                4) 在途运输轨迹 (GPS / TMS / IoT 设备数据接入)
                5) 异常预警看板 (延误 / 缺料 / 价格波动)

                技术栈: Spring Cloud + Kafka + Flink + Elasticsearch + MySQL

                团队: 1 PM + 2 后端 + 1 前端 + 1 数据 + 1 QA

                风险: 供应商集中度 / 采购合规 / 库存积压 / 在途延误 / 预警误报 / 物流接口不稳定。

                交付物: 供应商门户 + 采购订单平台 + 库存看板 + 运输轨迹大屏 + 预警中心。
                """;

        SowExtractor.Extraction e = SowExtractor.extract(sow);

        // 关键断言: 必须识别为 SUPPLY_CHAIN (而不是 ERP / BANKING_CORE)
        assertEquals("SUPPLY_CHAIN", e.industry(),
                "大量出现 '供应商/采购/库存/在途/运输' 应识别为 SUPPLY_CHAIN, 实际: " + e.industry());
        assertTrue(e.modules().stream().anyMatch(m -> m.contains("供应商")),
                "应有供应商模块, 实际: " + e.modules());
        assertTrue(e.modules().stream().anyMatch(m -> m.contains("采购")),
                "应有采购模块, 实际: " + e.modules());
        assertTrue(e.modules().stream().anyMatch(m -> m.contains("库存")),
                "应有库存模块, 实际: " + e.modules());
        // 风险信号 (新增的 5 个供应链专属)
        assertTrue(e.riskSignals().containsKey("SUPPLY_VENDOR"),
                "应有供应商集中度风险");
        assertTrue(e.riskSignals().containsKey("SUPPLY_PROCURE"),
                "应有采购合规风险");
        assertTrue(e.riskSignals().containsKey("SUPPLY_INVENTORY"),
                "应有库存积压风险");
        assertTrue(e.riskSignals().containsKey("SUPPLY_INTRANSIT"),
                "应有在途延误风险");
        assertTrue(e.riskSignals().containsKey("SUPPLY_ALERT"),
                "应有预警误报风险");
        // tech stack 抽取
        assertTrue(e.techStack().contains("Kafka") || e.techStack().contains("Flink"),
                "应抽到 Kafka/Flink, 实际: " + e.techStack());
        assertEquals(20, e.durationWeeks(), "20 周");
    }

    // ===== 边界场景: 空 / 太短 =====
    @Test
    void extract_empty() {
        SowExtractor.Extraction e1 = SowExtractor.extract("");
        assertTrue(e1.isEmpty(), "空串应返回空结果");

        SowExtractor.Extraction e2 = SowExtractor.extract(null);
        assertTrue(e2.isEmpty(), "null 应返回空结果");
    }
}
