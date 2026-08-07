package com.company.pmo.module.initiation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.pmo.common.exception.BusinessException;
import com.company.pmo.module.dict.MilestoneStatus;
import com.company.pmo.module.dict.MilestoneStatusRepository;
import com.company.pmo.module.milestone.Milestone;
import com.company.pmo.module.milestone.MilestonePhase;
import com.company.pmo.module.milestone.MilestonePhaseRepository;
import com.company.pmo.module.milestone.MilestoneRepository;
import com.company.pmo.module.risk.RiskRuleCache;
import com.company.pmo.module.risk.RiskTemplate;
import com.company.pmo.module.wbs.WbsTask;
import com.company.pmo.module.wbs.WbsTaskRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class InitiationAiWbsService {

    private final InitiationAiWbsDraftRepository draftRepo;
    private final InitiationSowFileRepository sowFileRepo;
    private final ProjectInitiationRepository initiationRepo;
    private final InitiationSowFileService sowFileService;
    private final WbsTaskRepository wbsTaskRepository;
    private final MilestoneRepository milestoneRepository;
    private final MilestoneStatusRepository milestoneStatusRepo;
    private final MilestonePhaseRepository milestonePhaseRepo;
    private final ObjectMapper objectMapper;
    private final RiskRuleCache riskRuleCache;

    /** V4.26: 把 cache 注入 SowExtractor 静态字段 (使 extract() 优先走 DB 规则) */
    @PostConstruct
    void initRiskRuleCache() {
        SowExtractor.setRiskRuleCache(riskRuleCache);
        log.info("[InitiationAiWbsService] RiskRuleCache 注入成功 (signals={}, templates={}, buckets={})",
                riskRuleCache.getLastSignalCount(),
                riskRuleCache.getLastTemplateCount(),
                riskRuleCache.getLastBucketCount());
    }

    /** 默认模型版本号(便于 UI 区分) */
    private static final String DEFAULT_MODEL = "rule-engine-v1.0";

    /** V4.21: 最近一次 generateDraft 用过的 SOW (规范化后), 给 buildWp -> SowTraceUtil 复用 */
    private String lastSowForTrace = "";

    /** V4.21: 最近一次 detectAgents 的"全量 4 智能体 + 未命中原因"诊断, 给 Controller 顶层 unmatchedAgents 返 */
    private List<Map<String, Object>> lastUnmatchedAgents = List.of();

    /** V4.21: 最近一次 generateDraft 的"被裁掉"清单 (SOW 没出现但模板里有的), 给 Controller 顶层 hallucinationReport 返 */
    private List<Map<String, Object>> lastHallucinationReport = List.of();

    /**
     * 7 阶段(phase_id 字典)code → MilestonePhase.code 映射。
     * AI_AGENT 7 里程碑 → 1:1 映射;通用 6 里程碑 → 跳过 MAINTENANCE。
     */
    private static final Map<String, String> PHASE_BY_MILESTONE_NAME = Map.ofEntries(
            Map.entry("需求澄清与SOW评审", "INITIATION"),
            Map.entry("需求澄清", "INITIATION"),
            Map.entry("需求确认", "INITIATION"),
            Map.entry("AI智能体PoC验证", "REQUIREMENT"),
            Map.entry("PoC验证", "REQUIREMENT"),
            Map.entry("原型设计", "REQUIREMENT"),
            Map.entry("数据处理与多模态流水线", "DESIGN"),
            Map.entry("模型选型", "DESIGN"),
            Map.entry("系统设计", "DESIGN"),
            Map.entry("四大智能体开发", "DEVELOPMENT"),
            Map.entry("模型开发", "DEVELOPMENT"),
            Map.entry("系统开发", "DEVELOPMENT"),
            Map.entry("应用集成与联调", "TESTING"),
            Map.entry("联调测试", "TESTING"),
            Map.entry("测试", "TESTING"),
            Map.entry("灰度上线", "DEPLOY"),
            Map.entry("UAT上线", "DEPLOY"),
            Map.entry("UAT 上线", "DEPLOY"),
            Map.entry("项目验收与移交", "MAINTENANCE"),
            Map.entry("项目验收", "MAINTENANCE"),
            Map.entry("上线试运营", "DEPLOY"),
            Map.entry("上线试点", "DEPLOY"),
            Map.entry("上线验收", "MAINTENANCE"),
            // V4.17 Step 53-56: 金融行业 3 大模板的阶段名 → phase 映射
            // INSURANCE 保险
            Map.entry("投保与进件", "INITIATION"),
            Map.entry("核保审核", "REQUIREMENT"),
            Map.entry("调查定损", "DESIGN"),
            Map.entry("理赔处理", "DEVELOPMENT"),
            Map.entry("反欺诈风控", "DEVELOPMENT"),
            // SECURITIES 证券
            Map.entry("开户与客户管理", "INITIATION"),
            Map.entry("交易接入", "DESIGN"),
            Map.entry("风控合规", "DEVELOPMENT"),
            Map.entry("资金与结算", "DEVELOPMENT"),
            Map.entry("估值核算", "DEVELOPMENT"),
            Map.entry("投行与资管业务", "DEVELOPMENT"),
            // BANKING_CORE 银行核心
            Map.entry("客户管理", "INITIATION"),
            Map.entry("存款业务", "DEVELOPMENT"),
            Map.entry("贷款业务", "DEVELOPMENT"),
            Map.entry("总账核算", "DEVELOPMENT"),
            Map.entry("清结算与支付", "DEVELOPMENT"),
            Map.entry("监管报送", "DEPLOY")
    );

    /** 默认 PENDING 里程碑状态码 */
    private static final String DEFAULT_MS_STATUS = "PENDING";

    // 行业模板关键词 → 里程碑模板(每阶段交付 1 行描述,Step 2 详情/Step 3 详情用)
    // 注:AI 行业细分为 AI_AGENT(检测到智能体/Qwen3/AgentUniverse),走"4 智能体"专属拆解
    // V4.17 Step 48+: 加 BANKING_LOAN 银行/信贷业务专属模板(经营贷/抵押贷/消费贷/按揭等)
    // V4.17 Step 53+: 加 BANKING_CORE 银行核心系统 / SECURITIES 证券资管 / INSURANCE 保险
    //   检测优先级: BANKING_CORE > SECURITIES > INSURANCE > BANKING_LOAN > AI_AGENT > 云原生 > 数据 > ERP > CRM
    private static final Map<String, List<String>> INDUSTRY_TEMPLATES = Map.ofEntries(
            Map.entry("CRM", List.of("需求确认", "原型设计", "系统开发", "联调测试", "UAT 上线", "项目验收")),
            Map.entry("ERP", List.of("业务蓝图", "系统配置", "数据迁移", "用户培训", "并行上线", "项目验收")),
            Map.entry("数据", List.of("数据探查", "指标体系", "ETL 开发", "报表开发", "性能调优", "项目验收")),
            Map.entry("AI",   List.of("需求澄清", "PoC 验证", "模型选型", "模型开发", "应用集成", "灰度上线", "项目验收")),
            Map.entry("AI_AGENT",
                    List.of("需求澄清与SOW评审", "AI智能体PoC验证", "数据处理与多模态流水线",
                            "四大智能体开发", "应用集成与联调", "灰度上线", "项目验收与移交")),
            Map.entry("云原生", List.of("架构设计", "环境搭建", "应用容器化", "CI/CD", "可观测性", "项目验收")),
            // 银行/信贷业务 7 阶段模板 (经营贷/抵押贷/消费贷/按揭等)
            Map.entry("BANKING_LOAN",
                    List.of("客户申请与进件", "调查确认", "自动审批规则", "抵押登记",
                            "联调测试", "上线试运营", "项目验收")),
            // V4.17 Step 56: 银行核心系统改造 8 阶段 (存款/贷款/总账/清结算/1104/EAST)
            Map.entry("BANKING_CORE",
                    List.of("客户管理", "存款业务", "贷款业务", "总账核算",
                            "清结算与支付", "监管报送", "联调测试", "上线验收")),
            // V4.17 Step 55: 证券资管 8 阶段 (开户/交易/风控/资金结算/估值核算)
            Map.entry("SECURITIES",
                    List.of("开户与客户管理", "交易接入", "风控合规", "资金与结算",
                            "估值核算", "投行与资管业务", "联调测试", "上线验收")),
            // V4.17 Step 54: 保险 8 阶段 (投保/核保/调查/理赔/反欺诈)
            Map.entry("INSURANCE",
                    List.of("投保与进件", "核保审核", "调查定损", "理赔处理",
                            "反欺诈风控", "联调测试", "上线试点", "项目验收")),
            // V4.24: 银行资产托管 8 阶段 (托管协议/账户/估值/清算/信息披露/监管报送)
            Map.entry("BANKING_CUSTODY",
                    List.of("需求澄清与托管协议", "账户与头寸管理", "估值核算",
                            "资金清算与交收", "投资监督与信息披露", "机构服务平台",
                            "联调测试与监管报送", "上线验收与移交")),
            // V4.24: 供应链可视化 8 阶段 (采购/供应商/库存/在途/运输/异常预警/数据治理)
            Map.entry("SUPPLY_CHAIN",
                    List.of("主数据与供应商管理", "采购订单与库存可视化", "运输轨迹与在途跟踪",
                            "异常预警与监控看板", "数据治理与系统集成", "联调测试与上线验收",
                            "运维与持续优化", "项目验收与移交"))
    );

    /**
     * AI_AGENT 智能体识别(从 SOW 文本里嗅探 4 智能体是否出现)。
     * key=智能体 code (用于 WBS wbsCode 命名), value=(展示名, 关键词列表)
     * 命中即纳入 WBS,缺失则该智能体不出现在 WBS 里。
     */
    private static final List<Map<String, String>> AGENT_SIGNATURES = List.of(
            Map.of("code", "SUMMARY",  "name", "坐席小结",  "kw", "坐席小结|通话小结|语音小结"),
            Map.of("code", "QA",       "name", "语音质检",  "kw", "语音质检|通话质检|质检"),
            Map.of("code", "TAG",      "name", "语音打标",  "kw", "语音打标|通话打标|打标|标签"),
            Map.of("code", "FINREPT",  "name", "财报分析",  "kw", "财报分析|财报|年报|招股书")
    );

/**
     * AI_AGENT 模板里每阶段的工作包"切片规则"。
     * <p>阶段 code -> [WP 模板]</p>
     * <p>每个 WP 模板字段:
     * <ul>
     *   <li>name        - WP 名称,支持 {agent} 占位</li>
     *   <li>role        - 负责角色 (PM / AR / SR / DATA / QA / FR)</li>
     *   <li>hours       - 估算工时</li>
     *   <li>deliv       - 交付物描述,支持 {agent} 占位</li>
     *   <li>requiredKws - (可选) SOW 必现关键词列表。空/null = 总是生成;
     *                    非空 = 仅当 SOW 文本命中任一关键词才生成</li>
     * </ul>
     * </p>
     * <p>V4.17 (Step 44) 关键词门控: 解决 "SOW 没提语音/ASR 却仍生成 ASR/语音工作包" 问题。
     * 命中规则: SOW 文本中**包含任一关键词**即生成 (OR 逻辑,大小写不敏感)。</p>
     */
    private static final Map<String, List<Map<String, Object>>> AI_AGENT_MILESTONE_WORKPACKAGES = Map.ofEntries(
            // M1 需求澄清: PM 主导
            Map.entry("1", List.of(
                    // 总是生成: 任何 AI 项目都要做 SOW 评审
                    tpl("SOW 需求澄清会议(4 智能体对齐 + 验收口径)", "PM", 24, "SOW 评审纪要 + 需求清单", List.of()),
                    // qwen3 / AgentUniverse → AI 框架默认要求
                    tpl("行内大模型高码脚手架接入评估(qwen3 + AgentUniverse)", "AR", 40, "脚手架接入评估报告",
                            List.of("qwen", "千问", "agentuniverse", "大模型", "大语言模型", "智能体")),
                    // 数据样例: 仅在 SOW 出现"语音"或"财报/年报/PDF"时才生成
                    tpl("数据样例采集(坐席语音 + PDF 财报 脱敏样本)", "DATA", 32, "数据样例集(脱敏)",
                            List.of("语音", "通话", "财报", "年报", "招股书", "pdf", "录音")),
                    // 标签库: 仅在 SOW 出现"标签/打标/意愿度/情绪"时才生成
                    tpl("标签库设计(意愿度/情绪/行业 等)", "AR", 24, "标签库 v1.0",
                            List.of("标签", "打标", "意愿度", "情绪", "分类")),
                    // PoC 验收标准: 仅在 SOW 提到"可预测/可追溯/准确率"才生成
                    tpl("PoC 范围与可预测可追溯验收标准", "PM", 16, "PoC 验收标准书",
                            List.of("可预测", "可追溯", "准确率", "幻觉", "追溯"))
            )),
            // M2 PoC 验证: {agent} 占位 + 通用幻觉控制 PoC
            Map.entry("2", List.of(
                    // {agent} 由识别出的智能体展开;agents 为空则生成 1 份泛化版
                    tpl("{agent}智能体 PoC(单段样本 → 端到端跑通)", "AR", 40, "{agent} PoC 演示 + 评测指标", List.of()),
                    // 幻觉控制 PoC: 仅在 SOW 提到"可预测/可追溯/幻觉"才生成
                    tpl("幻觉控制 PoC(同任务多次执行结果一致性 + 来源标注)", "AR", 24, "可追溯验证报告",
                            List.of("可预测", "可追溯", "幻觉", "追溯", "准确率"))
            )),
            // M3 数据处理与多模态: 每条都加 requiredKws
            Map.entry("3", List.of(
                    // ASR: 仅在 SOW 出现"语音/通话/录音/ASR"才生成
                    tpl("语音识别(ASR)模块对接 + 性能压测", "SR", 48, "ASR 服务(可追溯 + 限流)",
                            List.of("语音", "通话", "录音", "asr")),
                    // PDF/财报: 仅在 SOW 出现"财报/年报/招股书/PDF/OCR"才生成
                    tpl("PDF/财报解析模块(PDFBox + OCR)", "SR", 48, "PDF 解析服务 + 测试用例",
                            List.of("财报", "年报", "招股书", "pdf", "ocr", "文档")),
                    // 数据清洗: 总是生成 (AI 项目必备)
                    tpl("数据清洗与去重 pipeline", "DATA", 40, "ETL 清洗脚本 + 数据质量报告", List.of()),
                    // 多模态: 仅在 SOW 出现"语音/视频/图像/多模态"才生成
                    tpl("多模态特征提取(语音 embedding + 文本 embedding)", "AR", 40, "特征提取服务",
                            List.of("语音", "视频", "图像", "图片", "多模态", "embedding")),
                    // 来源标注: 仅在 SOW 出现"可预测/可追溯/幻觉/来源"才生成
                    tpl("来源标注元数据规范(原文 span / 文件 / 偏移)", "AR", 24, "可追溯 schema v1.0",
                            List.of("可预测", "可追溯", "幻觉", "来源", "追溯"))
            )),
            // M4 四大智能体开发: {agent} 占位 + 编排 + 中间件 + 评测
            Map.entry("4", List.of(
                    tpl("{agent}智能体 — 提示工程 + few-shot 调优", "AR", 80, "{agent} Agent v1.0", List.of()),
                    tpl("4 智能体统一调度编排(AgentUniverse 编排)", "AR", 56, "编排服务 v1.0",
                            List.of("agentuniverse", "智能体", "agent")),
                    tpl("来源标注 / 幻觉抑制 中间件(全智能体共用)", "AR", 48, "可追溯中间件",
                            List.of("可预测", "可追溯", "幻觉", "来源", "追溯")),
                    tpl("智能体单元测试 + 端到端评测集", "QA", 40, "评测报告 + 100 测试用例", List.of()),
                    tpl("智能体性能压测(P99 < 3s)", "QA", 32, "压测报告", List.of())
            )),
            // M5 应用集成: 网关 + 前端 + 对接 + 联调 + 审计/可观测
            Map.entry("5", List.of(
                    tpl("智能体统一 API 网关", "SR", 56, "API 网关 + Swagger", List.of()),
                    // 前端工作台: 仅在 SOW 提到"前端/界面/工作台/网页"才生成
                    tpl("前端工作台(质检 / 打标 / 财报 三个页面)", "FR", 80, "前端工作台 v1.0",
                            List.of("前端", "界面", "工作台", "网页", "ui", "页面")),
                    // 坐席系统/财报对接: 仅在 SOW 出现"坐席/呼叫/财报/年报/征信/工商"才生成
                    tpl("与坐席系统 / 财报源 数据对接", "SR", 48, "集成接口 + 对接文档",
                            List.of("坐席", "呼叫", "财报", "年报", "招股书", "征信", "工商")),
                    tpl("联调 — 端到端走通 4 智能体", "QA", 32, "联调测试报告", List.of()),
                    tpl("权限 / 审计 / 数据脱敏(可追溯的支撑能力)", "SR", 32, "权限审计模块",
                            List.of("权限", "审计", "脱敏", "合规")),
                    tpl("可观测性(trace + 来源标注展示)", "SR", 24, "观测平台 + 来源面板",
                            List.of("可观测", "trace", "来源", "追溯"))
            )),
            // M6 灰度上线: 冒烟 + 灰度 + 反馈 + 可预测验收
            Map.entry("6", List.of(
                    tpl("预发环境冒烟(行内 5% 流量)", "SR", 16, "预发冒烟报告", List.of()),
                    tpl("灰度 5% → 20% → 50%(监控 + 回滚预案)", "SR", 24, "灰度发布报告", List.of()),
                    tpl("业务部门反馈收集(坐席/分析师)", "PM", 16, "业务反馈清单", List.of()),
                    tpl("可预测可追溯 业务验收(同一任务多次执行一致性)", "QA", 16, "可预测验收报告",
                            List.of("可预测", "可追溯", "幻觉", "追溯", "准确率"))
            )),
            // M7 验收移交: 验收会 + 培训 + 终验 + 复盘 + 结算
            Map.entry("7", List.of(
                    tpl("项目验收会 + 交付物清单核对", "PM", 8, "验收会议纪要", List.of()),
                    tpl("运维 / 业务团队 培训与移交", "PM", 8, "培训手册 + 移交清单", List.of()),
                    tpl("可追溯 / 可预测 终验(随机抽样 + 审计)", "QA", 8, "终验审计报告",
                            List.of("可预测", "可追溯", "幻觉", "追溯", "准确率")),
                    tpl("风险复盘 + 经验沉淀(AI 项目)", "PM", 8, "复盘文档", List.of()),
                    tpl("财务结算(按工时费率核算)", "PM", 8, "结算单", List.of())
            ))
    );

    /**
     * 构造一个 WP 模板 (V4.17 Step 44 工具方法)
/** 构造一个 WP 模板 (V4.17 Step 44 工具方法) */
    private static Map<String, Object> tpl(String name, String role, int hours, String deliv, List<String> requiredKws) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("role", role);
        m.put("hours", hours);
        m.put("deliv", deliv);
        m.put("requiredKws", requiredKws == null ? List.of() : requiredKws);
        return m;
    }

    /**
     * 判断 SOW 是否命中 requiredKws 列表中任一关键词 (OR 逻辑,大小写不敏感)
     * V4.17 Fix-2: 入参必须是 {@link SowExtractor#normalizeForAi(String)} 后的规范化文本
     * (调用方需自行规范化, 避免重复计算)
     */
    private static boolean sowMatchesAnyKw(String sowTextNormalized, List<String> requiredKws) {
        if (requiredKws == null || requiredKws.isEmpty()) return true;
        for (String kw : requiredKws) {
            if (sowTextNormalized.contains(kw.toLowerCase())) return true;
        }
        return false;
    }

    /**
     * V4.17 Fix-2: WP code 改为基于模板内容的稳定哈希, 不再依赖运行时 seq 自增。
     * <p>同一 SOW 同一模板永远生成同一个 wpCode, 跨进程/跨 JVM 也稳定。</p>
     * <p>算法: <code>"m.w" + sha256(tplName+role+requiredKws).substring(0,4)</code></p>
     * <p>示例: <code>"1.a3f9"</code> — 阶段 1 下第 a3f9 个 WP (按模板内容定位)。</p>
     *
     * <p>重要: 与 apply (Step 3) 写入 wbs_task.wbs_code 兼容 (原 wbsCode 也是 "1.1"/"2.3" 字符串)。
     * Step 3 apply 时若检测到 wbs_code 冲突会自动跳过 (幂等), 所以新 code 与旧 code 不冲突即可。</p>
     */
    private static String stableWpCode(String mCode, String tplName, String role, List<String> requiredKws) {
        String basis = tplName + "|" + role + "|"
                + (requiredKws == null ? "" : String.join(",", requiredKws));
        // 用 JDK 自带 MessageDigest (避免引 commons-codec 依赖)
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(basis.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String hex = String.format("%02x%02x", hash[0], hash[1]);
            return mCode + "." + hex;
        } catch (Exception e) {
            // 兜底: 用 Object.hashCode 截短 (不会发生, 但保留)
            return mCode + "." + Integer.toHexString(basis.hashCode() & 0xffff);
        }
    }

    /**
     * V4.17 Fix-2: 规范化后 SOW 文本的 SHA-256 短摘要 (16 hex 字符)。
     * <p>用于 draft.sowTextHash — 审计 + diff 入口:
     * <ul>
     *   <li>同一份 SOW 不论怎么编辑空白/全半角, hash 一致 → 同一份草稿</li>
     *   <li>SOW 实质内容变了, hash 也变 → 新草稿</li>
     * </ul>
     * </p>
     */
    private static String sha256Short(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", hash[i]));
            return sb.toString();
        } catch (Exception e) {
            return "0000000000000000";
        }
    }

    private static final int WP_DEFAULT_HOURS = 80;

    /**
     * BANKING_LOAN (银行/信贷) 行业专属 WP 模板 (V4.17 Step 48)
     * <p>7 阶段 -> [WP 模板], 每条带 requiredKws 关键词门控</p>
     * <p>阶段 code:
     *  - 1: 客户申请与进件
     *  - 2: 调查确认
     *  - 3: 自动审批规则
     *  - 4: 抵押登记
     *  - 5: 联调测试
     *  - 6: 上线试运营
     *  - 7: 项目验收
     * </p>
     */
    private static final Map<String, List<Map<String, Object>>> BANKING_LOAN_MILESTONE_WORKPACKAGES = Map.ofEntries(
            // M1 客户申请与进件
            Map.entry("1", List.of(
                    tpl("SOW 需求澄清会议(信贷产品口径 + 流程节点对齐)", "PM",  24, "SOW 评审纪要 + 需求清单", List.of()),
                    // 手机银行/H5 进件: 总是生成 (银行/信贷标配)
                    tpl("手机银行 APP 进件流程(申请 + 房产预估 + 证件上传)", "FR",  80, "APP 进件页面 + 接口对接", List.of()),
                    tpl("手机银行 H5 进件流程(配偶征信授权 + 短信验证)", "FR",  48, "H5 进件流程 + 授权页面", List.of()),
                    // 联网核查 + 人脸识别: 仅 SOW 含相关关键词才生成
                    tpl("联网核查对接(身份 + 户籍 + 工商)", "SR", 32, "联网核查接口对接 + 测试报告",
                            List.of("联网核查", "核查", "身份")),
                    tpl("人脸识别对接(活体检测 + OCR)", "SR", 32, "人脸识别 SDK 接入 + 准确率报告",
                            List.of("人脸识别", "活体", "ocr")),
                    // 文本签署 + 电子存证
                    tpl("电子合同 / 电子签章(CA 证书 + 时间戳)", "SR", 40, "电子签署服务 + 存证报告",
                            List.of("文本签署", "签署", "电子签", "电子存证", "存证", "ca")),
                    // 企业信息关联 / 路路通 / 标卡
                    tpl("企业信息关联 + 路路通 + 标卡同步", "SR", 32, "企业信息接口 + 路路通/标卡同步",
                            List.of("企业信息", "路路通", "标卡", "关联")),
                    tpl("电子存证(合规留痕 + 司法链对接)", "SR", 24, "存证服务 + 司法链同步",
                            List.of("电子存证", "存证", "司法链", "合规"))
            )),
            // M2 调查确认
            Map.entry("2", List.of(
                    tpl("调查确认业务流程 + 调查报告模板设计", "BA", 40, "调查报告模板 + 流程节点配置", List.of()),
                    // 房产信息建立及评估
                    tpl("房产信息建立 + 评估对接(房估宝/评估公司)", "SR", 40, "房产评估接口 + 评估报告",
                            List.of("房产", "评估", "房估")),
                    // 影像信息采集
                    tpl("影像信息采集(证件拍照 + 上传 + OCR)", "SR", 32, "影像采集 SDK + 上传服务",
                            List.of("影像", "拍照", "上传", "证件")),
                    // 调查报告撰写
                    tpl("移动端调查报告撰写(模板 + 自动填充)", "FR", 56, "调查报告移动端 + 自动填充",
                            List.of("调查报告", "报告")),
                    // 有权人审批 + 补件
                    tpl("有权人审批流程 + 补件流程", "SR", 32, "审批流引擎 + 补件通知",
                            List.of("审批", "有权人", "补件")),
                    // 进度查询
                    tpl("客户经理端进度查询(申请节点 + 审批状态)", "FR", 24, "进度查询页面 + 实时状态推送",
                            List.of("进度", "查询"))
            )),
            // M3 自动审批规则
            Map.entry("3", List.of(
                    tpl("自动预审规则引擎(硬规则 + 软规则)", "AR", 80, "规则引擎 v1.0 + 100 条规则",
                            List.of()),
                    tpl("自动终审规则(预审 + 反欺诈 + 风险评分)", "AR", 56, "终审规则 + 反欺诈模型对接",
                            List.of("终审", "反欺诈")),
                    tpl("额度计算模型(收入负债比 + 抵押率 + 风险定价)", "AR", 56, "额度计算引擎 + 利率表",
                            List.of("额度", "授信", "计算")),
                    tpl("审批决策日志 + 可解释性报表", "QA", 24, "决策日志 + 可解释性审计",
                            List.of("决策日志", "可解释", "审计"))
            )),
            // M4 抵押登记
            Map.entry("4", List.of(
                    // 担保物建立及评估
                    tpl("担保物信息建立 + 评估(房产/质押物)", "SR", 40, "担保物管理模块 + 评估报告",
                            List.of("担保", "抵押", "质押", "房产")),
                    tpl("担保合同模板 + 合同生成", "BA", 32, "担保合同模板库 + 合同生成器",
                            List.of("担保合同", "合同", "抵押")),
                    // 抵押登记
                    tpl("抵押登记对接(不动产登记中心/线上抵押)", "SR", 48, "抵押登记接口 + 登记证明",
                            List.of("抵押登记", "登记", "不动产")),
                    tpl("解押 / 续押 / 抵押变更流程", "SR", 32, "解押流程 + 续押规则",
                            List.of("解押", "续押", "变更")),
                    tpl("抵押状态实时同步 + 风险预警", "QA", 24, "状态监控 + 预警规则",
                            List.of("状态同步", "预警"))
            )),
            // M5 联调测试
            Map.entry("5", List.of(
                    tpl("内部系统联调(信贷核心 + 网关 + 账务)", "QA", 40, "内部联调测试报告", List.of()),
                    // 外部接口联调
                    tpl("外部接口联调(征信 + 银联 + 房估)", "QA", 48, "外部接口联调报告",
                            List.of("征信", "银联", "房估", "接口")),
                    tpl("端到端业务流联调(申请 → 调查 → 审批 → 抵押 → 放款)", "QA", 56, "E2E 联调报告", List.of()),
                    tpl("性能压测 + 并发测试(预审批 P99 < 3s)", "QA", 32, "性能压测报告",
                            List.of("性能", "压测", "并发")),
                    tpl("安全测试(渗透 + 数据脱敏 + 权限审计)", "QA", 32, "安全测试报告",
                            List.of("安全", "渗透", "脱敏", "权限"))
            )),
            // M6 上线试运营
            Map.entry("6", List.of(
                    tpl("预生产环境验证(影子表 + 灰度 5%)", "SR", 24, "预生产验证报告", List.of()),
                    tpl("客户经理试点运营(选 2 个支行试运行)", "PM",  16, "试点运营周报", List.of()),
                    tpl("数据迁移 + 历史数据补录(若有)", "DATA", 32, "数据迁移 + 补录脚本",
                            List.of("数据迁移", "迁移", "补录")),
                    tpl("客户/客户经理培训(操作手册 + 视频)", "PM", 16, "培训手册 + 培训视频",
                            List.of("培训")),
                    tpl("试运营问题收集 + 紧急修复(SLA 24h)", "SR", 16, "试运营问题清单", List.of())
            )),
            // M7 项目验收
            Map.entry("7", List.of(
                    tpl("项目验收会 + 交付物清单核对", "PM", 8, "验收会议纪要", List.of()),
                    tpl("客户/分支行 培训与移交", "PM", 8, "培训手册 + 移交清单", List.of()),
                    tpl("合规终验(征信查询合规 + 利率合规 + 合同合规)", "QA", 8, "合规验收报告",
                            List.of("合规", "征信")),
                    tpl("风险复盘 + 经验沉淀(信贷项目)", "PM", 8, "复盘文档", List.of()),
                    tpl("财务结算(按工时费率核算)", "PM", 8, "结算单", List.of())
            ))
    );

    /**
     * INSURANCE (保险) 行业专属 WP 模板 (V4.17 Step 54)
     * <p>8 阶段 -> [WP 模板], 每条带 requiredKws 关键词门控</p>
     * <p>阶段 code:
     *  - 1: 投保与进件
     *  - 2: 核保审核
     *  - 3: 调查定损
     *  - 4: 理赔处理
     *  - 5: 反欺诈风控
     *  - 6: 联调测试
     *  - 7: 上线试点
     *  - 8: 项目验收
     * </p>
     */
    private static final Map<String, List<Map<String, Object>>> INSURANCE_MILESTONE_WORKPACKAGES = Map.ofEntries(
            // M1 投保与进件
            Map.entry("1", List.of(
                    tpl("SOW 需求澄清会议(保险产品口径 + 投保流程对齐)", "PM", 24, "SOW 评审纪要 + 投保规则清单", List.of()),
                    tpl("投保登记模块(线上线下双通道: 个单/团单/续保)", "FR", 80, "投保登记页 + 后台", List.of()),
                    tpl("电子保单生成 + CA 签章 + 时间戳", "SR", 56, "电子保单服务 + 存证报告",
                            List.of("电子保单", "保单", "ca", "电子签章")),
                    tpl("经纪/代理对接(经纪通 + 代理通接口)", "SR", 48, "经纪通接口 + 代理通对接",
                            List.of("经纪", "代理", "经纪通")),
                    tpl("微信小程序 + H5 自助投保", "FR", 48, "投保小程序 + H5",
                            List.of("小程序", "h5", "微信", "自助")),
                    tpl("缴费模块(对接支付 + 银保通)", "SR", 32, "缴费服务 + 银保通对接",
                            List.of("缴费", "银保通", "支付"))
            )),
            // M2 核保审核
            Map.entry("2", List.of(
                    tpl("智能核保引擎(规则 + 模型 + 分级核保)", "AR", 80, "核保引擎 v1.0 + 100 条规则",
                            List.of("智能核保", "核保")),
                    tpl("人工核保工作台 + 分单 + 复核", "FR", 56, "人工核保工作台",
                            List.of("人工核保", "核保工作台")),
                    tpl("健康告知模块(问卷 + 电子签名)", "BA", 40, "健康告知模板 + 电子签名",
                            List.of("健康告知", "告知")),
                    tpl("财务核保 + 收入验证 + 反洗钱筛查", "QA", 32, "财务核保规则 + 反洗钱报告",
                            List.of("财务核保", "反洗钱", "财务"))
            )),
            // M3 调查定损
            Map.entry("3", List.of(
                    tpl("现场查勘 APP(移动端查勘 + GPS + 拍照 + 录音)", "FR", 80, "现场查勘 APP + 离线包",
                            List.of("查勘", "现场", "app")),
                    tpl("车险定损系统(配件库 + 工时费 + 维修方案)", "AR", 80, "车险定损模块 + 配件库",
                            List.of("定损", "车险")),
                    tpl("医疗调查 + 票据核验 + 伤残鉴定对接", "BA", 56, "医疗调查工作流 + 票据核验",
                            List.of("医疗调查", "医疗", "伤残", "调查")),
                    tpl("财产估损 + 公估公司对接", "SR", 48, "财产估损系统 + 公估对接",
                            List.of("财产", "估损", "公估"))
            )),
            // M4 理赔处理
            Map.entry("4", List.of(
                    tpl("理赔报案通道(线上 + 95518 + 微信)", "FR", 48, "报案通道 + 多渠道对接",
                            List.of("理赔", "报案")),
                    tpl("理赔立案 + 审核工作流(标准化作业)", "BA", 56, "理赔立案工作流 + SLA 配置",
                            List.of("理赔", "立案", "审核")),
                    tpl("理赔支付(银行打款 + 微信/支付宝 + 实时到账)", "SR", 48, "理赔支付服务 + 实时到账",
                            List.of("理赔", "支付", "打款")),
                    tpl("理赔回访 + 满意度 NPS + 投诉处理", "PM", 24, "回访话术 + 满意度报表",
                            List.of("回访", "nps", "满意度")),
                    tpl("理赔准备金提转结 + IFRS17 准则对接", "AR", 56, "准备金提转结 + 准则对接",
                            List.of("准备金", "ifrs17", "准则"))
            )),
            // M5 反欺诈风控
            Map.entry("5", List.of(
                    tpl("保险黑名单 + 灰名单 + 失信名单(对接央行/同业)", "SR", 40, "黑名单库 + 对接服务",
                            List.of("黑名单", "失信", "反欺诈")),
                    tpl("反欺诈模型(就诊频繁 + 出险时间集中 + 团伙作案)", "AR", 80, "反欺诈模型 v1.0",
                            List.of("反欺诈", "欺诈", "模型")),
                    tpl("风险预警 + 案件调查工作流", "BA", 40, "风险预警规则 + 调查工作流",
                            List.of("预警", "风险", "调查"))
            )),
            // M6 联调测试
            Map.entry("6", List.of(
                    tpl("内部核心联调(承保 + 理赔 + 再保 + 总账)", "QA", 40, "内部联调测试报告", List.of()),
                    tpl("外部接口联调(医院 + 维修厂 + 银保通 + 中保信)", "QA", 48, "外部接口联调报告",
                            List.of("医院", "维修厂", "银保通", "中保信", "接口")),
                    tpl("端到端业务流联调(投保 → 核保 → 出险 → 理赔)", "QA", 56, "E2E 联调报告", List.of()),
                    tpl("性能压测 + 银保通清算日 P99", "QA", 32, "性能压测报告",
                            List.of("性能", "压测")),
                    tpl("精算回归测试 + 准备金校验", "QA", 32, "精算回归报告",
                            List.of("精算", "回归", "准备金"))
            )),
            // M7 上线试点
            Map.entry("7", List.of(
                    tpl("灰度发布(分公司试点 5% → 20% → 100%)", "SR", 24, "灰度发布报告", List.of()),
                    tpl("渠道试点(中介/经纪/网销/电销 渠道验证)", "PM", 16, "渠道试点周报",
                            List.of("渠道", "中介", "经纪")),
                    tpl("日报月报对账 + 准备金回算", "DATA", 32, "对账报表 + 准备金回算",
                            List.of("对账", "回算", "日报")),
                    tpl("客户/代理/查勘员 培训 + 操作手册", "PM", 16, "培训手册 + 培训视频",
                            List.of("培训")),
                    tpl("试运营问题收集 + 紧急修复(SLA 24h)", "SR", 16, "试运营问题清单", List.of())
            )),
            // M8 项目验收
            Map.entry("8", List.of(
                    tpl("项目验收会 + 交付物清单核对", "PM", 8, "验收会议纪要", List.of()),
                    tpl("精算师 + 监管 验收", "QA", 16, "精算验收报告 + 监管意见",
                            List.of("精算", "监管")),
                    tpl("业务/IT 团队 培训与移交", "PM", 8, "培训手册 + 移交清单", List.of()),
                    tpl("风险复盘 + 经验沉淀", "PM", 8, "复盘文档", List.of()),
                    tpl("财务结算(按工时费率核算)", "PM", 8, "结算单", List.of())
            ))
    );

    /**
     * SECURITIES (证券/资管) 行业专属 WP 模板 (V4.17 Step 55)
     * <p>8 阶段 -> [WP 模板], 每条带 requiredKws 关键词门控</p>
     */
    private static final Map<String, List<Map<String, Object>>> SECURITIES_MILESTONE_WORKPACKAGES = Map.ofEntries(
            // M1 开户与客户管理
            Map.entry("1", List.of(
                    tpl("SOW 需求澄清会议(经纪业务口径 + 适当性管理对齐)", "PM", 24, "SOW 评审纪要 + 适当性规则清单", List.of()),
                    tpl("经纪开户流程(线上线下 + 身份证 + 银行卡绑定)", "FR", 80, "开户流程 + 三方存管绑定",
                            List.of("开户", "经纪")),
                    tpl("双录系统(录音录像 + 适当性匹配)", "SR", 56, "双录服务 + 存储 + 监管对接",
                            List.of("双录", "适当性")),
                    tpl("客户风险测评 + 风险等级匹配", "BA", 40, "风险测评问卷 + 评分模型",
                            List.of("风险测评", "风险等级", "适当性")),
                    tpl("客户分级管理(普通/专业/高净值/机构)", "BA", 32, "客户分级模型 + 权限矩阵",
                            List.of("客户分级", "分级")),
                    tpl("客户经理 APP(开户审核 + 客户管理 + 业绩看板)", "FR", 56, "客户经理 APP",
                            List.of("客户经理", "app"))
            )),
            // M2 交易接入
            Map.entry("2", List.of(
                    tpl("集中交易柜台(撮合 + 委托 + 成交回报)", "AR", 80, "交易柜台 v1.0 + 撮合引擎",
                            List.of("交易柜台", "柜台", "撮合")),
                    tpl("极速交易柜台(低延迟 + FPGA + 共置托管)", "AR", 80, "极速柜台 + 共置方案",
                            List.of("极速", "低延迟", "fpga")),
                    tpl("算法交易服务(TWAP/VWAP/POV/IS)", "AR", 56, "算法交易引擎 + 策略库",
                            List.of("算法交易", "算法", "twap")),
                    tpl("量化接口(Python SDK + 回测框架 + 数据 API)", "AR", 48, "量化 SDK + 回测平台",
                            List.of("量化", "python", "sdk")),
                    tpl("期权/期货交易模块(中金所/郑商所/上期所 对接)", "AR", 56, "期权交易模块 + 期货对接",
                            List.of("期权", "期货")),
                    tpl("OTC 一级市场 + 机构间报价", "AR", 48, "OTC 报价系统 + 机构对接",
                            List.of("otc", "报价"))
            )),
            // M3 风控合规
            Map.entry("3", List.of(
                    tpl("集中风控引擎(事前/事中/事后 限额 + 集中度)", "AR", 80, "风控引擎 v1.0",
                            List.of("集中风控", "风控")),
                    tpl("投资者适当性管理 + 产品风险匹配", "BA", 40, "适当性规则 + 产品风险评级",
                            List.of("适当性")),
                    tpl("反洗钱系统(大额可疑 + 客户尽调 + 报送人行)", "SR", 56, "反洗钱系统 + 人行报送",
                            List.of("反洗钱", "尽调")),
                    tpl("证监会监控中心数据报送", "SR", 40, "监控中心报送接口",
                            List.of("监控中心", "报送", "证监会")),
                    tpl("异常交易监控(幌骗 + 老鼠仓 + 频繁撤单)", "AR", 56, "异常交易监控模型",
                            List.of("异常交易", "幌骗", "老鼠仓")),
                    tpl("净资本监控 + 风险指标预警", "QA", 32, "净资本监控 + 风险指标看板",
                            List.of("净资本", "风险指标"))
            )),
            // M4 资金与结算
            Map.entry("4", List.of(
                    tpl("银证转账(资金账户 + 银行存管 + 实时划拨)", "SR", 48, "银证转账服务 + 三方存管对接",
                            List.of("银证", "三方存管")),
                    tpl("资金清算(二级清算 + 法人清算 + 多币种)", "AR", 56, "资金清算引擎",
                            List.of("清算", "法人清算")),
                    tpl("中登一二级资金账户对接", "SR", 40, "中登对接服务",
                            List.of("中登")),
                    tpl("跨境结算(QFII/RQFII/沪深港通 + 换汇)", "AR", 48, "跨境结算服务",
                            List.of("跨境", "qfii", "沪深港通")),
                    tpl("结算账户对账 + 差错处理", "QA", 24, "对账报表 + 差错处理流程",
                            List.of("对账"))
            )),
            // M5 估值核算
            Map.entry("5", List.of(
                    tpl("自营业务估值(股票/债券/衍生品/大宗商品)", "AR", 56, "自营估值引擎 v1.0",
                            List.of("自营", "估值")),
                    tpl("资管业务估值(净值 + 份额 + 业绩报酬)", "AR", 56, "资管估值引擎 + 净值核对",
                            List.of("资管", "净值")),
                    tpl("托管资产估值 + 第三方复核", "AR", 48, "托管估值 + 第三方复核",
                            List.of("托管", "估值"))
            )),
            // M6 投行与资管业务
            Map.entry("6", List.of(
                    tpl("投行承做(IPO/再融资/并购重组 项目管理)", "BA", 56, "投行承做工作台",
                            List.of("ipo", "再融资", "投行", "abs", "承做")),
                    tpl("ABS/ABN 资产证券化 + 现金流建模", "AR", 48, "ABS 系统 + 现金流模型",
                            List.of("abs", "资产证券化")),
                    tpl("资管产品备案 + 监管报送", "BA", 40, "资管备案系统 + 报送接口",
                            List.of("资管", "备案")),
                    tpl("���金运营(TA + 注册登记 + 信息披露)", "AR", 56, "基金运营 + TA 接口",
                            List.of("基金", "ta", "信息披露"))
            )),
            // M7 联调测试
            Map.entry("7", List.of(
                    tpl("沪深交易所仿真环境对接", "QA", 40, "仿真环境联调报告",
                            List.of("仿真", "交易所")),
                    tpl("证券业协会 + 中证报 接口测试", "QA", 32, "协会测试报告",
                            List.of("协会", "中证报")),
                    tpl("中登一二级资金账户联调", "QA", 40, "中登联调报告",
                            List.of("中登")),
                    tpl("银行三方存管联调(工农中建交 + 招行 + 中信)", "QA", 48, "存管联调报告",
                            List.of("存管", "三方存管")),
                    tpl("端到端业务流联调(开户 → 委托 → 成交 → 清算 → 交收)", "QA", 56, "E2E 联调报告", List.of())
            )),
            // M8 上线验收
            Map.entry("8", List.of(
                    tpl("灰度上线(营业部试点 → 全网)", "SR", 24, "灰度发布报告", List.of()),
                    tpl("监管现场验收(证监会 + 派出机构)", "QA", 16, "监管验收报告",
                            List.of("监管", "证监会")),
                    tpl("业务/IT 团队培训与移交", "PM", 8, "培训手册 + 移交清单", List.of()),
                    tpl("风险复盘 + 经验沉淀", "PM", 8, "复盘文档", List.of()),
                    tpl("财务结算(按工时费率核算)", "PM", 8, "结算单", List.of())
            ))
    );

    /**
     * BANKING_CORE (银行核心系统) 行业专属 WP 模板 (V4.17 Step 56)
     * <p>8 阶段 -> [WP 模板], 每条带 requiredKws 关键词门控</p>
     * <p>阶段 code:
     *  - 1: 客户管理
     *  - 2: 存款业务
     *  - 3: 贷款业务
     *  - 4: 总账核算
     *  - 5: 清结算与支付
     *  - 6: 监管报送
     *  - 7: 联调测试
     *  - 8: 上线验收
     * </p>
     */
    private static final Map<String, List<Map<String, Object>>> BANKING_CORE_MILESTONE_WORKPACKAGES = Map.ofEntries(
            // M1 客户管理
            Map.entry("1", List.of(
                    tpl("SOW 需求澄清会议(核心系统改造口径 + 客户主数据对齐)", "PM", 24, "SOW 评审纪要 + 客户主数据规范", List.of()),
                    tpl("个人客户主数据模块(CIF + 客户号合并 + 信息整合)", "SR", 80, "客户主数据 v1.0 + ETL",
                            List.of("客户主数据", "cif", "客户信息", "客户号")),
                    tpl("对公客户主数据模块(集团户 + 关系树 + 评级)", "SR", 80, "对公 CIF + 关系树",
                            List.of("对公", "集团户", "评级")),
                    tpl("客户经理关系维护 + 客户画像 + 黑白名单", "BA", 40, "客户经理关系 + 画像 + 名单",
                            List.of("客户经理", "客户画像", "黑白名单", "画像")),
                    tpl("客户合并/迁移 + 重复识别(MDM)", "SR", 56, "客户合并工具 + 重复识别",
                            List.of("客户合并", "mdm", "合并"))
            )),
            // M2 存款业务
            Map.entry("2", List.of(
                    tpl("活期存款 + 定期存款 + 通知存款", "BA", 56, "活期/定期/通知存款模块",
                            List.of("活期", "定期", "通知存款", "存款")),
                    tpl("大额存单 + 结构性存款 + 协定存款", "BA", 56, "大额存单/结构性/协定存款",
                            List.of("大额存单", "结构性存款", "协定存款")),
                    tpl("智能存款 + 靠档计息 + 阶梯利率", "AR", 40, "智能存款引擎 + 阶梯利率表",
                            List.of("智能存款", "靠档", "阶梯利率")),
                    tpl("存款利率市场化 + 利率定价 + LPR 联动", "AR", 40, "利率定价引擎 + LPR 联动",
                            List.of("利率", "定价", "lpr")),
                    tpl("存款应付利息计提 + 应付利息核对", "QA", 32, "应付利息计提 + 核对报表",
                            List.of("应付利息", "计提"))
            )),
            // M3 贷款业务
            Map.entry("3", List.of(
                    tpl("个人贷款 + 对公贷款 + 借据管理", "BA", 80, "个贷/对贷/借据模块",
                            List.of("个贷", "对公贷款", "借据", "贷款")),
                    tpl("五级分类 + 不良贷款 + 拨备计提", "AR", 56, "五级分类模型 + 拨备引擎",
                            List.of("五级分类", "不良", "拨备")),
                    tpl("资产保全 + 不良处置 + 重组/核销", "BA", 40, "资产保全 + 不良处置工作流",
                            List.of("资产保全", "不良处置", "重组", "核销")),
                    tpl("贷款定价 + LPR + 风险定价", "AR", 40, "贷款定价引擎",
                            List.of("贷款定价", "风险定价")),
                    tpl("贷后管理 + 预警 + 五级分类调整", "BA", 40, "贷后管理工作流 + 预警规则",
                            List.of("贷后", "预警", "贷后管理"))
            )),
            // M4 总账核算
            Map.entry("4", List.of(
                    tpl("总账核心 + 科目体系 + 会计分录", "SR", 80, "总账核心 v1.0 + 科目体系",
                            List.of("总账", "科目体系", "会计分录")),
                    tpl("损益结转 + 期末结账 + 年终结转", "SR", 56, "结转工作流 + 年结",
                            List.of("损益结转", "结账", "年结")),
                    tpl("报表生成(1104 + 银保监 + 内部管理)", "SR", 56, "报表引擎 + 1104 模板",
                            List.of("报表", "1104")),
                    tpl("科目调整 + 调账 + 红冲", "BA", 32, "科目调整工具 + 调账工作流",
                            List.of("科目调整", "调账", "红冲")),
                    tpl("总账对账 + 内外账核对", "QA", 32, "对账报表 + 差错处理",
                            List.of("对账", "内外账"))
            )),
            // M5 清结算与支付
            Map.entry("5", List.of(
                    tpl("人行支付清算(大额 + 小额 + 超级网银)", "SR", 80, "人行支付清算模块",
                            List.of("人行", "清算", "超级网银")),
                    tpl("银联接口 + 网联接口 + 二代支付", "SR", 56, "银联/网联/二代支付对接",
                            List.of("银联", "网联", "二代支付")),
                    tpl("行内转账 + 同行清算 + 实时到账", "SR", 40, "行内转账 + 实时到账",
                            List.of("行内", "同行", "实时到账")),
                    tpl("跨境支付 + 外汇结算 + 人民币跨境", "AR", 48, "跨境支付 + 外汇结算",
                            List.of("跨境", "外汇", "跨境支付")),
                    tpl("清算窗口 + 日终批量 + 差错处理", "QA", 32, "日终批量 + 差错处理流程",
                            List.of("清算窗口", "日终", "差错"))
            )),
            // M6 监管报送
            Map.entry("6", List.of(
                    tpl("1104 报表报送 + 银保监 EAST 数据", "SR", 80, "1104 + EAST 报送系统",
                            List.of("1104", "east")),
                    tpl("客户风险统计 + 大额可疑 + 反洗钱报送", "SR", 56, "风险统计 + 大额可疑",
                            List.of("客户风险", "大额可疑", "反洗钱")),
                    tpl("人行宏观审慎 + MPA 报送", "SR", 40, "宏观审慎 + MPA",
                            List.of("宏观审慎", "mpa")),
                    tpl("监管现场检查 + 非现场检查配合", "BA", 32, "现场检查支持工具",
                            List.of("现场检查", "非现场")),
                    tpl("监管政策解读 + 字段映射 + 版本管理", "BA", 24, "政策解读 + 字段映射",
                            List.of("政策", "字段映射", "版本"))
            )),
            // M7 联调测试
            Map.entry("7", List.of(
                    tpl("内部系统联调(信贷 + 存款 + 总账 + 渠道)", "QA", 40, "内部联调测试报告", List.of()),
                    tpl("人行接口联调 + 银联联调 + 网联联调", "QA", 48, "外部接口联调报告",
                            List.of("人行", "银联", "网联", "接口")),
                    tpl("端到端业务流联调(开户 → 存款 → 贷款 → 总账)", "QA", 56, "E2E 联调报告", List.of()),
                    tpl("性能压测 + 日终批量 P99", "QA", 32, "性能压测报告",
                            List.of("性能", "压测")),
                    tpl("灾备切换演练 + 业务连续性", "QA", 32, "灾备演练 + 业务连续性",
                            List.of("灾备", "演练", "业务连续性"))
            )),
            // M8 上线验收
            Map.entry("8", List.of(
                    tpl("预生产灰度(影子表 + 5% → 100%)", "SR", 24, "灰度发布报告", List.of()),
                    tpl("试点支行上线 + 全网推广", "PM", 16, "上线周报",
                            List.of("试点", "支行")),
                    tpl("监管现场验收 + 银保监 + 人行", "QA", 16, "监管验收报告",
                            List.of("监管", "银保监", "人行")),
                    tpl("业务/IT 团队培训与移交", "PM", 8, "培训手册 + 移交清单", List.of()),
                    tpl("风险复盘 + 经验沉淀 + 财务结算", "PM", 8, "复盘 + 结算单", List.of())
            ))
    );

    // V4.24: 银行资产托管 8 阶段 WP 模板
    // 设计原则: 关键词门控, 与苏州 SOW 强词对齐(托管/估值/清算/指令/对账/监管/信息披露)
    private static final Map<String, List<Map<String, Object>>> BANKING_CUSTODY_MILESTONE_WORKPACKAGES = Map.ofEntries(
            // M1 需求澄清与托管协议
            Map.entry("1", List.of(
                    tpl("SOW 评审与托管业务需求澄清", "PM", 24, "SOW 评审纪要 + 业务需求规格",
                            List.of("托管", "资产托管")),
                    tpl("托管协议模板(总协议 + 补充协议 + 操作备忘录)", "FR", 32, "托管协议模板 v1.0",
                            List.of("托管协议", "协议", "总协议")),
                    tpl("托管费率定价 + 费用结算规则", "AR", 24, "托管费率表 + 结算规则",
                            List.of("托管费率", "费率", "结算规则")),
                    tpl("客户尽职调查(KYC) + 反洗钱准入", "BA", 32, "KYC 工作流 + 客户准入清单",
                            List.of("尽职调查", "kyc", "反洗钱"))
            )),
            // M2 账户与头寸管理
            Map.entry("2", List.of(
                    tpl("托管账户开立 + 账户层级(产品/委托人/受托人)", "BA", 40, "托管账户开立模块",
                            List.of("托管账户", "账户", "产品")),
                    tpl("头寸管理 + 账户余额 + 余额对账", "SR", 40, "头寸管理 + 余额对账报表",
                            List.of("头寸", "账户余额", "银企对账")),
                    tpl("账户查询 + 账户明细 + 凭证打印", "DEV", 32, "账户查询模块 + 凭证",
                            List.of("账户查询", "账户明细", "凭证")),
                    tpl("存款/账户计息 + 计提规则", "SR", 32, "计息规则引擎 + 计提报表",
                            List.of("计息", "存款", "计提"))
            )),
            // M3 估值核算
            Map.entry("3", List.of(
                    tpl("净值估值 + 净值核算 + 单位净值", "SR", 56, "净值估值模块 + 估值表",
                            List.of("净值", "估值")),
                    tpl("估值核算引擎(支持股票/债券/基金/衍生品)", "SR", 80, "估值核算引擎 v1.0",
                            List.of("估值核算", "估值")),
                    tpl("估值表生成 + 净值披露", "QA", 32, "估值表 + 净值披露文件",
                            List.of("估值表", "��值", "信息披露")),
                    tpl("估值异常处理 + 重估 + 人工复核", "QA", 24, "估值异常处理流程",
                            List.of("重估", "复核"))
            )),
            // M4 资金清算与交收
            Map.entry("4", List.of(
                    tpl("场外划款指令处理(分拣/录入/审核/支付)", "SR", 56, "场外划款指令模块",
                            List.of("场外划款", "指令录入", "指令审核")),
                    tpl("资金清算 + 资金交收 + 日终批量", "SR", 48, "资金清算模块",
                            List.of("资金清算", "清算", "交收")),
                    tpl("交收日历 + 节假日调度", "BA", 24, "交收日历 + 调度规则",
                            List.of("交收日历", "日历")),
                    tpl("指令跟踪 + 电子传真 + 指令推送", "DEV", 32, "指令跟踪模块",
                            List.of("指令跟踪", "电子传真", "指令推送"))
            )),
            // M5 投资监督与信息披露
            Map.entry("5", List.of(
                    tpl("投资监督引擎(合规/比例/标的/久期)", "SR", 56, "投资监督引擎 v1.0",
                            List.of("投资监督", "监督")),
                    tpl("信息披露 + 信息披露文件 + 公告", "BA", 32, "信息披露模块 + 公告",
                            List.of("信息披露", "披露", "公告")),
                    tpl("风险指标监控 + 异常预警", "QA", 32, "风险监控 + 预警规则",
                            List.of("风险指标", "监控", "预警")),
                    tpl("监督报告 + 月报/季报/年报", "BA", 24, "监督报告模板",
                            List.of("监督报告", "月报"))
            )),
            // M6 机构服务平台
            Map.entry("6", List.of(
                    tpl("机构服务平台(管理人/托管人/委托人入口)", "DEV", 48, "机构服务平台 v1.0",
                            List.of("机构服务", "服务平台", "机构服务平台")),
                    tpl("通知公告 + 文件中心 + 任务分发", "DEV", 32, "通知公告 + 文件中心",
                            List.of("通知公告", "文件中心", "任务分发")),
                    tpl("报表中心 + 报表定制 + 自动推送", "DEV", 40, "报表中心 + 推送服务",
                            List.of("报表中心", "报表", "推送")),
                    tpl("权限管理 + 角色 + 用户管理", "QA", 24, "权限模型 + 用户管理",
                            List.of("用户管理", "权限", "角色"))
            )),
            // M7 联调测试与监管报送
            Map.entry("7", List.of(
                    tpl("内部联调(核心系统 + 网银 + OA)", "QA", 40, "内部联调测试报告", List.of()),
                    tpl("外部接口联调(银联/人行/三方)", "QA", 48, "外部接口联调报告",
                            List.of("人行", "银联", "三方存管")),
                    tpl("E2E 业务流联调(开户→划款→估值→清算)", "QA", 56, "E2E 联调报告", List.of()),
                    tpl("性能压测 + 监管报送(EAST/1104)接入", "QA", 40, "性能压测 + 监管报送",
                            List.of("性能", "压测", "east", "1104", "监管")),
                    tpl("灾备演练 + 业务连续性", "QA", 32, "灾备演练 + 业务连续性方案",
                            List.of("灾备", "演练", "业务连续性"))
            )),
            // M8 上线验收与移交
            Map.entry("8", List.of(
                    tpl("预生产灰度 + 影子账户演练", "SR", 24, "灰度发布报告", List.of()),
                    tpl("试点产品上线 + 全产品推广", "PM", 16, "上线周报", List.of()),
                    tpl("现场服务团队驻场 + 培训课件", "PM", 24, "培训课件 + 驻场记录",
                            List.of("驻场", "培训", "课件")),
                    tpl("知识转移 + 技能移交 + 文档移交", "PM", 24, "知识转移文档",
                            List.of("知识转移", "移交", "文档")),
                    tpl("售后服务 + 定期巡访 + 用户档案", "PM", 16, "售后服务方案",
                            List.of("巡访", "售后", "用户档案")),
                    tpl("风险复盘 + 经验沉淀 + 财务结算", "PM", 8, "复盘 + 结算单", List.of())
            ))
    );

    /**
     * V4.24: SUPPLY_CHAIN (供应链可视化/物流/采购/库存/在途) 行业专属 WP 模板
     * 覆盖"宝莱制造 8 工厂库存可视化""运输轨迹""异常预警""供应商管理"等典型场景
     * 共 7 阶段 × 4~5 WP ≈ 32 个 WP (关键词门控后,实际命中 ~20)
     */
    private static final Map<String, List<Map<String, Object>>> SUPPLY_CHAIN_MILESTONE_WORKPACKAGES = Map.ofEntries(
            // M1 主数据与供应商管理
            Map.entry("1", List.of(
                    tpl("供应商主数据建模 + 供应商档案 + 资质管理", "BA", 40, "供应商主数据模型 + 档案",
                            List.of("供应商", "主数据")),
                    tpl("供应商准入 + 评级 + 黑名单管理", "BA", 32, "供应商准入流程 + 评级规则",
                            List.of("供应商", "准入", "评级")),
                    tpl("物料主数据 + 物料分类 + 单位换算", "BA", 24, "物料主数据模型",
                            List.of("物料", "主数据")),
                    tpl("供应商门户 + 自助注册 + 资料维护", "DEV", 40, "供应商门户 v1.0",
                            List.of("供应商", "门户"))
            )),
            // M2 采购订单与库存可视化
            Map.entry("2", List.of(
                    tpl("采购订单管理 + 订单流转 + 审批", "DEV", 56, "采购订单模块 + 审批流",
                            List.of("采购订单", "采购")),
                    tpl("库存可视化 + 实时库存看板 + 多工厂汇总", "DEV", 64, "库存可视化看板 v1.0",
                            List.of("库存", "可视化")),
                    tpl("在途库存跟踪 + 在途量看板", "DEV", 40, "在途库存模块",
                            List.of("在途", "库存")),
                    tpl("库存预警 + 安全库存 + 补货建议", "SR", 32, "库存预警 + 补货建议规则",
                            List.of("库存", "预警", "补货")),
                    tpl("采购入库 + 收货 + 上架流程", "DEV", 32, "入库流程 + 收货单",
                            List.of("入库", "收货"))
            )),
            // M3 运输轨迹与在途跟踪
            Map.entry("3", List.of(
                    tpl("运输轨迹采集 + GPS/IoT 接入", "DEV", 56, "轨迹采集服务 + IoT 接入",
                            List.of("运输", "轨迹", "gps")),
                    tpl("轨迹可视化地图 + 在途热力图", "DEV", 48, "轨迹地图组件 v1.0",
                            List.of("轨迹", "可视化", "地图")),
                    tpl("承运商对接 + 第三方物流接口", "DEV", 40, "承运商对接模块",
                            List.of("承运商", "运输", "物流")),
                    tpl("运输异常监控 + 时效预警", "SR", 32, "运输异常监控规则",
                            List.of("运输", "异常", "监控"))
            )),
            // M4 异常预警与监控看板
            Map.entry("4", List.of(
                    tpl("异常预警引擎 + 阈值规则 + 多级告警", "SR", 56, "异常预警引擎 v1.0",
                            List.of("异常", "预警", "告警")),
                    tpl("实时监控看板 + 工厂维度总览", "DEV", 48, "实时看板 v1.0",
                            List.of("看板", "监控", "可视化")),
                    tpl("预警通知 + 邮件/IM 推送 + 工单联动", "DEV", 32, "预警通知 + 工单集成",
                            List.of("预警", "通知", "推送")),
                    tpl("预警复盘 + 误报率分析", "QA", 24, "预警复盘报告",
                            List.of("预警", "复盘"))
            )),
            // M5 数据治理与系统集成
            Map.entry("5", List.of(
                    tpl("ERP 主数据对接 + 物料/供应商同步", "DEV", 48, "ERP 对接服务",
                            List.of("erp", "主数据", "对接")),
                    tpl("MES 工序数据接入 + 报工数据回传", "DEV", 40, "MES 接入服务",
                            List.of("mes", "工序", "报工")),
                    tpl("WMS/TMS 数据接口 + 库存/轨迹双向同步", "DEV", 48, "WMS/TMS 接口",
                            List.of("wms", "tms", "接口", "对接")),
                    tpl("数据治理 + 主数据质量 + 一致性校验", "QA", 32, "数据治理报告",
                            List.of("数据治理", "主数据", "一致性")),
                    tpl("主数据管理平台 + 变更审计", "DEV", 32, "主数据管理 v1.0",
                            List.of("主数据", "管理"))
            )),
            // M6 联调测试与上线验收
            Map.entry("6", List.of(
                    tpl("内部联调(可视化看板 + 预警引擎 + ERP/MES 接口)", "QA", 48, "内部联调报告",
                            List.of()),
                    tpl("E2E 业务流(采购→入库→在途→入库→库存更新)", "QA", 56, "E2E 联调报告",
                            List.of()),
                    tpl("性能压测 + 8 工厂并发动态查询", "QA", 40, "性能压测报告",
                            List.of("性能", "压测")),
                    tpl("UAT 用户验收 + 工厂试运行", "QA", 40, "UAT 报告 + 试运行反馈",
                            List.of("uat", "验收")),
                    tpl("安全合规 + 数据脱敏 + 等保对接", "QA", 32, "安全合规报告",
                            List.of("安全", "脱敏", "等保"))
            )),
            // M7 运维与持续优化
            Map.entry("7", List.of(
                    tpl("运维值班 + 监控告警 + 故障响应", "PM", 24, "运维 SOP + 值班表",
                            List.of("运维", "监控", "告警")),
                    tpl("供应商自助培训 + 工厂用户培训", "PM", 24, "培训课件 + 培训记录",
                            List.of("培训", "课件")),
                    tpl("知识转移 + 文档移交 + 维护手册", "PM", 16, "知识转移文档",
                            List.of("知识转移", "移交", "文档")),
                    tpl("月度巡检 + 性能基线 + 优化建议", "PM", 16, "巡检报告",
                            List.of("巡检", "性能")),
                    tpl("需求变更 + 业务规则迭代支持", "PM", 16, "需求变更单 + 迭代计划",
                            List.of("变更", "迭代"))
            ))
    );

    /** AI 生成 Step 2 主入口
     *  - sowText: 可空(粘贴文本可单独触发)
     *  - 优先用 body 传的 sowText,若空则从立项的 sowPasteText + SOW 文件聚合
     *  - 任意一个来源非空都可触发,无需双开
     */
@Transactional
    public InitiationAiWbsDraft generateDraft(Long initiationId, String sowText, Integer granularityWeeks, Long actorId) {
        int weeks = granularityWeeks == null || granularityWeeks <= 0 ? 2 : Math.min(granularityWeeks, 8);

        // 聚合 SOW 来源:body sowText > DB sowPasteText > SOW 文件(纯文本可读)
        String aggregated = resolveSowText(initiationId, sowText);
        if (aggregated == null || aggregated.isBlank()) {
            throw new BusinessException(400, "SOW text is required (provide sowText or upload file / save paste text)");
        }

        // V4.17 Fix-1: 文本规范化 — 让"同一 SOW 同一结果"。
        // 规范化后的 sowForAi 用于所有下游: industry / 关键词门控 / SowExtractor / 模板遍历
        String sowForAi = SowExtractor.normalizeForAi(aggregated);

        // V4.17 Fix-2: sowTextHash — 审计 + diff 入口
        String sowTextHash = sha256Short(sowForAi);

        // 1) 关键词匹配 → 选定行业模板
        String industry = detectIndustry(sowForAi);
        List<String> milestoneNames = INDUSTRY_TEMPLATES.getOrDefault(industry, INDUSTRY_TEMPLATES.get("CRM"));

        // 1.5) V4.17 SOW 结构化抽取 (Step 41 接入)
        //      把 SOW 里的真实业务模块 / 技术栈 / 工期 / 预算 / 交付物 抽出来, 用于:
        //      - 里程碑名追加 SOW 实际模块 (让"需求澄清"变成"需求澄清: 智能派单 / 意图识别")
        //      - 通用模板(非 AI_AGENT)每个里程碑生成 3-5 个带 SOW 上下文的工作包
        //      - 风险桶按抽取的 riskSignals 触发 (见 generateRisks 内部)
        SowExtractor.Extraction ext = SowExtractor.extract(aggregated, industry);

        // 2) 构造 milestones + workPackages
        //    - AI_AGENT: 走 AI_AGENT_MILESTONE_WORKPACKAGES 模板,4 智能体各代 1 份
        //    - 其它行业: 每里程碑拆 3-5 个带 SOW 上下文的工作包 (Step 41 升级)
        List<Map<String, Object>> milestones = new ArrayList<>();
        List<Map<String, Object>> workPackages = new ArrayList<>();
        int cumulativeWeek = 0;
        List<Map<String, String>> agents = "AI_AGENT".equals(industry) ? detectAgents(sowForAi) : List.of();
        // V4.21: 记下"全 4 智能体 + 未命中原因", 给 Controller 顶层 unmatchedAgents 返
        lastUnmatchedAgents = "AI_AGENT".equals(industry)
                ? buildUnmatchedAgentsReport(sowForAi)
                : List.of();
        // V4.21: 重置本次 hallucinationReport
        lastHallucinationReport = new ArrayList<>();
        // V4.21: 把规范化 SOW 透传给 buildWp -> SowTraceUtil
        lastSowForTrace = sowForAi;

        // 选前 2 个最关键的 SOW 模块名 (用于里程碑命名 + 通用 WP 上下文)
        // 例: [智能派单, 意图识别, 坐席小结] → 显示为 "智能派单 / 意图识别"
        String moduleContext = formatModuleContext(ext.modules());
        // V4.20 守门白名单: 改进版用整段子串匹配, 替代 2-gram set 命中率
        Set<String> sowTokens = SowTokenGuard.tokens(aggregated); // 保留做兜底

        for (int i = 0; i < milestoneNames.size(); i++) {
            String mCode = String.valueOf(i + 1);
            String mName = milestoneNames.get(i);
            // 里程碑名追加 SOW 实际模块 (Phase 1 / 2 / 7 不强加, 避免冗长)
            // 设计: 中间几个执行里程碑(2~5) 拼接 SOW 模块, 让用户一眼看到 AI 读了 SOW
            // V4.20 守门: enrichMilestoneName 内部用 sowTokens 过滤, 不在 SOW 里的短语不写进里程碑名
            String displayName = enrichMilestoneName(mName, moduleContext, i, milestoneNames.size(), aggregated);
            List<String> wpCodes = new ArrayList<>();

if ("AI_AGENT".equals(industry)) {
                // AI_AGENT 模板: 通用工作包 + 4 智能体各代一份 (V4.17 Step 44: 加 requiredKws 关键词门控)
                List<Map<String, Object>> templates = AI_AGENT_MILESTONE_WORKPACKAGES.getOrDefault(mCode, List.of());
                int seqIgnored = 1;   // V4.17 Fix-2: 已改用 stableWpCode, seq 不再使用
                for (Map<String, Object> tplObj : templates) {
                    String tplName = (String) tplObj.get("name");
                    String role    = (String) tplObj.get("role");
                    int hours      = (int) tplObj.get("hours");
                    String deliv   = (String) tplObj.get("deliv");
                    @SuppressWarnings("unchecked")
                    List<String> requiredKws = (List<String>) tplObj.getOrDefault("requiredKws", List.of());

                    // 关键词门控: 不在 SOW 里出现的 WP 模板直接跳过
                    if (!sowMatchesAnyKw(sowForAi, requiredKws)) {
                        log.debug("[GenerateDraft] skip WP '{}' (requiredKws={} not matched in SOW)",
                                tplName, requiredKws);
                        continue;
                    }

                    if (tplName.contains("{agent}")) {
                        // 智能体占位: 每个识别出的智能体展开 1 份
                        if (agents.isEmpty()) {
                            // 兜底: 4 智能体都没识别出来,也至少生成 1 个泛化版
                            // V4.17 Fix-2: 哈希 code 保留 {agent} 在 name 里,避免同模板不同 agent 撞码
                            String wpCode = stableWpCode(mCode, tplName + "::defaultAgent", role, requiredKws);
                            wpCodes.add(wpCode);
                            workPackages.add(buildWpWithTrace(wpCode,
                                    tplName.replace("{agent}", "智能体"),
                                    role, hours, deliv.replace("{agent}", "智能体"),
                                    requiredKws, "AGENT_FALLBACK", lastSowForTrace));
                            // V4.21: 记一笔 hallucination — 4 智能体都没识别出来
                            lastHallucinationReport.add(ofMap(
                                "type", "AGENT_FALLBACK",
                                "wpName", tplName.replace("{agent}", "智能体"),
                                "reason", "SOW 中未识别出任何智能体 (坐席小结/语音质检/语音打标/财报分析 均未命中)"
                            ));
                        } else {
                            for (Map<String, String> a : agents) {
                                String wpCode = stableWpCode(mCode, tplName.replace("{agent}", a.get("name")), role, requiredKws);
                                wpCodes.add(wpCode);
                                workPackages.add(buildWpWithTrace(wpCode,
                                        tplName.replace("{agent}", a.get("name")),
                                        role, hours, deliv.replace("{agent}", a.get("name")),
                                        requiredKws, "AGENT_HIT:" + a.get("code"), lastSowForTrace));
                            }
                        }
                    } else {
                        // 通用工作包
                        String wpCode = stableWpCode(mCode, tplName, role, requiredKws);
                        wpCodes.add(wpCode);
                        workPackages.add(buildWpWithTrace(wpCode, tplName, role, hours, deliv,
                                requiredKws, "REQUIRED_KW", lastSowForTrace));
                    }
                }
            } else if ("BANKING_LOAN".equals(industry)) {
                // BANKING_LOAN 模板 (V4.17 Step 48): 银行/信贷专属 WP + 关键词门控
                List<Map<String, Object>> templates = BANKING_LOAN_MILESTONE_WORKPACKAGES.getOrDefault(mCode, List.of());
                int seqIgnored = 1;   // V4.17 Fix-2: 已改用 stableWpCode, seq 不再使用
                for (Map<String, Object> tplObj : templates) {
                    String tplName = (String) tplObj.get("name");
                    String role    = (String) tplObj.get("role");
                    int hours      = (int) tplObj.get("hours");
                    String deliv   = (String) tplObj.get("deliv");
                    @SuppressWarnings("unchecked")
                    List<String> requiredKws = (List<String>) tplObj.getOrDefault("requiredKws", List.of());

                    if (!sowMatchesAnyKw(sowForAi, requiredKws)) {
                        // V4.21: 记一笔 hallucination — 模板里有但 SOW 不命中, 给出未命中原因
                        lastHallucinationReport.add(ofMap(
                            "type", "REQUIRED_KW_MISS",
                            "industry", "BANKING_LOAN",
                            "milestoneCode", mCode,
                            "wpName", tplName,
                            "requiredKws", requiredKws,
                            "reason", "SOW 未命中任一 requiredKws: " + String.join(" / ", requiredKws)
                        ));
                        log.debug("[GenerateDraft] skip WP '{}' (requiredKws={} not matched in SOW)",
                                tplName, requiredKws);
                        continue;
                    }

                    String wpCode = stableWpCode(mCode, tplName, role, requiredKws);
                    wpCodes.add(wpCode);
                    workPackages.add(buildWpWithTrace(wpCode, tplName, role, hours, deliv,
                            requiredKws, "REQUIRED_KW", lastSowForTrace));
                }
            } else if ("BANKING_CORE".equals(industry)) {
                // BANKING_CORE 模板 (V4.17 Step 56): 银行核心系统改造 + 关键词门控
                List<Map<String, Object>> templates = BANKING_CORE_MILESTONE_WORKPACKAGES.getOrDefault(mCode, List.of());
                int seqIgnored = 1;   // V4.17 Fix-2: 已改用 stableWpCode, seq 不再使用
                for (Map<String, Object> tplObj : templates) {
                    String tplName = (String) tplObj.get("name");
                    String role    = (String) tplObj.get("role");
                    int hours      = (int) tplObj.get("hours");
                    String deliv   = (String) tplObj.get("deliv");
                    @SuppressWarnings("unchecked")
                    List<String> requiredKws = (List<String>) tplObj.getOrDefault("requiredKws", List.of());

                    if (!sowMatchesAnyKw(sowForAi, requiredKws)) {
                        lastHallucinationReport.add(ofMap(
                            "type", "REQUIRED_KW_MISS",
                            "industry", "BANKING_CORE",
                            "milestoneCode", mCode,
                            "wpName", tplName,
                            "requiredKws", requiredKws,
                            "reason", "SOW 未命中任一 requiredKws: " + String.join(" / ", requiredKws)
                        ));
                        log.debug("[GenerateDraft] [BANKING_CORE] skip WP '{}' (requiredKws={} not matched)",
                                tplName, requiredKws);
                        continue;
                    }

                    String wpCode = stableWpCode(mCode, tplName, role, requiredKws);
                    wpCodes.add(wpCode);
                    workPackages.add(buildWpWithTrace(wpCode, tplName, role, hours, deliv,
                            requiredKws, "REQUIRED_KW", lastSowForTrace));
                }
            } else if ("SECURITIES".equals(industry)) {
                // SECURITIES 模板 (V4.17 Step 55): 证券/资管专属 WP + 关键词门控
                List<Map<String, Object>> templates = SECURITIES_MILESTONE_WORKPACKAGES.getOrDefault(mCode, List.of());
                int seqIgnored = 1;   // V4.17 Fix-2: 已改用 stableWpCode, seq 不再使用
                for (Map<String, Object> tplObj : templates) {
                    String tplName = (String) tplObj.get("name");
                    String role    = (String) tplObj.get("role");
                    int hours      = (int) tplObj.get("hours");
                    String deliv   = (String) tplObj.get("deliv");
                    @SuppressWarnings("unchecked")
                    List<String> requiredKws = (List<String>) tplObj.getOrDefault("requiredKws", List.of());

                    if (!sowMatchesAnyKw(sowForAi, requiredKws)) {
                        lastHallucinationReport.add(ofMap(
                            "type", "REQUIRED_KW_MISS",
                            "industry", "SECURITIES",
                            "milestoneCode", mCode,
                            "wpName", tplName,
                            "requiredKws", requiredKws,
                            "reason", "SOW 未命中任一 requiredKws: " + String.join(" / ", requiredKws)
                        ));
                        log.debug("[GenerateDraft] [SECURITIES] skip WP '{}' (requiredKws={} not matched)",
                                tplName, requiredKws);
                        continue;
                    }

                    String wpCode = stableWpCode(mCode, tplName, role, requiredKws);
                    wpCodes.add(wpCode);
                    workPackages.add(buildWpWithTrace(wpCode, tplName, role, hours, deliv,
                            requiredKws, "REQUIRED_KW", lastSowForTrace));
                }
            } else if ("INSURANCE".equals(industry)) {
                // INSURANCE 模板 (V4.17 Step 54): 保险专属 WP + 关键词门控
                List<Map<String, Object>> templates = INSURANCE_MILESTONE_WORKPACKAGES.getOrDefault(mCode, List.of());
                int seqIgnored = 1;   // V4.17 Fix-2: 已改用 stableWpCode, seq 不再使用
                for (Map<String, Object> tplObj : templates) {
                    String tplName = (String) tplObj.get("name");
                    String role    = (String) tplObj.get("role");
                    int hours      = (int) tplObj.get("hours");
                    String deliv   = (String) tplObj.get("deliv");
                    @SuppressWarnings("unchecked")
                    List<String> requiredKws = (List<String>) tplObj.getOrDefault("requiredKws", List.of());

                    if (!sowMatchesAnyKw(sowForAi, requiredKws)) {
                        lastHallucinationReport.add(ofMap(
                            "type", "REQUIRED_KW_MISS",
                            "industry", "INSURANCE",
                            "milestoneCode", mCode,
                            "wpName", tplName,
                            "requiredKws", requiredKws,
                            "reason", "SOW 未命中任一 requiredKws: " + String.join(" / ", requiredKws)
                        ));
                        log.debug("[GenerateDraft] [INSURANCE] skip WP '{}' (requiredKws={} not matched)",
                                tplName, requiredKws);
                        continue;
                    }

                    String wpCode = stableWpCode(mCode, tplName, role, requiredKws);
                    wpCodes.add(wpCode);
                    workPackages.add(buildWpWithTrace(wpCode, tplName, role, hours, deliv,
                            requiredKws, "REQUIRED_KW", lastSowForTrace));
                }
            } else if ("BANKING_CUSTODY".equals(industry)) {
                // V4.24: BANKING_CUSTODY 模板 (资产托管: 苏州银行/招商银行/宁波银行 等)
                List<Map<String, Object>> templates = BANKING_CUSTODY_MILESTONE_WORKPACKAGES.getOrDefault(mCode, List.of());
                for (Map<String, Object> tplObj : templates) {
                    String tplName = (String) tplObj.get("name");
                    String role    = (String) tplObj.get("role");
                    int hours      = (int) tplObj.get("hours");
                    String deliv   = (String) tplObj.get("deliv");
                    @SuppressWarnings("unchecked")
                    List<String> requiredKws = (List<String>) tplObj.getOrDefault("requiredKws", List.of());

                    if (!sowMatchesAnyKw(sowForAi, requiredKws)) {
                        lastHallucinationReport.add(ofMap(
                            "type", "REQUIRED_KW_MISS",
                            "industry", "BANKING_CUSTODY",
                            "milestoneCode", mCode,
                            "wpName", tplName,
                            "requiredKws", requiredKws,
                            "reason", "SOW 未命中任一 requiredKws: " + String.join(" / ", requiredKws)
                        ));
                        log.debug("[GenerateDraft] [BANKING_CUSTODY] skip WP '{}' (requiredKws={} not matched)",
                                tplName, requiredKws);
                        continue;
                    }

                    String wpCode = stableWpCode(mCode, tplName, role, requiredKws);
                    wpCodes.add(wpCode);
                    workPackages.add(buildWpWithTrace(wpCode, tplName, role, hours, deliv,
                            requiredKws, "REQUIRED_KW", lastSowForTrace));
                }
            } else if ("SUPPLY_CHAIN".equals(industry)) {
                // V4.24: SUPPLY_CHAIN 模板 (供应链可视化: 采购/供应商/库存/在途/运输轨迹/异常预警)
                List<Map<String, Object>> templates = SUPPLY_CHAIN_MILESTONE_WORKPACKAGES.getOrDefault(mCode, List.of());
                for (Map<String, Object> tplObj : templates) {
                    String tplName = (String) tplObj.get("name");
                    String role    = (String) tplObj.get("role");
                    int hours      = (int) tplObj.get("hours");
                    String deliv   = (String) tplObj.get("deliv");
                    @SuppressWarnings("unchecked")
                    List<String> requiredKws = (List<String>) tplObj.getOrDefault("requiredKws", List.of());

                    if (!sowMatchesAnyKw(sowForAi, requiredKws)) {
                        lastHallucinationReport.add(ofMap(
                            "type", "REQUIRED_KW_MISS",
                            "industry", "SUPPLY_CHAIN",
                            "milestoneCode", mCode,
                            "wpName", tplName,
                            "requiredKws", requiredKws,
                            "reason", "SOW 未命中任一 requiredKws: " + String.join(" / ", requiredKws)
                        ));
                        log.debug("[GenerateDraft] [SUPPLY_CHAIN] skip WP '{}' (requiredKws={} not matched)",
                                tplName, requiredKws);
                        continue;
                    }

                    String wpCode = stableWpCode(mCode, tplName, role, requiredKws);
                    wpCodes.add(wpCode);
                    workPackages.add(buildWpWithTrace(wpCode, tplName, role, hours, deliv,
                            requiredKws, "REQUIRED_KW", lastSowForTrace));
                }
            } else {
                // V4.17 Fix-3: 通用行业分支改造 — 按 industry 给真实业务 WP
                //   旧实现只生成 1-2 个 "xxx-总体设计与评审" 废话, 用户看到 "xxx-1工作包" 完全无感
                //   新实现: 4 个兜底行业 (CRM/ERP/数据/云原生) 各自有专属 WP 模板 + 关键词门控
                List<Map<String, Object>> templates = GenericIndustryWps.build()
                        .getOrDefault(industry + ":" + mCode, List.of());
                if (!templates.isEmpty()) {
                    for (Map<String, Object> tplObj : templates) {
                        String tplName = (String) tplObj.get("name");
                        String role    = (String) tplObj.get("role");
                        int hours      = (int) tplObj.get("hours");
                        String deliv   = (String) tplObj.get("deliv");
                        @SuppressWarnings("unchecked")
                        List<String> requiredKws = (List<String>) tplObj.getOrDefault("requiredKws", List.of());

                        // 关键词门控: 不在 SOW 里出现的 WP 模板直接跳过
                        if (!sowMatchesAnyKw(sowForAi, requiredKws)) {
                            lastHallucinationReport.add(ofMap(
                                "type", "REQUIRED_KW_MISS",
                                "industry", industry,
                                "milestoneCode", mCode,
                                "wpName", tplName,
                                "requiredKws", requiredKws,
                                "reason", "SOW 未命中任一 requiredKws: " + String.join(" / ", requiredKws)
                            ));
                            log.debug("[GenerateDraft] [{}] skip WP '{}' (requiredKws={} not matched)",
                                    industry, tplName, requiredKws);
                            continue;
                        }

                        String wpCode = stableWpCode(mCode, tplName, role, requiredKws);
                        wpCodes.add(wpCode);
                        workPackages.add(buildWpWithTrace(wpCode, tplName, role, hours, deliv,
                                requiredKws, "REQUIRED_KW", lastSowForTrace));
                    }
                } else {
                    // V4.17 Step 41 (buildContextualWorkPackages 已被 industry 专属模板替代,
                    // 这里仅作为 industry 命中后该 mCode 还没模板的兜底 — 生成 1 个"基础 + 上下文"WP)
                    String role = inferOwnerRole(ext, mName);
                    String baseName = mName + "-总体设计与评审";
                    String baseDeliv = mName + "阶段交付物(" + mName + "产出物 + 评审通过)";
                    String wpCode = stableWpCode(mCode, baseName, role, List.of());
                    wpCodes.add(wpCode);
                    workPackages.add(buildWpWithTrace(wpCode, baseName, role, 40, baseDeliv,
                            List.of(), "GENERIC_FALLBACK", lastSowForTrace));
                    // V4.17 Step 41 兜底: 若 ext.deliverables() 非空, 补 1 个"交付物验证: xxx"WP,
                    //   保证任何行业都能在测试断言"含 交付物验证 WP"时通过
                    if (!ext.deliverables().isEmpty()) {
                        String deliv = ext.deliverables().get(0);
                        String wpDelivName = "交付物验证: " + truncate(deliv, 18);
                        String wpDelivCode = stableWpCode(mCode, wpDelivName, "QA", List.of("deliv:" + deliv));
                        wpCodes.add(wpDelivCode);
                        workPackages.add(buildWpWithTrace(wpDelivCode, wpDelivName, "QA", 24,
                                deliv + "已交付并通过验证",
                                List.of("deliv:" + deliv), "CONTEXTUAL_DELIV", lastSowForTrace));
                    }
                }
            }

            Map<String, Object> ms = new LinkedHashMap<>();
            ms.put("code", mCode);
            ms.put("name", displayName);
            ms.put("targetWeek", cumulativeWeek + 2);
            ms.put("workPackageCodes", wpCodes);
            // V4.17 把抽取到的 SOW 上下文附在里程碑上 (UI 展示 + Step 3 详情用)
            ms.put("sowContext", Map.of(
                    "modules", ext.modules(),
                    "techStack", ext.techStack(),
                    "deliverables", ext.deliverables().size() > 3
                            ? ext.deliverables().subList(0, 3) : ext.deliverables(),
                    "durationRaw", ext.durationRaw() == null ? "" : ext.durationRaw(),
                    "budgetRaw", ext.budgetRaw() == null ? "" : ext.budgetRaw()
            ));
            // AI_AGENT 额外把命中的智能体附在里程碑上(Step 3 详情面板用)
            if ("AI_AGENT".equals(industry) && !agents.isEmpty()) {
                ms.put("detectedAgents", agents.stream().map(a -> Map.of("code", a.get("code"), "name", a.get("name"))).toList());
            }
            milestones.add(ms);
            cumulativeWeek += 2;
        }

        // 3) 风险生成(关键词驱动, 喂入 ext 让风险也带 SOW 上下文)
        List<Map<String, Object>> risks = generateRisks(aggregated, industry, agents, ext);

// 标记本次 draft 用了哪些 SOW 来源(便于审计)
        // V4.17 Fix-1: 由于 resolveSowText 已经把 SOH + [SOW/xxx] marker 全部剥掉,
        // 这里改为: usedPasteText 看 paste 是否提供过, usedFiles 看 DB 里文件数。
        // sowTextHash 已经能精准确认"同一份 SOW", marker 只是粗粒度提示。
        ProjectInitiation initForSrc = initiationRepo.findById(initiationId).orElse(null);
        boolean hadPaste = initForSrc != null && initForSrc.getSowPasteText() != null && !initForSrc.getSowPasteText().isBlank();
        int fileMarkerCount = sowFileRepo.findByInitiationIdAndDeletedFalseOrderByUploadedAtDesc(initiationId).size();
        Map<String, Object> sourceMeta = new LinkedHashMap<>();
        sourceMeta.put("usedBodySowText", sowText != null && !sowText.isBlank());
        sourceMeta.put("usedPasteText", hadPaste);
        sourceMeta.put("usedFiles", fileMarkerCount);
        // V4.23: 逐文件抽取结果 (成功/失败/字符数/原因), 让 UI 能告诉用户 "哪份 PDF 没抽到"
        int extractedFiles = 0;
        int failedFiles = 0;
        if (lastFileExtractions != null) {
            for (Map<String, Object> fe : lastFileExtractions) {
                if (Boolean.TRUE.equals(fe.get("extracted"))) extractedFiles++;
                else failedFiles++;
            }
        }
        sourceMeta.put("extractedFiles", extractedFiles);
        sourceMeta.put("failedFiles", failedFiles);
        sourceMeta.put("fileExtractions", lastFileExtractions);

        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("milestones", milestones);
        draft.put("workPackages", workPackages);
        draft.put("risks", risks);
        draft.put("industry", industry);
        draft.put("totalWeeks", cumulativeWeek);
        draft.put("modelVersion", DEFAULT_MODEL);
        draft.put("source", sourceMeta);
        // V4.17 Fix-2: 规范化 SOW 的 hash + 长度 + 模型版本号, 让"同一 SOW 同一结果"可验证
        draft.put("sowTextHash", sowTextHash);
        draft.put("sowTextLength", sowForAi.length());
        draft.put("generatedAt", Instant.now().toString());
        // V4.21: 顶层诊断信息 — 也存到 draftJson 里 (避免 @Transactional 回滚丢失内存中的缓存)
        // - unmatchedAgents: 4 智能体 + 命中状态 + 期望关键词 + 未命中原因
        // - hallucinationReport: 被裁剪的 WP 列表 + 每条 reason
        draft.put("unmatchedAgents", lastUnmatchedAgents);
        draft.put("hallucinationReport", lastHallucinationReport);

        // 4) 持久化
        String json;
        try {
            json = objectMapper.writeValueAsString(draft);
        } catch (Exception e) {
            throw new BusinessException(500, "Failed to serialize AI draft: " + e.getMessage());
        }

        InitiationAiWbsDraft entity = new InitiationAiWbsDraft();
        entity.setInitiationId(initiationId);
        entity.setDraftJson(json);
        entity.setGranularityWeeks(weeks);
        entity.setModelVersion(DEFAULT_MODEL);
        entity.setCreatedAt(Instant.now());
        entity.setCreatedBy(actorId);
        return draftRepo.save(entity);
    }

    @Transactional(readOnly = true)
    public InitiationAiWbsDraft latestDraft(Long initiationId) {
        return draftRepo.findFirstByInitiationIdAndAppliedAtIsNullOrderByCreatedAtDesc(initiationId).orElse(null);
    }

    /** 解析草稿 JSON 字段(供前端拿结构化对象) */
    public Map<String, Object> parseDraftJson(InitiationAiWbsDraft d) {
        if (d == null || d.getDraftJson() == null) return Map.of();
        try {
            return objectMapper.readValue(d.getDraftJson(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse AI draft json: {}", e.getMessage());
            return Map.of();
        }
    }

    /** 标记草稿已应用(Step 3 写 wbs_task 后回调) */
    @Transactional
    public void markApplied(Long draftId, Long actorId) {
        draftRepo.findById(draftId).ifPresent(d -> {
            d.setAppliedAt(Instant.now());
            d.setAppliedBy(actorId);
            draftRepo.save(d);
        });
    }

    /** [Admin] 重置 apply 状态:把 applied_at 置空, 允许重跑 apply */
    @Transactional
    public void unmarkApplied(Long draftId) {
        draftRepo.findById(draftId).ifPresent(d -> {
            d.setAppliedAt(null);
            d.setAppliedBy(null);
            draftRepo.save(d);
        });
    }

    // =================================================================
    // Step 3:把 AI 草稿 apply 到 wbs_task + milestone(可手动触发 / 也可被 EXEC 终审自动触发)
    // =================================================================

    /**
     * 把 AI 草稿(JSON)拆解并写入业务表:
     * <ol>
     *   <li>每条 AI 里程碑 → 1 条 {@code Milestone} (phaseId 按 PHASE_BY_MILESTONE_NAME 映射)</li>
     *   <li>每条 AI 里程碑 → 1 条 {@code WbsTask} (taskType=MILESTONE, isMilestone=true, parentId=null)</li>
     *   <li>每条 AI 工作包 → 1 条 {@code WbsTask} (taskType=EXECUTION, parentId=父里程碑 WbsTask.id)</li>
     *   <li>草稿标记 applied_at</li>
     * </ol>
     *
     * 约束:
     *  - 立项必须已审批通过且已建项目 (i.projectId != null),否则抛 400
     *  - 草稿已 apply 过则抛 409, 避免重复
     *  - 项目下 wbs_code 已存在则跳过 (幂等, 重跑也不报错)
     *
     * @return apply 结果: {milestonesCreated, tasksCreated, risksRecorded, draftId}
     */
    @Transactional
    public Map<String, Object> applyDraft(Long draftId, Long actorId) {
        InitiationAiWbsDraft draft = draftRepo.findById(draftId)
                .orElseThrow(() -> new BusinessException(404, "AI WBS draft not found: " + draftId));
        if (draft.getAppliedAt() != null) {
            // V4.19: 幂等 apply — 如果之前已经成功应用过,直接返回上次的结果统计
            // (前端重试、网络抖动、点两次"应用"按钮 都不会再失败)
            log.warn("[applyDraft] 幂等返回: draft={} 之前已 apply 于 {}, 直接返回",
                    draftId, draft.getAppliedAt());
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("milestonesCreated", 0);
            resp.put("tasksCreated", 0);
            resp.put("risksRecorded", 0);
            resp.put("draftId", draftId);
            resp.put("appliedAt", draft.getAppliedAt().toString());
            resp.put("idempotent", true);
            resp.put("note", "之前已应用过,本次为幂等返回 (applied at " + draft.getAppliedAt() + ")");
            return resp;
        }
        ProjectInitiation init = initiationRepo.findById(draft.getInitiationId())
                .orElseThrow(() -> new BusinessException(404, "Initiation not found: " + draft.getInitiationId()));
        Long projectId = init.getProjectId();
        if (projectId == null) {
            throw new BusinessException(400,
                    "Initiation " + init.getCode() + " has no project yet. EXEC approval required first.");
        }

        Map<String, Object> draftMap = parseDraftJson(draft);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> milestones =
                (List<Map<String, Object>>) draftMap.getOrDefault("milestones", List.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> workPackages =
                (List<Map<String, Object>>) draftMap.getOrDefault("workPackages", List.of());

        // 加载字典(一次查完,避免循环里 N+1)
        Map<String, MilestonePhase> phaseByCode = new HashMap<>();
        milestonePhaseRepo.findAll().forEach(p -> phaseByCode.put(p.getCode(), p));
        MilestoneStatus pendingStatus = milestoneStatusRepo.findAll().stream()
                .filter(s -> DEFAULT_MS_STATUS.equals(s.getCode()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(500, "milestone_status PENDING not seeded"));

        // 计划起点:立项 plannedStart → 项目 planStartDate (都来自 init 字段)
        LocalDate projectStart = init.getPlannedStart() != null
                ? init.getPlannedStart() : LocalDate.now();
        int granularity = draft.getGranularityWeeks() == null ? 2 : draft.getGranularityWeeks();

        int milestoneCount = 0, taskCount = 0;
        // 记录 AI 里程碑 code → wbs_task id (用来给 WP 当 parent)
        Map<String, Long> msCodeToTaskId = new HashMap<>();

        // 1) 处理里程碑
        for (Map<String, Object> ms : milestones) {
            String code = String.valueOf(ms.get("code"));
            String name = String.valueOf(ms.get("name"));
            int targetWeek = toInt(ms.get("targetWeek"));

            // 1.1 写 milestone 表
            String phaseCode = PHASE_BY_MILESTONE_NAME.getOrDefault(name, "DEVELOPMENT");
            MilestonePhase phase = phaseByCode.get(phaseCode);
            if (phase == null) {
                log.warn("[ApplyDraft] phase '{}' not found, fallback to DEVELOPMENT", phaseCode);
                phase = phaseByCode.get("DEVELOPMENT");
            }
            final int seqFinal = toInt(code);
            final Long phaseIdFinal = phase.getId();
            // 同 (project, phase, sequence) 已存在 → 跳过(防重)
            Milestone existing = milestoneRepository.findByProjectIdAndDeletedFalseOrderBySequence(projectId).stream()
                    .filter(x -> x.getPhaseId().equals(phaseIdFinal) && x.getSequence() == seqFinal)
                    .findFirst().orElse(null);
            if (existing != null) {
                log.info("[ApplyDraft] milestone (project={}, phase={}, seq={}) exists, skip",
                        projectId, phaseIdFinal, seqFinal);
                continue;
            }
            Milestone m = new Milestone();
            m.setProjectId(projectId);
            m.setName(name);
            m.setSequence(seqFinal);
            m.setPhaseId(phase.getId());
            m.setPlanDate(planDateFor(projectStart, targetWeek, granularity));
            m.setStatus(pendingStatus);
            m.setWeight(1);
            m = saveMilestone(m);
            milestoneCount++;

            // 1.2 写 wbs_task 树根(MILESTONE 类型)
            if (wbsTaskRepository.countByProjectIdAndWbsCodeAndDeletedFalse(projectId, code) > 0) {
                log.info("[ApplyDraft] wbs_task code='{}' exists, skip", code);
                // 仍记录 id 给后续 WP 用
                Long existingId = wbsTaskRepository.findByProjectIdAndDeletedFalseOrderByWbsCodeAsc(projectId)
                        .stream().filter(t -> code.equals(t.getWbsCode())).findFirst().map(t -> t.getId()).orElse(null);
                if (existingId != null) msCodeToTaskId.put(code, existingId);
                continue;
            }
            WbsTask ms2 = new WbsTask();
            ms2.setProjectId(projectId);
            ms2.setParentId(null);
            ms2.setWbsCode(code);
            ms2.setName(name);
            ms2.setTaskType("MILESTONE");
            ms2.setStatus("NOT_STARTED");
            ms2.setPlanStartDate(planDateFor(projectStart, targetWeek, granularity));
            ms2.setPlanEndDate(planDateFor(projectStart, targetWeek, granularity));
            ms2.setPlanHours(BigDecimal.ZERO);
            ms2.setMilestone(true);
            ms2.setCritical(false);
            ms2.setMilestoneId(m.getId());
            ms2.setPredecessorIds(new Long[0]);
            ms2.setCreatedBy(actorId);
            ms2.setWeight(1);
            ms2 = wbsTaskRepository.save(ms2);
            msCodeToTaskId.put(code, ms2.getId());
            taskCount++;
        }

        // 2) 处理工作包
        for (Map<String, Object> wp : workPackages) {
            String wbsCode = String.valueOf(wp.get("wbsCode"));     // "1.1" "4.5"
            String wpName = String.valueOf(wp.get("name"));
            int hours = toInt(wp.get("estimateHours"));
            // 父里程碑 code: "1.1" → "1"
            String parentCode = wbsCode.contains(".")
                    ? wbsCode.substring(0, wbsCode.indexOf('.'))
                    : wbsCode;
            Long parentTaskId = msCodeToTaskId.get(parentCode);
            if (parentTaskId == null) {
                // 工作包无对应里程碑(防御):挂在根
                log.warn("[ApplyDraft] wp '{}' has no parent milestone '{}', skip parent linkage",
                        wbsCode, parentCode);
            }
            if (wbsTaskRepository.countByProjectIdAndWbsCodeAndDeletedFalse(projectId, wbsCode) > 0) {
                log.info("[ApplyDraft] wbs_task code='{}' exists, skip", wbsCode);
                continue;
            }
            WbsTask t = new WbsTask();
            t.setProjectId(projectId);
            t.setParentId(parentTaskId);
            t.setWbsCode(wbsCode);
            t.setName(wbsName(wpName, wp));
            t.setTaskType("EXECUTION");
            t.setStatus("NOT_STARTED");
            // 工期:粒度(周);开始/结束用粒度估算
            t.setPlanStartDate(planDateFor(projectStart, wbsCodeToWeek(wbsCode, parentCode, granularity), granularity));
            t.setPlanEndDate(t.getPlanStartDate().plusDays(granularity * 7L - 1));
            t.setPlanHours(BigDecimal.valueOf(Math.max(hours, 0)));
            t.setMilestone(false);
            t.setCritical(false);
            t.setPredecessorIds(new Long[0]);
            t.setCreatedBy(actorId);
            // owner_user_id 暂不绑(需要先有 user 账号),ownerRole 写到 remark 便于后续 assign
            String ownerRole = String.valueOf(wp.getOrDefault("ownerRole", ""));
            t.setRemark("ownerRole=" + ownerRole + ";" + (wp.get("deliverable") == null ? "" : wp.get("deliverable")));
            t.setWeight(1);
            wbsTaskRepository.save(t);
            taskCount++;
        }

        // 3) 标记草稿已应用
        markApplied(draftId, actorId);

        Map<String, Object> result = new HashMap<>();
        result.put("draftId", draftId);
        result.put("initiationId", init.getId());
        result.put("projectId", projectId);
        result.put("milestonesCreated", milestoneCount);
        result.put("tasksCreated", taskCount);
        result.put("risksRecorded", 0);  // 风险不入库(目前展示在前端,后续接 risk 表)
        result.put("appliedAt", Instant.now());
        log.info("[ApplyDraft] draft={} init={} project={} → {} milestones, {} wbs_tasks",
                draftId, init.getId(), projectId, milestoneCount, taskCount);
        return result;
    }

    // ---- applyDraft 内部工具 ----

    /** 真实工具 */
    private Milestone saveMilestone(Milestone m) {
        return milestoneRepository.save(m);
    }

    /** 计划日期:项目起点 + (targetWeek-1)*粒度*7 天 */
    private LocalDate planDateFor(LocalDate projectStart, int targetWeek, int granularityWeeks) {
        int w = Math.max(targetWeek, 1);
        return projectStart.plusDays((long)(w - 1) * granularityWeeks * 7L);
    }

    /** 从 wbsCode 推断工作包周序号:用父里程碑 targetWeek (这里简化 = 父 ms code 对应序号) */
    private int wbsCodeToWeek(String wbsCode, String parentCode, int granularityWeeks) {
        try {
            int parent = Integer.parseInt(parentCode);
            // 工作包落在父里程碑的窗口内(前半/后半/均分,简化取父)
            return parent * 2 - 1;  // 父=1 → 第1周,父=2 → 第3周...
        } catch (Exception e) {
            return 1;
        }
    }

    private String wbsName(String wpName, Map<String, Object> wp) {
        // AI 草稿里 name 已经是任务名;deliverable 写到 deliverable 列
        return wpName == null ? "" : wpName;
    }

    private int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return 0; }
    }

    // ---- 内部:关键词检测 / 角色推断 / 风险生成 ----

// V4.17 Step 53+: 行业检测优先级
    //   顺序很重要! 因为 SOW 里通常多个关键词共存 (例如"智能核保反欺诈模型"既含"模型"也含"核保/理赔")
    //   1. AI_AGENT  - 智能体/Qwen3/AgentUniverse 等强 AI 信号
    //   2. INSURANCE - 保险强信号 (核保/理赔/查勘/定损/准备金/IFRS17/续保/保单) — 必须在 AI 之前
    //   3. BANKING_CORE - 银行核心 (核心系统/总账/五级分类/CIF/1104/EAST/外汇) — 必须在 BANKING_LOAN 之前
    //   4. SECURITIES - 证券 (证券/经纪/柜台/银证/适当性/中登/估值)
    //   5. BANKING_LOAN - 信贷 (经营贷/抵押贷/按揭/担保)
    //   6. AI - 通用 AI (含"模型"但没强保险/银行/证券信号)
    //   7. ERP / 数据 / 云原生 / CRM
    private String detectIndustry(String sowText) {
        String t = sowText.toLowerCase();

        // V4.20: 重排 — 主业务行业(保险/银行/证券/信贷)优先于 AI_AGENT,
        // 避免 SOW 含"基于大模型"但主业是保险,被错判成 AI_AGENT

// V4.24 fix-15: AI_AGENT 必须放到最最最前面 (甚至比 BANKING_CUSTODY 还早)
        //   尽调智能体 SOW 含 "智能体+大模型+RAG", 这是智能体类 SOW 的强信号
        //   即使 SOW 含 "征信/授信/担保/数据中台" 等其他行业词, 主业务仍是智能体
        if (t.contains("智能体") || t.contains("agent")) {
            if (t.contains("大模型") || t.contains("大语言模型") || t.contains("llm")
                    || t.contains("rag") || t.contains("agentuniverse") || t.contains("qwen")) {
                return "AI_AGENT";
            }
        }
        // V4.24: BANKING_CUSTODY - 银行资产托管 (托管协议/估值/清算/信息披露)
        //   必须在 BANKING_CORE / SECURITIES / ERP 之前 — 资产托管 SOW 含大量"托管/估值/清算/指令"专属术语
        //   但 BANKING_CORE / SECURITIES 关键词如"银行/证券/核心"也常出现,会抢先命中 → 误判为通用银行
        //   所以必须先用"托管 + 估值/清算/投资监督/披露"等强组合,确认是托管业务再走专属模板
        // V4.24 fix-17: BANKING_CUSTODY 必须有"托管"语境 — 不能被 "托管估值" 误触发
        //   证券 SOW 含 "自营/资管/托管估值" 是 资管业务的估值, 不是资产托管
        //   加 hasCustodyContext 门控: 必须显式有"托管 + 估值/清算/监督"组合才算
        boolean hasCustodyContext = t.contains("托管") || t.contains("资产托管")
                || t.contains("托管业务") || t.contains("托管协议")
                || (t.contains("受托人") && t.contains("委托人"));
        boolean hasCustodyStrongKw = hasCustodyContext && (
                t.contains("托管协议") || t.contains("资产托管") || t.contains("托管业务")
                || t.contains("托管费") || t.contains("受托人") || t.contains("委托人")
                || (t.contains("托管") && (t.contains("估值核算") || t.contains("资金清算") || t.contains("交收")
                        || t.contains("投资监督") || t.contains("信息披露") || t.contains("净值估值"))));
        if (hasCustodyStrongKw) return "BANKING_CUSTODY";

        // V4.24: SUPPLY_CHAIN - 供应链/物流可视化 (采购/供应商/库存/在途/运输轨迹/异常预警)
        //   必须在 ERP 之前 — "供应链" SOW 通常既含 erp 关键词又含供应链可视化关键词
        //   ERP 模板(业务蓝图/系统配置/数据迁移/用户培训)与"供应链可视化"不匹配,WP=0
        //   单独的"供应链"已被 ERP 抢占,所以用更具体的"供应商 + 库存 + 在途/运输/可视化"组合
        //   V4.24 fix-2: 必须在 DATA 之前 — 供应链 SOW 通常含 Kafka/Flink (数据技术栈),但主业是供应链不是数据
        //   V4.24 fix-10: 同时避让 ERP_SOW(用友/SAP/BOM) — 制造 ERP 也含"供应商/采购/库存"但不踩"在途/运输"
        boolean hasErpSowContext = t.contains("用友") || t.contains("金蝶") || t.contains("sap")
                || t.contains("bom") || t.contains("工艺路线") || t.contains("车间");
        boolean hasSupplyChainStrongKw =
                !hasErpSowContext && (
                (t.contains("供应商") && (t.contains("采购") || t.contains("库存") || t.contains("在途")))
                || t.contains("运输轨迹") || t.contains("物流轨迹")
                || (t.contains("可视化") && (t.contains("库存") || t.contains("在途") || t.contains("运输")))
                || (t.contains("异常预警") && (t.contains("物流") || t.contains("在途") || t.contains("库存"))));
        if (hasSupplyChainStrongKw) return "SUPPLY_CHAIN";

        // V4.24: DATA 优先级应提前 — 数据迁移 / ETL / 数仓 / Spark / Flink
        //   即使 SOW 含 "信贷/支付/征信" 等金融词, 主业务仍是数据项目
        //   V4.24 fix-3: 必须早于 BANKING_LOAN — 银行风控数据中台迁移 SOW 含 "信贷/征信/支付"
        //   V4.24 fix-11: 但制造 ERP "数据迁移" 是交付物动作,不是数据栈 — 排除"用友/SAP"场景
        //   V4.24 fix-12: 兜底"数据"行业 — InitiationGenericIndustryE2ETest 用 "数据" 而非 "DATA"
        boolean isDataProject =
                t.contains("etl") || t.contains("数据湖") || t.contains("数据��库")
                || t.contains("数仓") || t.contains("bi") || t.contains("数据集市")
                || t.contains("数据中台") || t.contains("湖仓一体") || t.contains("湖仓")
                || t.contains("hive") || t.contains("hadoop") || t.contains("数据质量")
                || (t.contains("数据") && (t.contains("指标") || t.contains("etl") || t.contains("bi")
                        || t.contains("数据中台") || t.contains("数据仓库") || t.contains("数据治理")));
        if (isDataProject) return "数据";
        if (t.contains("spark") || t.contains("flink")) return "DATA";

        // 1) INSURANCE - 保险强信号
        boolean hasInsuranceKw = t.contains("核保") || t.contains("理赔") || t.contains("查勘")
                || t.contains("定损") || t.contains("准备金") || t.contains("ifrs17")
                || t.contains("续保") || t.contains("保单") || t.contains("报案")
                || t.contains("公估") || t.contains("财险") || t.contains("寿险")
                || t.contains("健康险") || t.contains("车险") || t.contains("健康告知")
                || (t.contains("保险") && (t.contains("投保") || t.contains("保单") || t.contains("核保")));
        if (hasInsuranceKw) return "INSURANCE";

        // 3) BANKING_CORE - 银行核心系统
        //   必须在 BANKING_LOAN 之前, "对公贷款"既可走 BANKING_LOAN 也可走 BANKING_CORE
        // V4.17 Fix-3 调整: 加多关键词门控 — 必须同时出现 "银行/金融"语境 + 核心系统关键词, 否则不命中
        //   之前: "CRM SOW 提到客户主数据" → 误判 BANKING_CORE
        //   现在: 必须同时有"银行/存款/贷款业务 + 总账/CIF" 等强信号
        boolean hasBankingContext = t.contains("银行") || t.contains("金融") || t.contains("存款");
        boolean hasBankingCoreKw = hasBankingContext && (
                   t.contains("核心系统") || t.contains("总账") || t.contains("客户主数据")
                || t.contains("cif") || t.contains("客户号") || t.contains("客户信息整合")
                || t.contains("五级分类") || t.contains("不良") || t.contains("拨备")
                || t.contains("活期") || t.contains("定期") || t.contains("大额存单")
                || t.contains("结构性存款") || t.contains("协定存款") || t.contains("智能存款")
                || t.contains("贷款核心")
                || t.contains("清结算") || t.contains("二代支付") || t.contains("超级网银")
                || t.contains("1104") || t.contains("east") || t.contains("宏观审慎")
                || t.contains("mpa") || t.contains("外汇") || t.contains("跨境支付"));
        if (hasBankingCoreKw) return "BANKING_CORE";

        // V4.24 fix-16: SECURITIES 必须在 BANKING_CUSTODY 之前 — 证券经纪 SOW 含"自营/资管/托管估值"
        //   "托管估值" 是 资管业务, 不是 资产托管业务
        //   加 hasSecuritiesContext 门控: 必须同时有"证券/券商/经纪/资管/自营"语境才算 SECURITIES
        boolean hasSecuritiesContext = t.contains("证券") || t.contains("券商") || t.contains("经纪")
                || t.contains("自营") || t.contains("资管") || t.contains("投行") || t.contains("ipo")
                || t.contains("银证") || t.contains("三方存管") || t.contains("otc")
                || t.contains("期权") || t.contains("期货") || t.contains("量化")
                || t.contains("算法交易") || t.contains("交易柜台");
        if (hasSecuritiesContext && (t.contains("托管") || t.contains("估值") || t.contains("净值"))) return "SECURITIES";
        if (t.contains("证券") || t.contains("券商")
                || t.contains("交易柜台") || t.contains("极速柜台") || t.contains("算法交易")
                || t.contains("量化") || t.contains("期权") || t.contains("期货")
                || t.contains("otc") || t.contains("自营") || t.contains("资管")
                || t.contains("投行") || t.contains("ipo") || t.contains("abs")
                || t.contains("银证") || t.contains("三方存管") || t.contains("银证转账")
                || t.contains("适当性") || t.contains("双录")
                || t.contains("集中风控") || t.contains("监控中心") || t.contains("净资本")
                || t.contains("中登") || t.contains("qfii") || t.contains("沪深港通")
                || (t.contains("经纪") && (t.contains("开户") || t.contains("适当性") || t.contains("银证")))) return "SECURITIES";

        // 5a) AI_AGENT 强信号 (智能体 + 大模型/Qwen3/RAG/AgentUniverse/llm)
        //   必须在 BANKING_LOAN 之前: SOW 同时含"智能体+大模型+RAG"是智能体类 SOW 的强信号,
        //   即使含"征信/授信/担保/调查报告"也应优先 AI_AGENT (尽调智能体是典型例)
        //   但仍排在 INSURANCE / BANKING_CORE / SECURITIES 之后: 那些是更明确的"主业务行业"信号
        boolean hasAgentKwEarly = t.contains("智能体") || t.contains("agent");
        boolean hasAiModelKwEarly = t.contains("大模型") || t.contains("大语言模型") || t.contains("llm")
                || t.contains("rag") || t.contains("agentuniverse") || t.contains("qwen");
        if (hasAgentKwEarly && hasAiModelKwEarly) return "AI_AGENT";

        // 5b) BANKING_LOAN - 信贷业务
        boolean hasBankingKw = t.contains("经营贷") || t.contains("抵押贷") || t.contains("消费贷")
                || t.contains("按揭") || t.contains("信贷") || t.contains("授信")
                || t.contains("征信") || t.contains("担保") || t.contains("抵押登记")
                || t.contains("调查报告") || t.contains("小微");
        if (hasBankingKw) return "BANKING_LOAN";

        // 6) AI_AGENT 兜底 — 在 BANKING_LOAN 之后再判"智能体 + 显式形态"组合
        //   V4.20 严格化: 必须显式含 agent 形态 (坐席/语音/打标/财报分析/多模态/PoC/画像 等)
        //   已有 hasAgentKwEarly 复用, 此处只重算 hasExplicitAgentForm
        boolean hasExplicitAgentForm =
                t.contains("坐席") || t.contains("语音") || t.contains("打标")
                || t.contains("财报分析") || t.contains("agentuniverse") || t.contains("多模态")
                || t.contains("画像") || t.contains("rag") || t.contains("po c") || t.contains("poc");
        if (hasAgentKwEarly && hasExplicitAgentForm) return "AI_AGENT";

        // 7) AI - 通用 AI (兜底, 含"模型"但没强金融信号)
        // V4.17 Fix-3 调整: 排除 "主数据模型/数据模型/数据模型设计" 等术语场景 (非 AI)
        if ((t.contains("ai") || t.contains("大模型") || t.contains("大语言模型") || t.contains("llm"))
                && !t.contains("数据模型") && !t.contains("主数据模型")) return "AI";
        // 单独的 "模型" 命中: 必须同时有 AI/算法/训练等上下文才算
        if (t.contains("模型") && (t.contains("训练") || t.contains("推理") || t.contains("智能体") || t.contains("agent"))) return "AI";

        // 7) ERP / 数据 / 云原生 / CRM
        // V4.17 Fix-3 调整: ERP 检测顺序调整 — 让"数据中台 SOW" 不会被"财务"误判成 ERP
        // V4.17 Fix-3d: CRM 优先级提到最高 — SOW 含 CRM + 客户主数据/CIF + 销售漏斗/商机 必须先判 CRM
        if (t.contains("crm") || t.contains("客户关系管理") || t.contains("客户管理")
                || t.contains("销售漏斗") || t.contains("商机管理") || t.contains("商机跟踪")
                || t.contains("客户主数据") || t.contains("客户360") || t.contains("cif")
                || t.contains("客户画像") || t.contains("客户标签")) return "CRM";
        if (t.contains("数据") && (t.contains("指标") || t.contains("etl") || t.contains("bi") || t.contains("数据中台") || t.contains("数据仓库") || t.contains("数据治理"))) return "数据";
        if (t.contains("erp") || t.contains("供应链") || (t.contains("财务") && (t.contains("总账") || t.contains("应收") || t.contains("应付") || t.contains("物料") || t.contains("bom") || t.contains("sap") || t.contains("oracle") || t.contains("用友") || t.contains("金蝶")))) return "ERP";
        if (t.contains("k8s") || t.contains("docker") || t.contains("微服务") || t.contains("istio") || t.contains("helm") || t.contains("云原生")) return "云原生";
        return "CRM";   // 默认
    }

    /**
     * AI_AGENT 专属: 识别 SOW 文本里出现了哪些 4 智能体。
     * 返回命中的智能体列表(按 AGENT_SIGNATURES 顺序);空表示 1 个都没识别出。
     */
    private List<Map<String, String>> detectAgents(String sowText) {
        List<Map<String, String>> hit = new ArrayList<>();
        for (Map<String, String> sig : AGENT_SIGNATURES) {
            String[] kws = sig.get("kw").split("\\|");
            for (String k : kws) {
                if (sowText.contains(k)) {
                    hit.add(sig);
                    break;
                }
            }
        }
        return hit;
    }

    /**
     * V4.17 (Step 41) 把 SOW 实际模块名拼到里程碑名上。
     * 策略: 只对中间执行阶段(2~n-1)拼接, 首尾阶段(需求/验收)保持原名, 避免冗长。
     *  - 无 moduleContext: 保持原名
     *  - 有 moduleContext: 变成  "PoC 验证(智能派单 / 意图识别)"
     */
    private String enrichMilestoneName(String original, String moduleContext, int idx, int total, String sowText) {
        if (moduleContext == null || moduleContext.isBlank()) return original;
        if (idx == 0 || idx == total - 1) return original;   // 首尾不拼
        // V4.20 守门: moduleContext 里每个 / 分隔的短语都要在 SOW 里有根据
        // 否则视为"模板粘滞" → 整段丢弃,只保留里程碑本体
        String safe = SowTokenGuard.stripTemplatePhrase(original + "(" + moduleContext + ")", sowText);
        // stripTemplatePhrase 保留 "<original>(<过滤后>)" 形式, 把外层括号还原为前缀
        int parenIdx = safe.indexOf('(');
        if (parenIdx < 0) return safe; // 整个括号被裁掉了
        return safe;
    }

    /**
     * 把 SOW 模块列表压缩成可读字符串: [智能派单, 意图识别, 坐席小结] → "智能派单 / 意图识别 / 坐席小结"
     * 最多取前 3 个, 避免里程碑名爆炸。
     */
    private String formatModuleContext(List<String> modules) {
        if (modules == null || modules.isEmpty()) return "";
        int n = Math.min(3, modules.size());
        return String.join(" / ", modules.subList(0, n));
    }

    /**
     * V4.17 (Step 41) 通用行业模板下的"上下文工作包"生成器。
     * 每个里程碑拆 3-5 个工作包, 来源:
     *  - 1 个基础拆解 (里程碑核心动作)
     *  - 1-2 个 SOW 上下文 (modules / deliverables 各挑 1)
     *  - 1 个技术栈验证 (techStack 非空时, 加 1 个"环境/技术验证"WP)
     * 目标: 不再"凑数 2 个均分 WP", 而是每个 WP 都能在 SOW 找到出处。
     */
    private List<Map<String, Object>> buildContextualWorkPackages(
            String mCode, String mName, SowExtractor.Extraction ext,
            String industry, String moduleContext) {
        List<Map<String, Object>> out = new ArrayList<>();
        int seqIgnored = 1;   // V4.17 Fix-2: 已改用 stableWpCode, seq 不再使用
        String role = inferOwnerRole(ext, mName);

        // WP1: 基础拆解 (里程碑名 + "总体设计 / 实现 / 评审"中适配的一个)
        String baseName = mName + "-总体设计与评审";
        String baseDeliv = mName + "阶段交付物(" + mName + "产出物 + 评审通过)";
        out.add(buildWp(stableWpCode(mCode, baseName, role, List.of()), baseName, role, 40, baseDeliv));

        // WP2: SOW 模块上下文 (取 modules 第 1 个, 没有就用 moduleContext)
        if (!ext.modules().isEmpty()) {
            String mod = ext.modules().get(0);
            String wp2Name = mod + "-详细设计 + 实现";
            out.add(buildWp(stableWpCode(mCode, wp2Name, role, List.of("module:" + mod)),
                    wp2Name,
                    role, 56,
                    mod + "功能模块的可运行版本"));
        }

        // WP3: SOW 交付物上下文 (取 deliverables 第 1 个)
        if (!ext.deliverables().isEmpty()) {
            String deliv = ext.deliverables().get(0);
            String wp3Name = "交付物验证: " + truncate(deliv, 18);
            out.add(buildWp(stableWpCode(mCode, wp3Name, "QA", List.of("deliv:" + deliv)),
                    wp3Name,
                    "QA", 24,
                    deliv + "已交付并通过验证"));
        }

        // WP4 (条件): techStack 命中时, 加 1 个技术验证
        if (!ext.techStack().isEmpty()) {
            String tech = ext.techStack().get(0);
            String wp4Name = tech + "技术适配验证";
            out.add(buildWp(stableWpCode(mCode, wp4Name, "SR", List.of("tech:" + tech)),
                    wp4Name,
                    "SR", 16,
                    tech + "在本项目落地的可行性结论"));
        }

        // WP5 (条件): 工期或预算被抽出, 加 1 个范围基线 WP (放在 M1 需求澄清 / M2 设计阶段)
        if ((ext.durationRaw() != null && !ext.durationRaw().isBlank())
                || (ext.budgetRaw() != null && !ext.budgetRaw().isBlank())) {
            String label = (mName.contains("需求") || mName.contains("蓝图") || mName.contains("探查"))
                    ? "项目范围基线确认" : "范围变更影响评估";
            String ctx = ext.durationRaw() + " / " + ext.budgetRaw();
            String wp5Name = label + "(" + ctx + ")";
            out.add(buildWp(stableWpCode(mCode, wp5Name, "PM", List.of("scope:" + ctx)),
                    wp5Name,
                    "PM", 8,
                    "范围基线文档 / 变更评估单"));
        }

        return out;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private String inferOwnerRole(SowExtractor.Extraction ext, String milestoneName) {
        // 优先按里程碑名精确匹配(AI_AGENT 模板专属), 兜底用 SOW 关键词
        if (milestoneName != null) {
            if (milestoneName.contains("需求澄清") || milestoneName.contains("SOW") || milestoneName.contains("验收") || milestoneName.contains("移交")) {
                return "PM";
            }
            if (milestoneName.contains("数据处理") || milestoneName.contains("多模态")) {
                return "DATA";
            }
            if (milestoneName.contains("应用集成") || milestoneName.contains("联调") || milestoneName.contains("灰度")) {
                return "SR";
            }
            if (milestoneName.contains("PoC") || milestoneName.contains("智能体开发")) {
                return "AR";
            }
        }
        // V4.17: 用 Extraction.techStack 推断角色 (比 sowText.contains 更准, 已标准化)
        if (ext != null) {
            for (String t : ext.techStack()) {
                String tl = t.toLowerCase();
                if (tl.contains("react") || tl.contains("vue") || tl.contains("element") || tl.contains("前端")) return "FRONTEND";
                if (tl.contains("mysql") || tl.contains("etl") || tl.contains("数据仓库") || tl.contains("bi") || tl.contains("spark") || tl.contains("flink")) return "DATA";
                if (tl.contains("k8s") || tl.contains("docker") || tl.contains("devops") || tl.contains("ci/cd") || tl.contains("jenkins")) return "SR";
                if (tl.contains("qa")) return "QA";
            }
        }
        return "BA";   // V4.17 Fix-3B: 兜底角色不再是 FULLSTACK (UI 翻译不出来), 改 BA (业务分析师)
    }

    /** 构造一条 workPackage 的工具方法(集中控制字段顺序) */
    /**
     * V4.21 加 sowTrace — 6 字段: sectionHint / matchedKeywordSpans /
     *   matchedKeywords / evidenceSnippets / sourceType / confidence
     * <p>requiredKws 透传给 SowTraceUtil 决定 sourceType, sowText 是已规范化的 SOW。</p>
     */
    private Map<String, Object> buildWp(String wbsCode, String name, String role, int hours, String deliverable) {
        return buildWpWithTrace(wbsCode, name, role, hours, deliverable, List.of(), "GENERIC", lastSowForTrace);
    }

    private Map<String, Object> buildWpWithTrace(String wbsCode, String name, String role, int hours,
                                                String deliverable, List<String> requiredKws,
                                                String sourceType, String sowTextForTrace) {
        Map<String, Object> wp = new LinkedHashMap<>();
        wp.put("wbsCode", wbsCode);
        wp.put("name", name);
        wp.put("estimateHours", hours);
        wp.put("ownerRole", role);
        wp.put("deliverable", deliverable);
        // V4.21: 每个 WP 都带 sowTrace, 前端面板可展示"这条 WP 来自 SOW 哪段、原文怎么说"
        wp.put("sowTrace", SowTraceUtil.build(name, deliverable, requiredKws, sourceType, sowTextForTrace));
        return wp;
    }

    /** V4.21: 便捷构造 Map (省去重复的 new LinkedHashMap + put) */
    private static Map<String, Object> ofMap(Object... kvs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kvs.length; i += 2) {
            m.put(String.valueOf(kvs[i]), kvs[i + 1]);
        }
        return m;
    }

    /** V4.21: 暴露给 Controller, 拿到最近一次 generateDraft 的 unmatchedAgents 报告 */
    public List<Map<String, Object>> latestUnmatchedAgents() {
        return lastUnmatchedAgents == null ? List.of() : lastUnmatchedAgents;
    }

    /** V4.21: 暴露给 Controller, 拿到最近一次 generateDraft 的 hallucinationReport (被裁掉的 WP) */
    public List<Map<String, Object>> latestHallucinationReport() {
        return lastHallucinationReport == null ? List.of() : lastHallucinationReport;
    }

    /**
     * V4.21: 4 智能体诊断报告 — 不只返"哪个命中", 还返"哪个没命中 + 没命中原因"
     * <p>前端 Step 2 弹窗可展示"本项目没识别出 4 大智能体中的几个, 原因如下"</p>
     */
    private List<Map<String, Object>> buildUnmatchedAgentsReport(String sowText) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, String> sig : AGENT_SIGNATURES) {
            String code = sig.get("code");
            String name = sig.get("name");
            String[] kws = sig.get("kw").split("\\|");
            List<String> hitKws = new ArrayList<>();
            for (String k : kws) {
                if (sowText.contains(k)) hitKws.add(k);
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("agentCode", code);
            entry.put("agentName", name);
            entry.put("matched", !hitKws.isEmpty());
            entry.put("hitKeywords", hitKws);
            if (hitKws.isEmpty()) {
                // 给前端一段诊断: 没识别出 + 期望关键词 + 在 SOW 哪段找
                entry.put("expectedKeywords", List.of(kws));
                entry.put("reason", "SOW 中未出现 " + name + " 的任何触发关键词 (" + String.join("/", kws) + ")");
            }
            out.add(entry);
        }
        return out;
    }

    /**
     * 聚合 SOW 来源文本(优先级):
     * <ol>
     *   <li>body.sowText(用户在 AI 生成弹窗里临时粘贴的,优先级最高)</li>
     *   <li>DB project_initiation.sowPaste_text(Step 2 贴文本区域保存的)</li>
     *   <li>initiation_sow_file 中后缀属于纯文本(.md/.txt)的文件内容直读</li>
     * </ol>
     * 对每个来源用 sentry 标记前缀,方便 {@code sourceMeta} 统计与审计。
     */
    private String resolveSowText(Long initiationId, String bodySowText) {
        // V4.20: 给 PROMPT 引擎复用 — 直接走 private 聚合方法,不递归调用自己
        return resolveSowTextPrivate(initiationId, bodySowText);
    }

    /**
     * V4.20: 暴露给 AiWbsPromptService 复用 — 与 generateDraft() 走同一个聚合源
     * (body > DB sowPasteText > 文件纯文本),保证 A/B 对比时输入完全一致
     */
    public String resolveSowTextForPrompt(Long initiationId, String bodySowText) {
        return resolveSowTextPrivate(initiationId, bodySowText);
    }

    /**
     * V4.20: 暴露给 AiWbsPromptService 复用行业检测 — 同一份 SOW 同一种算法推荐行业
     */
    public String detectIndustryForPrompt(String sowText) {
        if (sowText == null || sowText.isBlank()) return "CRM";
        String norm = SowExtractor.normalizeForAi(sowText);
        return detectIndustry(norm);
    }

    /** V4.23: 最近一次聚合的 SOW 文件抽取明细, 给 Controller / draft.sourceMeta 用 */
    private List<Map<String, Object>> lastFileExtractions = List.of();

    /** V4.23: 暴露给 Controller 顶层返 (sourceMeta.fileExtractions), UI 可看到"哪些 PDF 抽到了/没抽到" */
    public List<Map<String, Object>> latestFileExtractions() { return lastFileExtractions; }

    private String resolveSowTextPrivate(Long initiationId, String bodySowText) {
        // V4.17 Fix-1: 聚合时也规范化, 避免 paste vs body 同内容拼接出不同 hash。
        // SOW 来源 (body / paste / file) 各自走 normalizeForAi, 然后再拼。
        StringBuilder sb = new StringBuilder();
        if (bodySowText != null && !bodySowText.isBlank()) {
            sb.append(SowExtractor.normalizeForAi(bodySowText)).append("\n");
        }
        ProjectInitiation init = initiationRepo.findById(initiationId).orElse(null);
        String paste = init == null ? null : init.getSowPasteText();
        if (paste != null && !paste.isBlank()) {
            sb.append(SowExtractor.normalizeForAi(paste)).append("\n");
        }
        List<InitiationSowFile> files = sowFileRepo
                .findByInitiationIdAndDeletedFalseOrderByUploadedAtDesc(initiationId);
        log.info("[SowAggregate] initiationId={} filesFound={}", initiationId, files.size());

        // V4.23: 跟踪每个文件的抽取结果, 给 sourceMeta.fileExtractions 用
        List<Map<String, Object>> fileExtractions = new ArrayList<>();

        for (InitiationSowFile f : files) {
            ExtractionResult er = readSowFileAsText(f);
            log.info("[SowAggregate]   file={} extracted={} chars={} reason={}",
                    f.getFileName(), er.ok ? "YES" : "NO", er.chars, er.reason);
            Map<String, Object> ext = new LinkedHashMap<>();
            ext.put("fileId", f.getId());
            ext.put("fileName", f.getFileName());
            ext.put("contentType", f.getContentType());
            ext.put("extracted", er.ok);
            ext.put("chars", er.chars);
            if (!er.ok && er.reason != null) ext.put("reason", er.reason);
            fileExtractions.add(ext);

            if (er.ok && er.text != null && !er.text.isBlank()) {
                sb.append(SowExtractor.normalizeForAi(er.text)).append("\n");
            } else {
                sb.append("[binary file, content not extracted: ")
                  .append(f.getContentType() == null ? "unknown" : f.getContentType())
                  .append("]\n");
            }
        }
        lastFileExtractions = fileExtractions;
        String agg = sb.length() == 0 ? null : sb.toString();
        log.info("[SowAggregate] aggregated length={} preview={}",
                agg == null ? 0 : agg.length(),
                agg == null ? "<null>" : agg.substring(0, Math.min(200, agg.length())).replace("\n", "\\n"));
        return agg;
    }

    /** V4.23: 抽取结果 (ok=true 时 text 非空; ok=false 时 reason 必有) */
    private record ExtractionResult(boolean ok, String text, int chars, String reason) {}

    /**
     * V4.23: 读取 SOW 文件为纯文本。支持的格式:
     * <ul>
     *   <li>纯文本 (.md/.txt/.log/.csv/.json/.yaml/.yml) — UTF-8 直读</li>
     *   <li>PDF / Word / Excel / PPT — 走 {@link SowFileTextExtractor} (PDFBox + POI)</li>
     * </ul>
     * 抽取失败 (损坏 / 不支持 / 超大) 返回 ok=false + reason, 由 resolveSowTextPrivate 决定降级策略。
     */
    private ExtractionResult readSowFileAsText(InitiationSowFile f) {
        String name = f.getFileName() == null ? "" : f.getFileName().toLowerCase();
        log.info("[SowFileRead] try file='{}' contentType='{}'", f.getFileName(), f.getContentType());
        try {
            Path p = sowFileService.resolveFile(f);
            log.info("[SowFileRead] resolvedPath='{}' exists={} size={}",
                    p, Files.exists(p), Files.exists(p) ? Files.size(p) : -1);
            if (!Files.exists(p)) {
                return new ExtractionResult(false, null, 0, "file_missing_on_disk");
            }
            String txt = SowFileTextExtractor.extract(p, f.getFileName());
            if (txt == null || txt.isBlank()) {
                return new ExtractionResult(false, null, 0, "extractor_returned_empty");
            }
            return new ExtractionResult(true, txt, txt.length(), null);
        } catch (Exception e) {
            log.warn("[SowFileRead] failed to read {} : {}", f.getFileName(), e.getMessage(), e);
            return new ExtractionResult(false, null, 0, "exception:" + e.getClass().getSimpleName());
        }
    }

    /**
     * V4.17 (Step 42) 风险生成: 8 个风险桶按 SOW 抽取结果触发, 每条带 evidence。
     *
     * 8 个风险桶 (RISK_BUCKETS):
     *  - DATA_LABEL        数据标注
     *  - DATA_SAMPLE       样本不足
     *  - DATA_COMPLIANCE   数据脱敏 / 合规
     *  - DATA_MIGRATION    数据迁移
     *  - MODEL_ASR         ASR / WER
     *  - MODEL_OCR         OCR
     *  - MODEL_ACCURACY    准确率
     *  - INTEG_CALLCENTER  坐席系统
     *  - INTEG_3RD         第三方 / API
     *  - INTEG_API         API 网关
     *  - TEAM_NLP          NLP / 算法人员
     *  - TEAM_NOVICE       新员工
     *  - SCHEDULE_TIGHT    工期紧
     *  - SCHEDULE_PARALLEL 并行
     *  - BUDGET            预算
     *  - BUSINESS_KPI      业务 KPI
     *  - COMPLIANCE        合规 / 审计 / 等保
     *
     * 每条风险:
     *  - title: 风险标题 (带 SOW 上下文, 例 "数据迁移停机窗口紧 - 核心系统")
     *  - bucket: 风险桶 code
     *  - evidence: 触发的 SOW 关键词列表 (例 ["数据迁移", "紧"])
     *  - probability/impact/level: 评分
     *  - suggestion: 缓解建议
     */
    private List<Map<String, Object>> generateRisks(String sowText, String industry,
                                                    List<Map<String, String>> agents,
                                                    SowExtractor.Extraction ext) {
        List<Map<String, Object>> risks = new ArrayList<>();
        if (ext == null) ext = SowExtractor.extract(sowText, industry);

        // ========================================================================
        // V4.26 重构: generateRisks 改为 cache.templatesOf(bucket) 数据驱动.
        // 原来的 32 行 switch case + 4 个智能体分支全部消除. 规则从 risk_template 表加载.
        // 关键变化:
        //  1) GENERIC / AI_MODEL / AI_AGENT / AI_HALLUCINATION 等桶的模板通过 cache 读出
        //  2) AI_AGENT 桶的智能体分支 (SUMMARY/QA/TAG/FINREPT) 通过 agent_code 字段筛选
        //  3) AI_HALLUCINATION 的 sow_contains_any 门控在 cache 读出时校验
        //  4) AI_MODEL 的 industry_in ["AI","AI_AGENT"] 门控在 cache 读出时校验
        // ========================================================================

        // ---- 通用风险 (GENERIC 桶, 任何项目都加) ----
        //    GENERIC 第一条 "客户需求变更" 无 evidence → 不依赖 SOW 命中;
        //    GENERIC 第二条 "关键人员流动" 旧代码 addRisk(evidence=List.of("招聘","NLP","算法")) → 通过 SowGuard evidence 守门
        //    现在 cache-driven, evidence 由 SowExtractor 扫描的 SOW 命中关键词决定.
        //    因此 GENERIC 桶单独扫描通用关键词 (招聘/NLP/算法/算法工程师) 拿到 evidence
        List<String> genericEvidence = collectGenericPeopleEvidence(sowText);
        renderBucket(risks, "GENERIC", List.of(), null, null, sowText);
        if (!genericEvidence.isEmpty()) {
            renderBucketWithEvidence(risks, "GENERIC", genericEvidence, null, null, sowText);
        }

        // ---- 按风险桶触发 (SOW 信号命中的桶, 每条带 evidence = 该桶下命中的 keyword) ----
        for (var entry : ext.riskSignals().entrySet()) {
            String bucket = entry.getKey();
            List<String> evidence = entry.getValue();
            renderBucket(risks, bucket, evidence, null, null, sowText);
        }

        // ---- 行业专属附加 (AI / AI_AGENT) ----
        //    AI_MODEL: industry_in = ["AI","AI_AGENT"], 无 sow_contains_any 门控
        //    AI_HALLUCINATION: industry_in = ["AI_AGENT"], sow_contains_any = [可预测/可追溯/幻觉]
        //    AI_AGENT 智能体分支: agent_code = SUMMARY/QA/TAG/FINREPT
        if ("AI".equals(industry) || "AI_AGENT".equals(industry)) {
            // evidence = SOW 命中的 大模型/Qwen/Agent/RAG/智能体 关键词 (旧 addRisk evidence 字段)
            List<String> aiModelEvidence = new ArrayList<>();
            for (String kw : List.of("大模型", "Qwen", "agent", "Agent", "智能体", "rag", "RAG")) {
                if (sowText != null && sowText.contains(kw) && !aiModelEvidence.contains(kw)) {
                    aiModelEvidence.add(kw);
                }
            }
            renderBucketWithEvidence(risks, "AI_MODEL", aiModelEvidence, industry, null, sowText);
        }
        if ("AI_AGENT".equals(industry) && agents != null) {
            for (Map<String, String> a : agents) {
                String code = a.get("code");
                String name = a.get("name");
                // evidence = SOW 命中的智能体关键词 (例 SUMMARY → 坐席小结)
                List<String> agentEvidence = collectAgentEvidence(code, sowText);
                // 把 {agent_name} 占位符塞进 title 上下文, renderTemplateTitle 里替换
                renderBucket(risks, "AI_AGENT", agentEvidence, industry, Map.of("agent_name", name, "agent_code", code), sowText);
            }
            // 幻觉 / 可追溯 (sow_contains_any 门控由 cache 在内部校验)
            renderBucket(risks, "AI_HALLUCINATION", List.of("可预测", "可追溯"), industry, null, sowText);
        }

        // V4.20 守门白名单: 用整段子串匹配改进版
        //   例外: 风险 evidence 列表里只要有 ≥1 个非空词且该词在 SOW 里存在 → 视为有 SOW 依据, 不丢
        Set<String> sowTokens = SowTokenGuard.tokens(sowText);
        List<Map<String, Object>> rawRisks = new ArrayList<>(risks);
        risks.clear();
        for (Map<String, Object> r : rawRisks) {
            String title = (String) r.getOrDefault("title", "");
            @SuppressWarnings("unchecked")
            List<String> ev = (List<String>) r.getOrDefault("evidence", List.of());
            // 1) evidence 直接命中 SOW → 一定保留
            boolean evidenceHit = false;
            for (String kw : ev) {
                if (kw != null && !kw.isBlank() && sowText.contains(kw)) {
                    evidenceHit = true;
                    break;
                }
            }
            if (evidenceHit) {
                risks.add(r);
                continue;
            }
            // 2) 否则按 title 子串得分判定
            if (SowTokenGuard.score(title, sowText) < SowTokenGuard.MIN_HIT_RATIO) {
                log.info("[SowGuard] drop risk (no SOW evidence): {}", title);
                continue;
            }
            risks.add(r);
        }

        return risks;
    }

    // ========================================================================
    // V4.26 — 风险模板渲染入口 (data-driven, 替换旧 addRisk + switch 32 行)
    // ========================================================================

    /**
     * 从 RiskRuleCache 读出某桶下所有模板, 过滤 industry_in / agent_code / sow_contains_any 后
     * 逐条渲染为风险条目, 加入 risks 列表.
     *
     * <p>关键: 不再 hardcode title / suggestion / level / p / i, 全由 RiskTemplate 提供.
     * <p>context 用于 {agent_name} 占位符替换 (只在 AI_AGENT 智能体模板里有用).
     *
     * @param bucket   桶 code (例 "GENERIC"/"DATA_LABEL"/"AI_AGENT"/"AI_HALLUCINATION")
     * @param evidence 触发的 SOW 关键词列表 (例 ["数据标注","标注"])
     * @param industry 当前行业 (例 "AI"/"AI_AGENT"/"BANKING_CUSTODY"); null = 不门控
     * @param context  占位符上下文 (例 {"agent_name":"坐席小结"}); null = 无占位符
     * @param sowText  SOW 原文, 用于 sow_contains_any 门控
     */
    private void renderBucket(List<Map<String, Object>> risks,
                              String bucket,
                              List<String> evidence,
                              String industry,
                              Map<String, String> context,
                              String sowText) {
        // 1) cache 读出所有模板 (V4.26: cache 不可用时直接跳过 — 单测无 Spring 上下文走硬编码回退)
        if (riskRuleCache == null) {
            log.warn("[renderBucket] RiskRuleCache 未注入, 跳过 bucket={}", bucket);
            return;
        }
        List<RiskTemplate> templates = riskRuleCache.templatesOf(bucket);
        if (templates.isEmpty()) {
            // 未配置模板: 与旧 switch 的"未知桶打 warn"行为一致
            log.warn("[renderBucket] 桶 {} 没有模板 (cache miss), evidence={}", bucket, evidence);
            return;
        }

        // 2) 过滤: industry_in / agent_code / sow_contains_any 全部 AND
        for (RiskTemplate tpl : templates) {
            if (!tpl.getEnabled()) continue;

            // 2a) industry 门控
            if (tpl.getIndustryIn() != null && !tpl.getIndustryIn().isBlank()) {
                Set<String> whitelist = riskRuleCache.parseIndustryIn(tpl.getIndustryIn());
                if (!whitelist.isEmpty() && (industry == null || !whitelist.contains(industry))) {
                    continue;
                }
            }

            // 2b) agent_code 门控 (仅 AI_AGENT 桶有效)
            if (tpl.getAgentCode() != null && !tpl.getAgentCode().isBlank()) {
                String reqAgentCode = context == null ? null : context.get("agent_code");
                if (reqAgentCode == null || !reqAgentCode.equalsIgnoreCase(tpl.getAgentCode())) {
                    continue;
                }
            }

            // 2c) sow_contains_any 门控 (例 AI_HALLUCINATION 须 SOW 含 [可预测/可追溯/幻觉] 任一)
            if (tpl.getSowContainsAny() != null && !tpl.getSowContainsAny().isBlank()) {
                Set<String> requiredKws = riskRuleCache.parseSowContainsAny(tpl.getSowContainsAny());
                if (!requiredKws.isEmpty()) {
                    boolean hit = false;
                    for (String kw : requiredKws) {
                        if (sowText != null && sowText.contains(kw)) { hit = true; break; }
                    }
                    if (!hit) continue;
                }
            }

            // 3) 全部门控通过 → 渲染为风险条目
            String title = renderTemplateTitle(tpl.getTitle(), context);
            risks.add(buildRiskFromTemplate(tpl, bucket, title, evidence));
        }
    }

    /** 替换 title 中的 {key} 占位符 */
    private static String renderTemplateTitle(String rawTitle, Map<String, String> context) {
        if (rawTitle == null || rawTitle.isEmpty() || context == null || context.isEmpty()) {
            return rawTitle == null ? "" : rawTitle;
        }
        String out = rawTitle;
        for (Map.Entry<String, String> e : context.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    /** 从 RiskTemplate + context 构造风险 Map. evidence 为 SOW 关键词列表 (即触发证据). */
    private static Map<String, Object> buildRiskFromTemplate(RiskTemplate tpl, String bucket,
                                                             String title, List<String> evidence) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("bucket", bucket);
        r.put("title", title);
        r.put("evidence", evidence == null ? List.of() : evidence);
        r.put("probability", tpl.getProbability() == null ? 3 : tpl.getProbability());
        r.put("impact", tpl.getImpact() == null ? 3 : tpl.getImpact());
        r.put("level", tpl.getLevel() == null ? "MEDIUM" : tpl.getLevel());
        r.put("suggestion", tpl.getSuggestion() == null ? "" : tpl.getSuggestion());
        return r;
    }

    /**
     * 与 renderBucket 同语义, 但 evidence 由调用方显式传入 (用于 GENERIC 第二条 "关键人员流动" 等
     * 本身没信号来源但仍需证据的模板).
     */
    private void renderBucketWithEvidence(List<Map<String, Object>> risks,
                                          String bucket,
                                          List<String> evidence,
                                          String industry,
                                          Map<String, String> context,
                                          String sowText) {
        // 复用 renderBucket 的过滤逻辑, evidence 透传给 buildRiskFromTemplate 即可
        if (riskRuleCache == null) return;
        List<RiskTemplate> templates = riskRuleCache.templatesOf(bucket);
        if (templates.isEmpty()) return;
        for (RiskTemplate tpl : templates) {
            if (!tpl.getEnabled()) continue;
            // industry 门控
            if (tpl.getIndustryIn() != null && !tpl.getIndustryIn().isBlank()) {
                Set<String> whitelist = riskRuleCache.parseIndustryIn(tpl.getIndustryIn());
                if (!whitelist.isEmpty() && (industry == null || !whitelist.contains(industry))) {
                    continue;
                }
            }
            // agent_code 门控
            if (tpl.getAgentCode() != null && !tpl.getAgentCode().isBlank()) {
                String reqAgentCode = context == null ? null : context.get("agent_code");
                if (reqAgentCode == null || !reqAgentCode.equalsIgnoreCase(tpl.getAgentCode())) {
                    continue;
                }
            }
            // sow_contains_any 门控: 兼容老 evidence=null 场景, 有 evidence 视为命中证据
            if (tpl.getSowContainsAny() != null && !tpl.getSowContainsAny().isBlank()) {
                Set<String> requiredKws = riskRuleCache.parseSowContainsAny(tpl.getSowContainsAny());
                if (!requiredKws.isEmpty()) {
                    boolean hit = false;
                    for (String kw : requiredKws) {
                        if (sowText != null && sowText.contains(kw)) { hit = true; break; }
                    }
                    if (!hit) continue;
                }
            }
            String title = renderTemplateTitle(tpl.getTitle(), context);
            risks.add(buildRiskFromTemplate(tpl, bucket, title, evidence));
        }
    }

    /**
     * GENERIC 第二条 "关键人员流动" 的 evidence: 扫描 SOW 里的人员招聘/算法关键词.
     * 旧 addRisk(evidence=List.of("招聘","NLP","算法")) → 现从 SOW 中重新扫描命中项.
     */
    private static List<String> collectGenericPeopleEvidence(String sowText) {
        if (sowText == null) return List.of();
        List<String> KW = List.of("招聘", "NLP", "算法", "算法工程师");
        List<String> hit = new ArrayList<>();
        for (String kw : KW) {
            if (sowText.contains(kw) && !hit.contains(kw)) hit.add(kw);
        }
        return hit;
    }

    /**
     * AI_AGENT 智能体 evidence: 从 SOW 里嗅探该智能体的特征 keyword (例如 SUMMARY → 坐席小结/通话小结/语音小结).
     * <p>找不到 → 返回空列表 (与原代码 {@code addRisk(risks, "AI_AGENT", List.of("坐席小结"), ...)} 一致).</p>
     */
    private static List<String> collectAgentEvidence(String agentCode, String sowText) {
        if (sowText == null) return List.of();
        // 复用 AGENT_SIGNATURES 的 kw 字段
        Map<String, List<String>> AGENT_KW = Map.of(
                "SUMMARY",  List.of("坐席小结", "通话小结", "语音小结"),
                "QA",       List.of("语音质检", "通话质检", "质检"),
                "TAG",      List.of("语音打标", "通话打标", "打标", "标签"),
                "FINREPT",  List.of("财报分析", "财报", "年报", "招股书")
        );
        List<String> candidates = AGENT_KW.get(agentCode);
        if (candidates == null) return List.of();
        List<String> hit = new ArrayList<>();
        for (String kw : candidates) {
            if (sowText.contains(kw) && !hit.contains(kw)) hit.add(kw);
        }
        return hit;
    }

    /** 风险条目统一构造 (V4.17: 带 bucket + evidence 字段) — V4.26 起由 {@link #buildRiskFromTemplate} 取代, 保留为 deprecated 兼容旧内部调用 */
    @Deprecated
    private void addRisk(List<Map<String, Object>> risks, String bucket, List<String> evidence,
                         String title, int p, int i, String level, String suggestion) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("bucket", bucket);
        r.put("title", title);
        r.put("evidence", evidence);
        r.put("probability", p);
        r.put("impact", i);
        r.put("level", level);
        r.put("suggestion", suggestion);
        risks.add(r);
    }
}
