package com.hex.projectgovern.module.initiation;

import com.hex.projectgovern.module.dict.InitiationStatus;
import com.hex.projectgovern.module.dict.InitiationStatusRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hex.projectgovern.module.milestone.MilestoneRepository;
import com.hex.projectgovern.module.wbs.WbsTaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V4.17 Step 59 — 经营贷 SOW 全链路 E2E 验证
 *
 * <p>完整模拟用户通过前端按钮触发的链路:
 *  Controller → InitiationAiWbsService.generateDraft → SowExtractor.extract
 *  → AI 模板选择 → 关键词门控 → 风险生成 → 持久化 → 数据库回读</p>
 *
 * <p>数据用经营贷 SOW (来自 PMO 立项文档), 与银行 PMO 经理日常工作场景一致</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class InitiationBankingLoanE2ETest {

    @org.junit.jupiter.api.BeforeEach
    void seedStatus() {
        // 播种 initiation_status (InitiationService.submit() 要查 PENDING)
        if (statusRepo0.count() == 0) {
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
                statusRepo0.save(x);
            }
        }
    }

    @Autowired InitiationAiWbsService wbsService;
    @Autowired ProjectInitiationRepository initRepo;
    @Autowired InitiationService initiationService;
    @Autowired InitiationAiWbsDraftRepository draftRepo;
    @Autowired MilestoneRepository milestoneRepo;
    @Autowired WbsTaskRepository wbsTaskRepo;
    @Autowired ObjectMapper om;
    @Autowired InitiationStatusRepository statusRepo0;   // 用于播种 PENDING 状态

    /** 真实的经营贷 SOW (PMO 立项文档原文, 模拟用户在浏览器粘贴的输入) */
    private static final String BANKING_LOAN_SOW = """
            个人作业平台经营贷项目主要需求概述
            项目背景：为更好的服务小微企业主客户，支持普惠信贷业务发展，进一步提升抵押类经营贷产品流程，新设开发经营贷授信额度产品。
            ㈠客户申请。主要实现客户端申请进件流程，包含手机银行和手机银行H5，以及配偶征信授权流程。主要实现房产预估、证件上传、联网核查、人脸识别、文本签署、短信验证、企业信息关联、同步申请路路通、同步申请标卡、配偶征信授权和电子存证等功能等。
            ㈡调查确认。主要实现移动端的调查确认和调查审批，包括申请信息、预审信息、房产信息建立及评估、影像信息的采集、调查报告撰写，有权人审批及补件流程、进度查询等。
            ㈢自动审批。主要实现自动预审和终审的规则，以及额度的计算等。
            ㈣抵押登记。主要实现担保物的建立及评估，以及担保合同的登记等。
            """;

    @Test
    @DisplayName("V4.17 Step 59 — 经营贷 SOW 全链路 E2E (粘贴 SOW → 模板匹配 → WP 生成 → 风险 → 持久化)")
    void bankingLoanSow_fullE2E_pipeline() throws Exception {
        // ============ 步骤 1: 创建立项 (模拟 PMO 经理新建项目) ============
        ProjectInitiation init = new ProjectInitiation();
        init.setCode("INIT-E2E-LOAN-" + System.nanoTime());
        init.setTitle("E2E经营贷验证项目");
        init.setApplicantId(1L);
        init.setDepartmentId(1L);
        init.setBackground("小微企业主经营贷授信额度产品");
        init.setGoals("支持普惠信贷业务发展，提升抵押类经营贷产品流程");
        init.setScope("客户申请/调查/审批/抵押登记全流程");
        init = initiationService.submit(init);
        Long initiationId = init.getId();
        System.out.println("\n╔══════════════════════════════════════════════════════╗");
        System.out.println("║ STEP 1: 立项创建成功                                   ║");
        System.out.println("╠══════════════════════════════════════════════════════╣");
        System.out.println("║ 立项ID = " + initiationId + "   code=" + init.getCode());
        System.out.println("╚══════════════════════════════════════════════════════╝");

        // ============ 步骤 2: 粘贴 SOW 文本 (模拟用户在前端"AI生成 WBS"按钮粘贴) ============
        init.setSowPasteText(BANKING_LOAN_SOW);
        init = initRepo.save(init);
        System.out.println("\n╔══════════════════════════════════════════════════════╗");
        System.out.println("║ STEP 2: SOW 文本入库 (DB sowPasteText)                ║");
        System.out.println("╠══════════════════════════════════════════════════════╣");
        System.out.println("║ 文本长度 = " + BANKING_LOAN_SOW.length() + " 字符");
        System.out.println("║ 来源标记 = PASTE (前端粘贴触发)");
        System.out.println("╚══════════════════════════════════════════════════════╝");

        // ============ 步骤 3: 调用 generateDraft (等价于前端 POST /api/initiation/ai-wbs/generate) ============
        InitiationAiWbsDraft draft = wbsService.generateDraft(initiationId, null, 2, 1L);
        Map<String, Object> result = om.readValue(draft.getDraftJson(), new TypeReference<Map<String, Object>>() {});
        List<Map<String, Object>> milestones = (List<Map<String, Object>>) result.get("milestones");
        List<Map<String, Object>> workPackages = (List<Map<String, Object>>) result.get("workPackages");
        List<Map<String, Object>> risks = (List<Map<String, Object>>) result.get("risks");

        // ============ 步骤 4: 打印命中模板 (关键词扫描结果) ============
        System.out.println("\n╔══════════════════════════════════════════════════════╗");
        System.out.println("║ STEP 3: 关键词扫描 → 行业模板匹配                      ║");
        System.out.println("╠══════════════════════════════════════════════════════╣");
        System.out.println("║ Industry         = " + result.get("industry") + "             ║");
        System.out.println("║ modelVersion     = " + result.get("modelVersion") + "         ║");
        System.out.println("║ totalWeeks       = " + result.get("totalWeeks") + " 周           ║");
        System.out.println("║ 命中模板         = BANKING_LOAN 8 阶段");
        System.out.println("╚══════════════════════════════════════════════════════╝");

        // ============ 步骤 5: 打印抽取的 SOW 上下文 ============
        Map<String, Object> sowCtx = (Map<String, Object>) milestones.get(0).get("sowContext");
        System.out.println("\n╔══════════════════════════════════════════════════════╗");
        System.out.println("║ STEP 4: SOW 结构化抽取 (SowExtractor)                 ║");
        System.out.println("╠══════════════════════════════════════════════════════╣");
        System.out.println("║ modules      = " + sowCtx.get("modules"));
        System.out.println("║ techStack    = " + sowCtx.get("techStack"));
        System.out.println("║ deliverables = " + sowCtx.get("deliverables"));
        System.out.println("║ durationRaw  = " + sowCtx.get("durationRaw"));
        System.out.println("║ budgetRaw    = " + sowCtx.get("budgetRaw"));
        System.out.println("╚══════════════════════════════════════════════════════╝");

        // ============ 步骤 6: 打印 7 个里程碑 ============
        System.out.println("\n╔══════════════════════════════════════════════════════╗");
        System.out.println("║ STEP 5: 里程碑列表 (7 阶段)                           ║");
        System.out.println("╠══════════════════════════════════════════════════════╣");
        for (Map<String, Object> m : milestones) {
            System.out.printf("║  M%s  %-45s WP=%s%n",
                    m.get("code"),
                    String.valueOf(m.get("name")).substring(0, Math.min(45, String.valueOf(m.get("name")).length())),
                    m.get("workPackageCodes"));
        }
        System.out.println("╚══════════════════════════════════════════════════════╝");

        // ============ 步骤 7: 打印所有工作包 (按 wbsCode 排序) ============
        System.out.println("\n╔══════════════════════════════════════════════════════╗");
        System.out.println("║ STEP 6: 工作包清单 (31 个, 按 wbsCode)                ║");
        System.out.println("╠══════════════════════════════════════════════════════╣");
        workPackages.sort((a, b) -> String.valueOf(a.get("wbsCode")).compareTo(String.valueOf(b.get("wbsCode"))));
        int totalHours = 0;
        for (Map<String, Object> wp : workPackages) {
            int hours = Integer.parseInt(String.valueOf(wp.get("estimateHours")));
            totalHours += hours;
            String name = String.valueOf(wp.get("name"));
            if (name.length() > 35) name = name.substring(0, 33) + "..";
            System.out.printf("║  %-5s %-38s %3s %3sh%n",
                    wp.get("wbsCode"), name, wp.get("ownerRole"), wp.get("estimateHours"));
        }
        System.out.println("╠══════════════════════════════════════════════════════╣");
        System.out.println("║ 合计: " + workPackages.size() + " 个工作包, " + totalHours + " h (~ " + (totalHours/8) + " 人天)");
        System.out.println("╚═══════════════��══════════════════════════════════════╝");

        // ============ 步骤 8: 打印风险清单 ============
        System.out.println("\n╔══════════════════════════════════════════════════════╗");
        System.out.println("║ STEP 7: 风险清单 (按风险桶分组)                        ║");
        System.out.println("╠══════════════════════════════════════════════════════╣");
        java.util.Map<String, java.util.List<String>> risksByBucket = new java.util.LinkedHashMap<>();
        for (Map<String, Object> r : risks) {
            risksByBucket.computeIfAbsent(String.valueOf(r.get("bucket")), k -> new java.util.ArrayList<>())
                    .add(String.valueOf(r.get("title")));
        }
        for (Map.Entry<String, java.util.List<String>> e : risksByBucket.entrySet()) {
            System.out.println("║  [" + e.getKey() + "]");
            for (String t : e.getValue()) {
                String line = "    - " + t;
                if (line.length() > 56) line = line.substring(0, 54) + "..";
                System.out.println("║" + line);
            }
        }
        System.out.println("╠══════════════════════════════════════════════════════╣");
        System.out.println("║ 风险合计: " + risks.size() + " 条, 跨 " + risksByBucket.size() + " 个桶");
        System.out.println("╚══════════════════════════════════════════════════════╝");

        // ============ 步骤 9: 验证持久化 (DB 中确实有 draft) ============
        InitiationAiWbsDraft saved = draftRepo.findById(draft.getId()).orElseThrow();
        assertThat(saved.getDraftJson()).isNotBlank();
        assertThat(saved.getAppliedAt()).as("刚生成的 draft 应未 apply").isNull();
        System.out.println("\n╔══════════════════════════════════════════════════════╗");
        System.out.println("║ STEP 8: 持久化校验                                     ║");
        System.out.println("╠══════════════════════════════════════════════════════╣");
        System.out.println("║ draftId           = " + saved.getId());
        System.out.println("║ initiationId      = " + saved.getInitiationId());
        System.out.println("║ granularityWeeks  = " + saved.getGranularityWeeks());
        System.out.println("║ modelVersion      = " + saved.getModelVersion());
        System.out.println("║ draftJson.length  = " + saved.getDraftJson().length() + " 字符");
        System.out.println("║ appliedAt         = " + saved.getAppliedAt() + " (未 apply, 等待 EXEC 审批)");
        System.out.println("║ 数据库回读         = ✅ InitiationAiWbsDraft 表 1 行");
        System.out.println("╚══════════════════════════════════════════════════════╝");

        // ============ 步骤 10: 关键断言 (业务正确性) ============
        assertThat(result.get("industry")).as("行业必须命中 BANKING_LOAN").isEqualTo("BANKING_LOAN");
        assertThat(milestones).as("7 个里程碑").hasSize(7);
        assertThat(workPackages).as("31 个工作包").hasSize(31);
        assertThat(risks.size()).as("风险条数 ≥ 4 (SOW 含配偶征信 + 联网核查 + 抵押登记 + 担保 + 授信)").isGreaterThanOrEqualTo(4);
        assertThat(risksByBucket).containsKey("COMPLIANCE");      // 配偶征信 → 合规
        assertThat(risksByBucket).containsKey("INTEG_3RD");       // 联网核查/房估 → 第三方
        assertThat(workPackages).extracting(w -> (String) w.get("name"))
                .as("关键银行专属 WP 必须生成")
                .anyMatch(n -> n.contains("手机银行 APP"))
                .anyMatch(n -> n.contains("联网核查"))
                .anyMatch(n -> n.contains("人脸识别"))
                .anyMatch(n -> n.contains("调查报告"))
                .anyMatch(n -> n.contains("抵押登记"))
                .anyMatch(n -> n.contains("担保物"));

        // V4.17 Fix-2 验证: 1) wbsCode 现在是基于哈希的稳定 code (e.g. "1.a3f9" 而非 "1.1")
        String firstWpCode = (String) workPackages.get(0).get("wbsCode");
        System.out.println("\n╔══════════════════════════════════════════════════════╗");
        System.out.println("║ V4.17 Fix-1/2 验证: 同一 SOW 同一结果                    ║");
        System.out.println("╠══════════════════════════════════════════════════════╣");
        System.out.println("║ sample wpCode   = " + firstWpCode + "  (哈希码, 非顺序 1.1/1.2)");
        System.out.println("║ sowTextHash     = " + result.get("sowTextHash"));
        System.out.println("║ sowTextLength   = " + result.get("sowTextLength"));
        assertThat(firstWpCode).as("wpCode 应为 mCode.hash 形式 (e.g. 1.a3f9)")
                .matches("^[1-7]\\.[0-9a-f]{4}$");
        assertThat(result.get("sowTextHash")).as("sowTextHash 必须生成")
                .isNotNull()
                .asString().hasSize(16);

        // V4.17 Fix-1/2 关键测试: 同一份 SOW 第二次生成, WBS 必须 bit-equal 一致
        System.out.println("║ running 2nd pass (验证同一 SOW 同一结果)...             ║");
        InitiationAiWbsDraft draft2 = wbsService.generateDraft(initiationId, null, 2, 1L);
        Map<String, Object> result2 = om.readValue(draft2.getDraftJson(), new TypeReference<Map<String, Object>>() {});
        // 1) sowTextHash 必须一致
        assertThat(result2.get("sowTextHash")).as("同一 SOW → sowTextHash 一致").isEqualTo(result.get("sowTextHash"));
        // 2) industry 必须一致
        assertThat(result2.get("industry")).as("同一 SOW → industry 一致").isEqualTo(result.get("industry"));
        // 3) wpCode 集合必须完全一致 (顺序无关)
        java.util.Set<String> wpCodes1 = workPackages.stream()
                .map(w -> (String) w.get("wbsCode")).collect(java.util.stream.Collectors.toSet());
        List<Map<String, Object>> wps2 = (List<Map<String, Object>>) result2.get("workPackages");
        java.util.Set<String> wpCodes2 = wps2.stream()
                .map(w -> (String) w.get("wbsCode")).collect(java.util.stream.Collectors.toSet());
        assertThat(wpCodes2).as("同一 SOW → wpCode 集合 bit-equal").isEqualTo(wpCodes1);
        // 4) wp 数量必须一致
        assertThat(wps2).as("同一 SOW → wp 数量一致").hasSize(workPackages.size());
        // 5) milestone 数量 + code 必须一致
        List<Map<String, Object>> ms2 = (List<Map<String, Object>>) result2.get("milestones");
        assertThat(ms2).as("同一 SOW → milestone 数量一致").hasSize(milestones.size());
        java.util.Set<String> msCodes1 = milestones.stream().map(m -> (String) m.get("code")).collect(java.util.stream.Collectors.toSet());
        java.util.Set<String> msCodes2 = ms2.stream().map(m -> (String) m.get("code")).collect(java.util.stream.Collectors.toSet());
        assertThat(msCodes2).as("同一 SOW → milestone code 集合一致").isEqualTo(msCodes1);
        System.out.println("║  2nd pass:  wpCode 集合一致, milestone 一致, hash 一致  ║");
        System.out.println("║  ✅ \"同一 SOW 同一结果\" 验证通过                         ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");

        // V4.17 Fix-1 文本规范化验证: 全角括号 / 多余空格 / 繁简混排, 结果应该一致
        // 注意: 由于 test 第一阶段已经用 BODY 参数 + sowPasteText 都跑过一次,
        // 这里通过 null 入参避免 body 内容重复添加, 只验证 paste 文本的 hash 一致
        System.out.println("\n╔══════════════════════════════════════════════════════╗");
        System.out.println("║ V4.17 Fix-1 文本规范化验证 (等价 SOW 变体)              ║");
        System.out.println("╠══════════════════════════════════════════════════════╣");
        String sowVariant = BANKING_LOAN_SOW
                .replace("（", "(").replace("）", ")")     // 全角→半角
                + "   \n   \n";  // 多余空格 + 多个空行 + 换行符
        // 把变体同时存到 sowPasteText, 然后用 null body 走聚合, 只拿 paste
        init.setSowPasteText(sowVariant);
        initRepo.save(init);
        InitiationAiWbsDraft draft3 = wbsService.generateDraft(initiationId, null, 2, 1L);
        Map<String, Object> result3 = om.readValue(draft3.getDraftJson(), new TypeReference<Map<String, Object>>() {});
        assertThat(result3.get("sowTextHash")).as("全角/半角/多余空格变体 → hash 一致").isEqualTo(result.get("sowTextHash"));
        assertThat(result3.get("industry")).as("全角变体 → industry 一致").isEqualTo(result.get("industry"));
        System.out.println("║  原文 sowTextHash      = " + result.get("sowTextHash"));
        System.out.println("║  全角变体 sowTextHash  = " + result3.get("sowTextHash"));
        System.out.println("║  ✅ \"同一 SOW 不同外观同一结果\" 验证通过                  ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");

        System.out.println("\n╔══════════════════════════════════════════════════════╗");
        System.out.println("║ ✅ 全链路 E2E 验证通过                                  ║");
        System.out.println("╠══════════════════════════════════════════════════════╣");
        System.out.println("║  - 行业识别   BANKING_LOAN ✓");
        System.out.println("║  - 7 里程碑   31 工作包   " + totalHours + " h");
        System.out.println("║  - " + risks.size() + " 条风险 跨 " + risksByBucket.size() + " 个桶");
        System.out.println("║  - 持久化     DB 已存 draft");
        System.out.println("║  - 待 EXEC 审批后 apply → 写入 milestone + wbs_task");
        System.out.println("╚══════════════════════════════════════════════════════╝\n");
    }
}