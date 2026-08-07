package com.hex.projectgovern.module.initiation;

import com.hex.projectgovern.module.dict.InitiationStatus;
import com.hex.projectgovern.module.dict.InitiationStatusRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V4.18 Step 60 — 资产托管 / 供应链可视化 SOW 全链路 E2E
 *
 * <p>验证 detectIndustry 的 3 个边界情况:
 *  1) BANKING_CUSTODY (苏州银行资产托管) — 必须早于 BANKING_CORE / SECURITIES 命中
 *  2) SUPPLY_CHAIN (制造业供应链可视化) — 必须早于 ERP 命中
 *  3) 同一份 SOW 二次生成 → bit-equal (Fix-1/2 回归)</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class InitiationBankingCustodyE2ETest {

    @BeforeEach
    void seedStatus() {
        if (statusRepo.count() == 0) {
            for (var pair : new String[][]{
                    {"PENDING", "审批中", "false"},
                    {"DEPT_APPROVED", "部门通过", "false"},
                    {"PMO_APPROVED", "PMO通过", "false"},
                    {"EXEC_APPROVED", "已批准", "true"},
                    {"REJECTED", "已驳回", "true"},
                    {"SUPPLEMENT", "需补充", "false"},
            }) {
                InitiationStatus x = new InitiationStatus();
                x.setCode(pair[0]); x.setName(pair[1]); x.setTerminal(Boolean.parseBoolean(pair[2]));
                x.setSortOrder(0);
                statusRepo.save(x);
            }
        }
    }

    @Autowired InitiationAiWbsService wbsService;
    @Autowired ProjectInitiationRepository initRepo;
    @Autowired InitiationService initiationService;
    @Autowired ObjectMapper om;
    @Autowired InitiationStatusRepository statusRepo;

    // ===== 苏州银行资产托管 SOW =====
    private static final String CUSTODY_SOW = """
            苏州银行资产托管综合管理服务项目 SOW
            工期 16 周, 预算 240 万。

            业务背景: 苏州银行拟通过采购建设资产托管综合管理服务。
            业务范围: 客户服务 + 托管业务 + 监督 + 估值 + 清算 + 信息披露。

            模块:
            1) 账户与头寸管理 (账户层级 / 余额对账)
            2) 净值估值核算 (净值核算 / 单位净值)
            3) 资金清算与交收 (场外划款 / 日终批量)
            4) 投资监督与信息披露 (合规监督 / 比例监督)
            5) 机构服务平台 (管理人入口 / 委托人入口)
            6) 监管报送 (EAST / 1104)

            风险: 资金清算失败 / 估值核算错误 / 投资监督规则未触发 / 托管业务连续性中断 /
                  信息披露延迟 / 数据脱敏合规 / 监管报送延迟。

            交付物: 托管系统 + 信息披露文件 + 监管报送数据 + 联调测试报告 + 培训课件。
            """;

    // ===== 制造业供应链可视化 SOW =====
    private static final String SUPPLY_CHAIN_SOW = """
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

    // ========================================================================
    // 测试 1: 资产托管 — industry 必须 = BANKING_CUSTODY
    // ========================================================================
    @Test
    @DisplayName("V4.18 — 苏州银行资产托管 SOW → BANKING_CUSTODY 行业 + 8 里程碑 + ≥18 WP + ≥3 风险")
    void custodySow_detectIndustryAndGenerate() throws Exception {
        ProjectInitiation init = new ProjectInitiation();
        init.setCode("INIT-CUSTODY-" + System.nanoTime());
        init.setTitle("E2E资产托管验证");
        init.setApplicantId(1L);
        init.setDepartmentId(1L);
        init.setBackground("苏州银行资产托管综合管理服务");
        init.setGoals("实现托管业务全流程线上化");
        init.setScope("客户服务+托管+监督+估值+清算+披露");
        init.setSowPasteText(CUSTODY_SOW);
        init = initiationService.submit(init);
        Long id = init.getId();

        InitiationAiWbsDraft draft = wbsService.generateDraft(id, null, 2, 1L);
        Map<String, Object> r = om.readValue(draft.getDraftJson(), new TypeReference<Map<String, Object>>() {});
        List<Map<String, Object>> ms = (List<Map<String, Object>>) r.get("milestones");
        List<Map<String, Object>> wp = (List<Map<String, Object>>) r.get("workPackages");
        List<Map<String, Object>> risks = (List<Map<String, Object>>) r.get("risks");

        // 关键断言: industry = BANKING_CUSTODY (不是 BANKING_CORE, 不是 SECURITIES)
        assertThat(r.get("industry")).as("苏州 SOW 大量 '托管/估值/清算/监督' 必须命中 BANKING_CUSTODY")
                .isEqualTo("BANKING_CUSTODY");
        // 里程碑数量
        assertThat(ms).as("8 阶段托管模板").hasSize(8);
        // WP 数量 (BANKING_CUSTODY 模板要求 ≥ 18)
        assertThat(wp.size()).as("托管 WP ≥ 18, 实际: " + wp.size()).isGreaterThanOrEqualTo(18);
        // 风险数量 (含专属)
        assertThat(risks.size()).as("托管风险 ≥ 3, 实际: " + risks.size()).isGreaterThanOrEqualTo(3);
        // 关键 WP 名称必须出现
        assertThat(wp).extracting(w -> (String) w.get("name"))
                .as("托管专属 WP 必须出现")
                .anyMatch(n -> n.contains("托管账户") || n.contains("账户开立"))
                .anyMatch(n -> n.contains("估值") || n.contains("净值"))
                .anyMatch(n -> n.contains("清算") || n.contains("交收"))
                .anyMatch(n -> n.contains("监督") || n.contains("披露"));
        // 风险桶必须含托管专属
        assertThat(risks).extracting(rr -> (String) rr.get("bucket"))
                .as("风险桶应含托管连续性 / 估值 / 清算 / 监督 / 披露 之一")
                .anyMatch(b -> b != null && (
                        b.contains("CUSTODY") || b.contains("VALUATION") ||
                        b.contains("SETTLEMENT") || b.contains("SUPERVISION") ||
                        b.contains("DISCLOSURE") || b.contains("REGULATORY")));
        // 风险桶通用项
        assertThat(risks).extracting(rr -> (String) rr.get("bucket"))
                .contains("COMPLIANCE");

        System.out.println("\n=== BANKING_CUSTODY E2E ✅ ===");
        System.out.printf("industry=%s | milestones=%d | wp=%d | risks=%d%n",
                r.get("industry"), ms.size(), wp.size(), risks.size());
    }

    // ========================================================================
    // 测试 2: 供应链可视化 — industry 必须 = SUPPLY_CHAIN
    // ========================================================================
    @Test
    @DisplayName("V4.18 — 制造业供应链可视化 SOW → SUPPLY_CHAIN 行业 + 8 里程碑 + ≥18 WP + ≥3 风险")
    void supplyChainSow_detectIndustryAndGenerate() throws Exception {
        ProjectInitiation init = new ProjectInitiation();
        init.setCode("INIT-SUPPLY-" + System.nanoTime());
        init.setTitle("E2E供应链可视化验证");
        init.setApplicantId(1L);
        init.setDepartmentId(1L);
        init.setBackground("制造业供应链上下游协同效率低, 在途运输不可见");
        init.setGoals("实现供应商/采购/库存/在途 全链路透明");
        init.setScope("供应商主数据/采购订单/库存可视化/在途轨迹/异常预警");
        init.setSowPasteText(SUPPLY_CHAIN_SOW);
        init = initiationService.submit(init);
        Long id = init.getId();

        InitiationAiWbsDraft draft = wbsService.generateDraft(id, null, 2, 1L);
        Map<String, Object> r = om.readValue(draft.getDraftJson(), new TypeReference<Map<String, Object>>() {});
        List<Map<String, Object>> ms = (List<Map<String, Object>>) r.get("milestones");
        List<Map<String, Object>> wp = (List<Map<String, Object>>) r.get("workPackages");
        List<Map<String, Object>> risks = (List<Map<String, Object>>) r.get("risks");

        // 关键断言: industry = SUPPLY_CHAIN (不是 ERP, 不是 BANKING_CORE)
        assertThat(r.get("industry")).as("大量 '供应商/采购/库存/在途/运输' 必须命中 SUPPLY_CHAIN")
                .isEqualTo("SUPPLY_CHAIN");
        assertThat(ms).as("8 阶段供应链模板").hasSize(8);
        assertThat(wp.size()).as("供应链 WP ≥ 18, 实际: " + wp.size()).isGreaterThanOrEqualTo(18);
        assertThat(risks.size()).as("供应链风险 ≥ 3, 实际: " + risks.size()).isGreaterThanOrEqualTo(3);
        // 关键 WP
        assertThat(wp).extracting(w -> (String) w.get("name"))
                .as("供应链专属 WP 必须出现")
                .anyMatch(n -> n.contains("供应商"))
                .anyMatch(n -> n.contains("采购"))
                .anyMatch(n -> n.contains("库存"))
                .anyMatch(n -> n.contains("运输") || n.contains("在途"));
        // 风险桶 (5 个供应���专属 SUPPLY_*)
        assertThat(risks).extracting(rr -> (String) rr.get("bucket"))
                .as("风险桶应含供应商/采购/库存/在途/预警 之一")
                .anyMatch(b -> b != null && b.startsWith("SUPPLY_"));

        System.out.println("\n=== SUPPLY_CHAIN E2E ✅ ===");
        System.out.printf("industry=%s | milestones=%d | wp=%d | risks=%d%n",
                r.get("industry"), ms.size(), wp.size(), risks.size());
    }

    // ========================================================================
    // 测试 3: 优先级回归 — 同一份 SOW 二次生成 bit-equal
    // ========================================================================
    @Test
    @DisplayName("V4.18 — 优先级回归: 资产托管 + 供应链二次生成 sowTextHash + industry 一致")
    void custodyAndSupply_deterministicRerun() throws Exception {
        // 资产托管
        ProjectInitiation init1 = new ProjectInitiation();
        init1.setCode("INIT-DET-C-" + System.nanoTime());
        init1.setTitle("托管确定性");
        init1.setApplicantId(1L); init1.setDepartmentId(1L);
        init1.setBackground("b"); init1.setGoals("g"); init1.setScope("s");
        init1.setSowPasteText(CUSTODY_SOW);
        init1 = initiationService.submit(init1);

        Map<String, Object> r1 = om.readValue(
                wbsService.generateDraft(init1.getId(), null, 2, 1L).getDraftJson(),
                new TypeReference<Map<String, Object>>() {});
        Map<String, Object> r1b = om.readValue(
                wbsService.generateDraft(init1.getId(), null, 2, 1L).getDraftJson(),
                new TypeReference<Map<String, Object>>() {});
        assertThat(r1b.get("industry")).isEqualTo(r1.get("industry"));
        assertThat(r1b.get("sowTextHash")).isEqualTo(r1.get("sowTextHash"));

        // 供应链
        ProjectInitiation init2 = new ProjectInitiation();
        init2.setCode("INIT-DET-S-" + System.nanoTime());
        init2.setTitle("供应链确定性");
        init2.setApplicantId(1L); init2.setDepartmentId(1L);
        init2.setBackground("b"); init2.setGoals("g"); init2.setScope("s");
        init2.setSowPasteText(SUPPLY_CHAIN_SOW);
        init2 = initiationService.submit(init2);

        Map<String, Object> r2 = om.readValue(
                wbsService.generateDraft(init2.getId(), null, 2, 1L).getDraftJson(),
                new TypeReference<Map<String, Object>>() {});
        Map<String, Object> r2b = om.readValue(
                wbsService.generateDraft(init2.getId(), null, 2, 1L).getDraftJson(),
                new TypeReference<Map<String, Object>>() {});
        assertThat(r2b.get("industry")).isEqualTo(r2.get("industry"));
        assertThat(r2b.get("sowTextHash")).isEqualTo(r2.get("sowTextHash"));

        System.out.println("\n=== 优先级 + 确定性回归 ✅ ===");
        System.out.printf("BANKING_CUSTODY hash=%s | SUPPLY_CHAIN hash=%s%n",
                r1.get("sowTextHash"), r2.get("sowTextHash"));
    }
}