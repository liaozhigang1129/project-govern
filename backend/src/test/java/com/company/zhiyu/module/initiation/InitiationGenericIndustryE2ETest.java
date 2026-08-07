package com.company.zhiyu.module.initiation;

import com.company.zhiyu.module.dict.InitiationStatus;
import com.company.zhiyu.module.dict.InitiationStatusRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.zhiyu.module.milestone.MilestoneRepository;
import com.company.zhiyu.module.wbs.WbsTaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V4.17 Fix-3 — 通用兜底行业 (CRM / ERP / 数据 / 云原生) E2E 验证
 *
 * <p>覆盖之前被你贴出来吐槽的"6 阶段 12 个 xxx-1工作包"分支:
 * 每个行业都生成 4-6 个真实业务 WP, 角色准确, 不再 FULLSTACK, 不再"xxx-1工作包"。</p>
 *
 * <p>每个行业一份 SOW (中性场景, 不故意含强行业词), 验证:
 * <ul>
 *   <li>industry 命中预期</li>
 *   <li>里程碑数 ≥ 6</li>
 *   <li>工作包数 ≥ 18 (每阶段 ≥ 3)</li>
 *   <li>所有 WP 名不含"-1工作包"/"-2工作包" 这种废话</li>
 *   <li>角色分布合理 (不能全部 FULLSTACK)</li>
 * </ul></p>
 */
@SpringBootTest
@ActiveProfiles("test")
class InitiationGenericIndustryE2ETest {

    @org.junit.jupiter.api.BeforeEach
    void seedStatus() {
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
    @Autowired ObjectMapper om;
    @Autowired InitiationStatusRepository statusRepo0;

    /** CRM 行业 SOW — 客户/商机/销售管理 */
    private static final String CRM_SOW = """
            客户关系管理系统升级项目主要需求概述
            项目背景：为更好的服务公司销售团队，提升客户管理效率，进一步优化销售流程，
            新设开发新一代客户关系管理平台。
            ㈠需求确认阶段。需要梳理现有销售流程，对比现状与未来流程差异，
            识别客户主数据、商机管理、销售漏斗等核心模块。
            ㈡原型设计阶段。设计客户主数据模型，梳理商机阶段定义与赢率规则，
            输出移动端原型和 UI 设计系统。
            ㈢系统开发阶段。开发客户主数据、商机管理、销售活动、合同订单等核心模块，
            配套移动端 APP 和客户 360 视图。
            ㈣联调测试阶段。完成内部接口和外部接口联调，包括短信、邮件、支付等。
            ㈤上线试运营阶段。完成 UAT 验收测试和灰度发布，配套培训手册。
            ㈥项目验收阶段。完成验收会��运维移交和财务结算。
            """;

    /** ERP 行业 SOW — 财务/供应链/生产 */
    private static final String ERP_SOW = """
            集团 ERP 系统实施项目主要需求概述
            项目背景：为提升集团财务、供应链、生产管理效率，统一全集团 ERP 平台。
            ㈠业务蓝图阶段。设计财务、供应链、生产全流程蓝图，完成现状调研和痛点清单，
            选型 SAP 或用友或金蝶 ERP 系统。
            ㈡系统配置阶段。完成主数据建模（物料、客户、供应商、会计科目），
            配置财务模块（总账、应收应付、资产）、供应链模块（采购、库存、销售）、
            生产模块（BOM、工艺路线、车间）。
            ㈢数据迁移阶段。完成历史数据迁移（物料、客户、供应商、单据），
            主数据初始化导入，期初余额录入试算平衡，配套接口开发。
            ㈣用户培训阶段。完成关键用户培训（财务、采购、销售、生产），操作手册编写。
            ㈤并行上线阶段。新旧系统并行 1 个月，处理用户工单，最终切换旧系统下线。
            ㈥项目验收阶段。完成验收会，运维移交，财务结算。
            """;

    /** 数据 行业 SOW — 指标/ETL/BI */
    private static final String DATA_SOW = """
            集团数据中台建设项目主要需求概述
            项目背景：构建集团统一指标体系和数据治理平台，支撑各业务线 BI 报表和自助分析。
            ㈠数据探查阶段。盘点业务数据源（库、表、接口、文件），完成空值/重复/分布/异常值探查，
            输出指标体系梳理。
            ㈡指标体系设计阶段。设计维度、度量、血缘关系，输出指标字典；
            设计 ODS/DWD/DWS/ADS 数据分层架构。
            ㈢ETL 开发阶段。完成数据抽取、清洗、转换、加载脚本，
            开发数据质量校验规则，处理 SCD 缓慢变化维。
            ㈣报表开发阶段。开发 BI 报表（Tableau/帆软），搭建自助分析平台，
            配套大屏可视化。
            ㈤性能调优阶段。完成 SQL 性能调优（慢查询、索引、分区），
            处理数据倾斜，优化存储成本。
            ㈥项目验收阶段。完成数据交付和用户培训，运维移交，财务结算。
            """;

    /** 云原生 行业 SOW — K8s/CI-CD/微服务 */
    private static final String CLOUDNATIVE_SOW = """
            云原生平台建设项目主要需求概述
            项目背景：将公司传统应用迁移到云原生架构，提升研发效率和系统稳定性。
            ㈠架构设计阶段。完成微服务/服务网格/事件驱动架构设计，
            技术选型 K8s、Istio、Kafka、Consul。
            ㈡环境搭建阶段。搭建多 AZ 高可用 K8s 集群、CI/CD 流水线（GitLab CI/ArgoCD）、
            镜像仓库（Harbor）、监控告警（Prometheus/Grafana）。
            ㈢应用容器化阶段。完成应用 Docker 容器化改造，编写 Helm Chart 模板化部署，
            接入服务网格 Istio。
            ㈣可观测性阶段。落地 Metrics/Logs/Traces 可观测性平台，
            完成日志聚合（ELK）和链路追踪（SkyWalking）。
            ㈤安全加固阶段。完成镜像扫描、RBAC、NetworkPolicy 安全加固，
            灾备演练 + 混沌工程演练。
            ㈥上线验收阶段。生产环境灰度发布，运维移交，财务结算。
            """;

    @Test
    @DisplayName("V4.17 Fix-3 — CRM 行业 E2E: 真实业务 WP, 角色多样, 不再 xxx-1工作包")
    void crmIndustry_fullE2E_pipeline() throws Exception {
        runIndustryE2E("CRM", CRM_SOW, List.of("客户", "商机", "销售"), 6);
    }

    @Test
    @DisplayName("V4.17 Fix-3 — ERP 行业 E2E: 主数据/财务/供应链 WP, 角色 BA/SR/PM")
    void erpIndustry_fullE2E_pipeline() throws Exception {
        runIndustryE2E("ERP", ERP_SOW, List.of("物料", "财务", "供应链", "采购"), 6);
    }

    @Test
    @DisplayName("V4.17 Fix-3 — 数据 行业 E2E: ETL/指标体系/BI 报表 WP")
    void dataIndustry_fullE2E_pipeline() throws Exception {
        runIndustryE2E("数据", DATA_SOW, List.of("ETL", "指标", "BI", "报表"), 6);
    }

    @Test
    @DisplayName("V4.17 Fix-3 — 云原生 行业 E2E: K8s/CI-CD/Istio WP, 角色 AR/SR")
    void cloudNativeIndustry_fullE2E_pipeline() throws Exception {
        runIndustryE2E("云原生", CLOUDNATIVE_SOW, List.of("K8s", "Istio", "CI/CD"), 6);
    }

    @SuppressWarnings("unchecked")
    private void runIndustryE2E(String expectedIndustry, String sow, List<String> requiredNameSnippets, int minMs) throws Exception {
        // ============ STEP 1: 立项 ============
        ProjectInitiation init = new ProjectInitiation();
        init.setCode("INIT-E2E-" + expectedIndustry + "-" + System.nanoTime());
        init.setTitle("E2E " + expectedIndustry + " 兜底行业验证");
        init.setApplicantId(1L);
        init.setDepartmentId(1L);
        init.setBackground("验证 Fix-3 通用行业分支");
        init.setGoals("证明通用兜底行业也生成真实业务 WP");
        init.setScope(sow);
        init = initiationService.submit(init);

        // ============ STEP 2: 粘贴 SOW ============
        init.setSowPasteText(sow);
        initRepo.save(init);

        // ============ STEP 3: 生成草稿 ============
        InitiationAiWbsDraft draft = wbsService.generateDraft(init.getId(), null, 2, 1L);
        Map<String, Object> result = om.readValue(draft.getDraftJson(), new TypeReference<Map<String, Object>>() {});
        List<Map<String, Object>> milestones = (List<Map<String, Object>>) result.get("milestones");
        List<Map<String, Object>> workPackages = (List<Map<String, Object>>) result.get("workPackages");

        // ============ 断言 ============
        System.out.println("\n╔══════════════════════════════════════════════════════╗");
        System.out.println("║ 行业 = " + expectedIndustry);
        System.out.println("║ 命中 industry = " + result.get("industry"));
        System.out.println("║ milestones = " + milestones.size() + " 个");
        System.out.println("║ workPackages = " + workPackages.size() + " 个");
        System.out.println("╚══════════════════════════════════════════════════════╝");

        assertThat(result.get("industry")).as(expectedIndustry + " 行业命中").isEqualTo(expectedIndustry);
        assertThat(milestones.size()).as(expectedIndustry + " 里程碑数 ≥ " + minMs).isGreaterThanOrEqualTo(minMs);
        assertThat(workPackages.size()).as(expectedIndustry + " 工作包数 ≥ 18").isGreaterThanOrEqualTo(18);

        // 关键断言: 不再出现 "xxx-1工作包"/"xxx-2工作包" 这种废话
        for (Map<String, Object> wp : workPackages) {
            String name = (String) wp.get("name");
            assertThat(name).as("WP 名不能是 'xxx-1工作包' 这种废话").doesNotMatch(".*-\\d+工作包.*");
            assertThat(name.length()).as("WP 名太短 (name=" + name + ")").isGreaterThanOrEqualTo(4);
        }

        // 角色多样: 至少出现 3 种角色
        java.util.Set<String> roles = new java.util.HashSet<>();
        for (Map<String, Object> wp : workPackages) {
            roles.add((String) wp.get("ownerRole"));
        }
        System.out.println("角色分布 = " + roles);
        assertThat(roles.size()).as(expectedIndustry + " 角色种类 ≥ 3 (不能全是 FULLSTACK)").isGreaterThanOrEqualTo(3);
        assertThat(roles).as("不能出现 FULLSTACK (UI 翻译不出来)").doesNotContain("FULLSTACK");

        // 关键 WP 必须出现 (证明 SOW 关键词门控生效)
        for (String snippet : requiredNameSnippets) {
            boolean anyMatch = workPackages.stream()
                    .anyMatch(w -> ((String) w.get("name")).contains(snippet));
            assertThat(anyMatch).as(expectedIndustry + " WP 含 '" + snippet + "'").isTrue();
        }

        // 打印 WP 名 (头 15 个)
        System.out.println("--- 头部 WP ---");
        workPackages.stream().limit(15).forEach(w ->
            System.out.println("  " + w.get("wbsCode") + "  " + w.get("name") + "  [" + w.get("ownerRole") + "]"));
    }
}