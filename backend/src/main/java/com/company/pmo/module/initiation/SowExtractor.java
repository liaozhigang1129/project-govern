package com.company.pmo.module.initiation;

import com.company.pmo.module.risk.RiskRuleCache;
import com.company.pmo.module.risk.RiskSignal;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SOW 文本抽取器 (V4.17 修复: 让 AI 真正读懂 SOW)
 *
 * 目的: 从 SOW 自然语言中**结构化抽取**信息, 替代"t.contains('AI')"这种硬匹配。
 * 抽取结果会喂给 generateDraft / generateRisks, 让生成的 WBS / 风险带 SOW 上下文。
 *
 * 8 个维度:
 *  - modules      业务模块清单 (智能派单 / 意图识别 ...)
 *  - techStack    技术栈 (大模型 / ASR / K8s ...)
 *  - timeline     工期 (周/月/天 → weeks)
 *  - budget       预算 (金额 + 货币)
 *  - deliverables 交付物清单 (1) 2) 编号)
 *  - teamHints    团队角色线索 (NLP / 前端 / 算法)
 *  - integrations 集成 / 数据源 (坐席系统 / CRM / 财报)
 *  - riskSignals  风险信号 (ASR / 紧 / 招聘 / 脱敏)
 *
 * 全部用关键词字典 + 正则实现, 不依赖 LLM;
 * 未来接 LLM 时只需在 extract() 末尾追加一段 hybrid 逻辑, 抽取器本体不变。
 */
@Slf4j
public class SowExtractor {

    // ===== 关键词字典 =====

    /** 业务模块线索 (优先级高: 出现"X 模型 / X 智能体 / X 系统"等结构) */
    private static final Pattern MODULE_PATTERN = Pattern.compile(
            "(?<![\\u4e00-\\u9fa5A-Za-z])([\\u4e00-\\u9fa5A-Za-z]{2,15}(?:模型|智能体|系统|模块|报表|看板|引擎|平台|服务))"
    );

    /** 工期正则: 支持 6 个月 / 24 周 / 180 天 */
    private static final Pattern TIMELINE_PATTERN = Pattern.compile(
            "(\\d+)\\s*(个月|月|周|天|人天|人月)"
    );

    /** 预算正则: 38 万 / 50k / 100,0000 / 100w */
    private static final Pattern BUDGET_PATTERN = Pattern.compile(
            "(?:预算|合同|金额|费用|报价)?\\s*[::]?\\s*(\\d+(?:[.,]\\d+)?)\\s*(万|w|W|k|K|亿|元|RMB|CNY|USD|$)?"
    );

    /** 交付物编号行: 1) / 2) / (1) / (2) / 1. / 2. */
    private static final Pattern DELIVERABLE_LINE = Pattern.compile(
            "^[\\s]*(?:\\d+[\\.\\)、]|\\([\\d]+\\))[\\s]*(.{2,80}?)$",
            Pattern.MULTILINE
    );

    /** 技术栈词典 (扩展: 命中加 1) */
    private static final String[] TECH_TOKENS = {
            "大模型", "大语言模型", "LLM", "Qwen", "千问", "GPT", "Claude",
            "AgentUniverse", "智能体", "RAG", "Prompt", "Embedding",
            "ASR", "TTS", "OCR", "NLP",
            "K8s", "K8S", "Kubernetes", "Docker", "微服务", "Spring Cloud",
            "MySQL", "PostgreSQL", "Redis", "MongoDB", "Elasticsearch",
            "Kafka", "RabbitMQ", "Flink", "Spark",
            "React", "Vue", "Angular", "Element",
            "ETL", "BI", "数据仓库", "数据湖", "湖仓一体",
            "Java", "Python", "Go", "Rust", "Node",
            "云原生", "DevOps", "CI/CD", "Jenkins", "GitLab"
    };

    /** 团队角色线索 (后续 Step 4 派遣 / Step 5 风险用) */
    private static final String[] TEAM_TOKENS = {
            "NLP", "算法", "数据标注", "数据分析师", "前端", "后端", "全栈", "架构师",
            "测试", "QA", "运维", "SRE", "实施", "配置", "项目经理", "PM",
            "客户经理", "AR", "售前", "SR", "方案", "FR", "需求", "BA",
            "UI", "UE", "视觉"
    };

/** 集成 / 数据源线索 */
    private static final String[] INTEGRATION_TOKENS = {
            "坐席系统", "CRM", "工单系统", "工单", "客服系统", "呼叫中心",
            "财报", "ERP", "SAP", "Oracle", "钉钉", "飞书", "企微", "企业微信",
            "Slack", "邮件系统", "短信", "IM", "微信公众号", "小程序",
            "征信", "支付", "银行", "保险核心", "保单", "核心系统", "数据中台",
            "BI 系统", "第三方", "API 网关", "OAuth", "SSO", "LDAP",
            "PDF", "Excel", "Word", "影像系统", "扫描", "OCR 平台",
            // V4.17 Step 48: 银行/信贷业务集成
            "人脸识别", "活体检测", "联网核查", "房估宝", "银联",
            "不动产登记", "不动产登记中心", "电子签章", "电子签署",
            "手机银行", "H5", "短信验证", "电子存证", "司法链",
            "央行征信", "百行征信", "反欺诈", "决策引擎",
            // V4.17 Step 54: 保险业务集成
            "银保通", "中保信", "医院", "维修厂", "公估",
            // V4.17 Step 55: 证券业务集成
            "银证", "三方存管", "监控中心", "证监会", "中登",
            "qfii", "沪深港通", "沪深交易所", "仿真环境", "证券业协会",
            // V4.17 Step 56: 银行核心系统集成
            "人行", "网联", "二代支付", "超级网银", "宏观审慎", "east",
            "1104"
    };

    /**
     * 银行/信贷业务模块线索 (V4.17 Step 48)
     * 用于从 SOW 抽取业务模块 (经营贷/抵押贷/消费贷/按揭 等通用)
     */
    private static final String[] BANKING_MODULE_TOKENS = {
            "经营贷", "抵押贷", "消费贷", "按揭", "信贷", "授信", "额度",
            "客户申请", "进件", "客户经理", "调查报告", "审批", "抵押登记",
            "担保", "担保合同", "不动产", "房产预估", "房产评估", "影像采集",
            "配偶征信", "电子存证", "电子签章", "路路通", "标卡"
    };

    /**
     * 保险业务模块线索 (V4.17 Step 54)
     */
    private static final String[] INSURANCE_MODULE_TOKENS = {
            "投保", "核保", "智能核保", "人工核保", "健康告知", "财务核保",
            "查勘", "定损", "车险", "医疗调查", "财产估损", "伤残",
            "理赔", "报案", "立案", "审核", "支付", "回访", "满意度",
            "准备金", "ifrs17", "再保", "公估", "黑名单", "失信",
            "电子保单", "保单", "保险", "续保", "团单", "个单",
            "经纪", "代理", "经纪通", "代理通"
    };

    /**
     * 证券业务模块线索 (V4.17 Step 55)
     */
    private static final String[] SECURITIES_MODULE_TOKENS = {
            "开户", "经纪", "双录", "适当性", "风险测评", "客户分级",
            "交易柜台", "柜台", "撮合", "极速", "低延迟", "fpga",
            "算法交易", "算法", "twap", "vwap", "量化", "sdk",
            "期权", "期货", "otc", "报价",
            "集中风控", "风控", "反洗钱", "尽调", "监控中心", "报送",
            "异常交易", "幌骗", "老鼠仓", "净资本", "风险指标",
            "银证", "三方存管", "清算", "法人清算", "中登", "跨境", "对账",
            "自营", "资管", "托管", "估值", "净值",
            "投行", "ipo", "再融资", "并购", "abs", "资产证券化", "基金", "ta"
    };

    /**
     * 银行核心系统模块线索 (V4.17 Step 56)
     */
    private static final String[] BANKING_CORE_MODULE_TOKENS = {
            "客户主数据", "cif", "客户号", "客户信息整合", "对公", "集团户",
            "评级", "客户合并", "mdm",
            "活期", "定期", "通知存款", "大额存单", "结构性存款", "协定存款",
            "智能存款", "靠档", "阶梯利率", "存款",
            "个贷", "对公贷款", "借据", "贷款", "贷款核心",
            "五级分类", "不良", "拨备", "资产保全", "重组", "核销",
            "贷款定价", "风险定价", "贷后", "预警",
            "总账", "科目体系", "会计分录", "损益结转", "结账", "年结",
            "报表", "科目调整", "调账", "红冲", "内外账",
            "清结算", "支付清算", "二代支付", "超级网银",
            "跨境支付", "外汇", "清算窗口", "日终", "差错",
            "1104", "east", "客户风险", "大额可疑", "宏观审慎", "mpa",
            "现场检查", "政策", "字段映射"
    };

    /**
     * V4.24: 资产托管业务模块线索 (苏州银行托管场景)
     */
    private static final String[] CUSTODY_MODULE_TOKENS = {
            "托管账户", "托管协议", "托管合同", "委托资产",
            "估值核算", "净值估值", "单位净值", "净值核算", "估值表",
            "资金清算", "资金交收", "场外划款", "日终批量", "交收日历",
            "投资监督", "合规监督", "比例监督", "标的监督", "久期监督",
            "信息披露", "披露文件", "公告",
            "机构服务平台", "管理人入口", "委托人入口", "托管人入口",
            "监管报送", "east 报送", "1104 报送",
            "反洗钱", "aml", "kyc"
    };

    /**
     * V4.24: 供应链可视化/物流/采购 业务模块线索
     */
    private static final String[] SUPPLY_CHAIN_MODULE_TOKENS = {
            "供应商主数据", "供应商管理", "供应商准入", "供应商分级", "供应商绩效",
            "采购订单", "采购合同", "po 管理", "询比价", "招投标",
            "库存可视化", "vmi", "安全库存", "库存积压", "库存预警",
            "在途运输", "在途跟踪", "运输轨迹", "gps 跟踪", "tms",
            "异常预警", "预警看板", "预警中心",
            "mes 集成", "wms 集成", "tms 集成", "erp 集成",
            "供应链可视化", "供应链协同", "供应链金融"
    };

    /**
     * 风险信号 → 风险桶映射
     * key=信号词, value=对应风险桶 code (后续 generateRisks 按 code 分类生成)
     */
    private static final Map<String, String> RISK_SIGNAL_TO_BUCKET = Map.ofEntries(
            // 数据
            Map.entry("数据标注", "DATA_LABEL"),
            Map.entry("标注", "DATA_LABEL"),
            Map.entry("样本", "DATA_SAMPLE"),
            Map.entry("数据脱敏", "DATA_COMPLIANCE"),
            Map.entry("脱敏", "DATA_COMPLIANCE"),
            Map.entry("数据迁移", "DATA_MIGRATION"),
            // 模型
            Map.entry("ASR", "MODEL_ASR"),
            Map.entry("OCR", "MODEL_OCR"),
            Map.entry("准确率", "MODEL_ACCURACY"),
            Map.entry("WER", "MODEL_ASR"),
            // 集成
            Map.entry("坐席系统", "INTEG_CALLCENTER"),
            Map.entry("第三方", "INTEG_3RD"),
            Map.entry("API", "INTEG_API"),
            Map.entry("对接", "INTEG_3RD"),
            // 团队
            Map.entry("NLP", "TEAM_NLP"),
            Map.entry("算法工程师", "TEAM_NLP"),
            Map.entry("新员工", "TEAM_NOVICE"),
            // 工期
            Map.entry("赶", "SCHEDULE_TIGHT"),
            Map.entry("紧", "SCHEDULE_TIGHT"),
            Map.entry("并行", "SCHEDULE_PARALLEL"),
            // 预算
            Map.entry("预算", "BUDGET"),
            Map.entry("报价", "BUDGET"),
            // 业务
            Map.entry("目标", "BUSINESS_KPI"),
            Map.entry("KPI", "BUSINESS_KPI"),
            Map.entry("转化", "BUSINESS_KPI"),
            Map.entry("提升", "BUSINESS_KPI"),
            // 合规
            Map.entry("合规", "COMPLIANCE"),
            Map.entry("隐私", "COMPLIANCE"),
            Map.entry("审计", "COMPLIANCE"),
            Map.entry("等保", "COMPLIANCE"),
            Map.entry("ISO", "COMPLIANCE"),
            // V4.17 Step 48: 银行/信贷业务专属风险信号
            Map.entry("抵押登记", "COMPLIANCE"),     // 抵押登记合规要求
            Map.entry("联网核查", "INTEG_3RD"),     // 第三方接口
            Map.entry("人脸识别", "INTEG_3RD"),     // 第三方接口
            Map.entry("征信查询", "COMPLIANCE"),     // 央行征信合规
            Map.entry("电子签章", "COMPLIANCE"),     // CA 证书合规
            Map.entry("担保", "BUSINESS_KPI"),      // 担保率 KPI
            Map.entry("反欺诈", "COMPLIANCE"),      // 反欺诈合规
            Map.entry("房估", "INTEG_3RD"),         // 房估宝等
            Map.entry("信贷", "COMPLIANCE"),        // 信贷业务合规
            // V4.24: 资产托管/银行资管业务专属风险信号
            Map.entry("托管", "CUSTODY"),          // 资产托管业务连续性
            Map.entry("资产托管", "CUSTODY"),
            Map.entry("托管业务", "CUSTODY"),
            Map.entry("托管协议", "CUSTODY"),
            Map.entry("估值核算", "VALUATION"),     // 估值错误
            Map.entry("资金清算", "SETTLEMENT"),    // 清算失败
            Map.entry("投资监督", "SUPERVISION"),  // 投资监督违规
            Map.entry("信息披露", "DISCLOSURE"),   // 信息披露延迟/错误
            Map.entry("净值", "VALUATION"),
            Map.entry("头寸", "SETTLEMENT"),
            Map.entry("交收", "SETTLEMENT"),
            Map.entry("指令", "CUSTODY_OPS"),       // 指令处理失败
            Map.entry("场外划款", "CUSTODY_OPS"),
            Map.entry("反洗钱", "AML"),            // 反洗钱合规 (V4.24: 升级到 AML 桶)
            Map.entry("报送", "REGULATORY"),        // 监管报送
            Map.entry("east", "REGULATORY"),
            Map.entry("1104", "REGULATORY"),
            Map.entry("培训", "KNOWLEDGE_TRANSFER"),// 培训/知识转移不充分
            Map.entry("知识转移", "KNOWLEDGE_TRANSFER"),
            Map.entry("驻场", "ONSITE"),            // 驻场约束
            Map.entry("授信", "BUSINESS_KPI"),       // 授信额度 KPI
            // V4.17 Step 54: 保险业务专属风险信号
            Map.entry("准备金", "COMPLIANCE"),      // 监管合规
            Map.entry("ifrs17", "COMPLIANCE"),      // IFRS17 国际准则
            Map.entry("精算", "COMPLIANCE"),        // 精算合规
            Map.entry("黑名单", "COMPLIANCE"),      // 反欺诈名单
            Map.entry("保单", "DATA_COMPLIANCE"),   // 保单数据合规
            Map.entry("报案", "BUSINESS_KPI"),      // 报案量 KPI
            Map.entry("理赔", "BUSINESS_KPI"),      // 理赔时效 KPI
            Map.entry("续保率", "BUSINESS_KPI"),    // 续保率 KPI
            // V4.17 Step 55: 证券业务专属风险信号
            Map.entry("集中风控", "COMPLIANCE"),    // 监管强制
            Map.entry("监控中心", "COMPLIANCE"),    // 证监会报送
            Map.entry("适当性", "COMPLIANCE"),      // 投资者适当性
            Map.entry("双录", "COMPLIANCE"),        // 销售双录合规
            Map.entry("净资本", "COMPLIANCE"),      // 净资本监控
            Map.entry("量化", "TECH_NEW"),          // 新技术依赖
            Map.entry("算法交易", "TECH_NEW"),      // 新技术依赖
            Map.entry("跨境", "COMPLIANCE"),        // 跨境合规
            Map.entry("自营", "BUSINESS_KPI"),      // 自营 KPI
            Map.entry("资管", "BUSINESS_KPI"),      // 资管 KPI
            Map.entry("投行", "EXEC_REG"),          // 投行承做监管
            Map.entry("ipo", "EXEC_REG"),           // IPO 监管
            // V4.17 Step 56: 银行核心系统专属风险信号
            Map.entry("核心系统", "EXEC_REG"),      // 核心系统改造风险
            Map.entry("五级分类", "COMPLIANCE"),    // 监管合规
            Map.entry("不良", "BUSINESS_KPI"),      // 不良率 KPI
            Map.entry("拨备", "BUSINESS_KPI"),      // 拨备覆盖率 KPI
            Map.entry("存款", "EXEC_REG"),          // 存款保险条例
            Map.entry("宏观审慎", "COMPLIANCE"),    // MPA 监管
            Map.entry("外汇", "COMPLIANCE"),        // 外汇管理合规
            Map.entry("跨境支付", "COMPLIANCE"),    // 跨境合规
            // V4.24: 供应链可视化/物流/采购/库存 专属风险信号
            Map.entry("供应商管理", "SUPPLY_VENDOR"),     // 供应商管理风险
            Map.entry("供应商", "SUPPLY_VENDOR"),         // 供应商准入/质量/集中度
            Map.entry("采购", "SUPPLY_PROCURE"),          // 采购合规与效率
            Map.entry("库存", "SUPPLY_INVENTORY"),        // 库存积压/缺货/准确率
            Map.entry("在途", "SUPPLY_INTRANSIT"),       // 在途跟踪丢失/延误
            Map.entry("运输", "SUPPLY_INTRANSIT"),        // 运输异常
            Map.entry("物流", "SUPPLY_INTRANSIT"),        // 物流时效
            Map.entry("可视化", "BUSINESS_KPI"),          // 看板 KPI
            Map.entry("异常预警", "SUPPLY_ALERT"),         // 预警准确���/误报
            Map.entry("预警", "SUPPLY_ALERT"),
            Map.entry("看板", "BUSINESS_KPI"),            // 看板 KPI
            Map.entry("mes", "INTEG_3RD"),                // MES 集成
            Map.entry("wms", "INTEG_3RD"),                // WMS 集成
            Map.entry("tms", "INTEG_3RD"),                // TMS 集成
            Map.entry("erp", "INTEG_3RD"),                // ERP 集成
            Map.entry("gps", "INTEG_3RD"),                // GPS/IoT 集成
            Map.entry("iot", "INTEG_3RD")                 // IoT 设备集成
    );

    /**
     * 抽取结果 DTO (用 record 保证不可变 + toString 自带)
     */
    public record Extraction(
            String industry,             // AI_AGENT / AI / CRM / ERP / DATA / CLOUDNATIVE / GENERIC
            List<String> modules,        // 业务模块名
            List<String> techStack,      // 技术栈 token
            Integer durationWeeks,       // 工期 (周)  0 = 未知
            String durationRaw,          // 原始字符串 (例: "6 个月")
            Long budgetCents,            // 预算 (分) 0 = 未知
            String budgetRaw,            // 原始字符串
            List<String> deliverables,   // 交付物
            List<String> teamHints,      // 团队角色
            List<String> integrations,   // 集成
            Map<String, List<String>> riskSignals  // 风险信号 → 触发关键词列表
    ) {
        public boolean isEmpty() {
            return modules.isEmpty() && techStack.isEmpty() && deliverables.isEmpty()
                    && durationWeeks == 0 && budgetCents == 0;
        }
    }

    /**
     * 主入口: 从 SOW 文本抽全部维度
     * @param sowText 用户贴入的 SOW 文本 (已 trim, 已合并文件内容)
     * @param industryHint 行业线索 (可空: 来自 SOW 关键词, 例如 AI/CRM/ERP)
     */
    public static Extraction extract(String sowText, String industryHint) {
        if (sowText == null || sowText.isBlank()) {
            return empty();
        }
        String text = normalizeForAi(sowText);

        String industry = industryHint != null && !industryHint.isBlank()
                ? industryHint
                : detectIndustry(text);

        return new Extraction(
                industry,
                extractModules(text),
                extractTechStack(text),
                extractTimeline(text),
                extractTimelineRaw(text),
                extractBudgetCents(text),
                extractBudgetRaw(text),
                extractDeliverables(text),
                extractTokens(text, TEAM_TOKENS),
                extractTokens(text, INTEGRATION_TOKENS),
                extractRiskSignals(text)
        );
    }

    public static Extraction extract(String sowText) {
        return extract(sowText, null);
    }

    private static Extraction empty() {
        return new Extraction("GENERIC", List.of(), List.of(), 0, "", 0L, "",
                List.of(), List.of(), List.of(), Map.of());
    }

    // ===== 各维度抽取 =====

    /**
     * V4.17 (Fix-1) 文本规范化: 让"同一 SOW 同一结果"。
     * <p>目的: 消除前/后端文本差异(空白/全半角/繁简/不可见字符/SOH 分隔符), 让
     * {@link #detectIndustry(String)} / 关键词门控 / {@link #MODULE_PATTERN} 抽取结果
     * <b>与文本外观无关, 只与业务语义有关</b>。</p>
     * <p><b>关键: 多行结构保留</b>(deliverables 抽取依赖 multiline + ^$ 锚定)。</p>
     *
     * <ol>
     *   <li>统一换行符 (\r\n / \r → \n)</li>
     *   <li>剥离 SOH 分隔符 (\u0001) 与 [SOW/xxx] 前缀标记</li>
     *   <li>全角空格 (　) / 半角空格 / tab → 单个半角空格</li>
     *   <li>全角标点 → 半角标点 (排除 CJK 字符)</li>
     *   <li>繁简统一 (重点行业术语: 经营贷/貸款/信贷/授信 ...)</li>
     *   <li>trim</li>
     * </ol>
     *
     * 注: 繁简映射只覆盖行业高频词, 完整方案需引入 opencc4j (backlog)。
     */
    public static String normalizeForAi(String s) {
        if (s == null) return "";
        String t = s;

        // 1. 统一换行
        t = t.replace("\r\n", "\n").replace("\r", "\n");

        // 2. 剥离 SOH + SOW marker 前缀 (resolveSowText 加的 [SOW/BODY] / [SOW/PASTE] / [SOW/FILE:xxx])
        t = t.replace("\u0001[SOW/BODY]\u0001\n", "\n");
        t = t.replace("\u0001[SOW/PASTE]\u0001\n", "\n");
        t = t.replaceAll("\u0001\\[SOW/FILE:[^\\]]+\\]\\u0001\\n", "\n");
        t = t.replace("\u0001", "");   // 兜底剥光所有 SOH

        // 3. 全角空格 / 半角空格 / tab → 单个半角空格 (行内); 行首尾去掉
        t = t.replaceAll("[ \\t　]+", " ");
        // 多行压紧空白 (但保留 \n)
        t = t.replaceAll(" *\\n *", "\n");
        // 连续空行 → 单空行
        t = t.replaceAll("\\n{3,}", "\n\n");

        // 4. 全角标点 → 半角 (括号 / 逗号 / 冒号 / 分号 — 关键词匹配不吃全角)
        t = t.replace("（", "(").replace("）", ")")
             .replace("，", ",").replace("：", ":").replace("；", ";")
             .replace("！", "!").replace("？", "?")
             // 4.1 CJK 圆圈数字 ㈠-㈩ → (1)-(10) — 经营贷 SOW 常用
             .replace("㈠", "(1)").replace("㈡", "(2)")
             .replace("㈢", "(3)").replace("㈣", "(4)")
             .replace("㈤", "(5)").replace("㈥", "(6)")
             .replace("㈦", "(7)").replace("㈧", "(8)")
             .replace("㈨", "(9)").replace("㈩", "(10)");

        // 5. 繁简统一 (金融行业高频词 — 让"經營貸" == "经营贷")
        t = t.replace("經營貸", "经营贷").replace("貸款", "贷款")
             .replace("經營", "经营").replace("擔保", "担保")
             .replace("資產", "资产").replace("風險", "风险")
             .replace("證券", "证券").replace("賬戶", "账户")
             .replace("數據", "数据").replace("業務", "业务")
             .replace("客戶", "客户").replace("認證", "认证");

        // 6. trim
        return t.trim();
    }

    /** 兼容旧调用 (normalize → normalizeForAi) */
    private static String normalize(String s) {
        return normalizeForAi(s);
    }

    private static String detectIndustry(String text) {
        String t = text.toLowerCase();
        // V4.20: 优先级重排 + 严格化 — "主业务行业" 应优先于 "AI_AGENT"
        // 保险/银行核心/证券/信贷 等行业一旦显式出现,优先锁定主行业,不降级到 AI_AGENT
        // (否则保险 SOW 含"基于大模型"会被错认为 AI_AGENT,模板风险"AI 幻觉"等会污染)
        // V4.20 fix-2: 但如果 SOW 含"智能体+大模型+Qwen/RAG/AgentUniverse"等智能体强信号,
        // 即使含"征信/授信/担保",也应优先 AI_AGENT (因为主业务其实是大模型产品,不是信贷产品)

        // 0) AI_AGENT 抢占 (优先级最高)
        boolean hasAgentKwEarly = t.contains("智能体") || t.contains("agent");
        boolean hasAiModelEarly = t.contains("大模型") || t.contains("大语言模型") || t.contains("llm")
                || t.contains("rag") || t.contains("agentuniverse") || t.contains("qwen");
        boolean hasExplicitAgentFormEarly =
                t.contains("坐席") || t.contains("语音") || t.contains("打标")
                || t.contains("财报分析") || t.contains("agentuniverse") || t.contains("多模态")
                || t.contains("画像") || t.contains("rag") || t.contains("poc");
        if (hasAgentKwEarly && (hasAiModelEarly || hasExplicitAgentFormEarly)) return "AI_AGENT";

        // V4.24: 资产托管/银行资管业务 — 苏州银行托管业务场景
        // 优先级: BANKING_CUSTODY 应在 BANKING_CORE / SECURITIES 之前
        // 含托管三件套(估值核算/资金清算/信息披露) 应优先 BANKING_CUSTODY
        boolean isCustody =
                t.contains("托管") || t.contains("资产托管") || t.contains("托管业务")
                || (t.contains("估值核算") && (t.contains("托管") || t.contains("监督")))
                || t.contains("资金清算")
                || t.contains("投资监督")
                || (t.contains("信息披露") && (t.contains("托管") || t.contains("净值")));
        if (isCustody) return "BANKING_CUSTODY";

        // 1) 金融业判定优先 (出现即锁定, 即便含"大模型")
        if (t.contains("保险") || t.contains("核保") || t.contains("投保") || t.contains("理赔")
                || t.contains("车险") || t.contains("财险") || t.contains("寿险")
                || t.contains("查勘") || t.contains("定损") || t.contains("准备金")
                || t.contains("ifrs17") || t.contains("保单") || t.contains("续保")) return "INSURANCE";
// V4.24 fix-5: SUPPLY_CHAIN 必须在 DATA 之前 — 供应链 SOW 也含 Spark/Flink
        //   用更具体的"供应商 + 库存 + 在途/运输/可视化"组合
        // V4.24 fix-7: 也必须在 BANKING_LOAN 之前 — 供应链 SOW 含"采购合同"
        boolean isSupplyChainNow =
                (t.contains("供应商") && (t.contains("采购") || t.contains("库存") || t.contains("在途")))
                || t.contains("运输轨迹") || t.contains("物流轨迹")
                || (t.contains("可视化") && (t.contains("库存") || t.contains("在途") || t.contains("运输")))
                || (t.contains("异常预警") && (t.contains("物流") || t.contains("在途") || t.contains("库存")));
        if (isSupplyChainNow) return "SUPPLY_CHAIN";

        // V4.24 fix-4: DATA 必须在 BANKING_LOAN 之前
        // 数据迁移/ETL/数仓/Spark/Flink SOW 即使含"信贷/征信/支付/担保",主业务仍是数据项目
        // V4.24 fix-8: 但制造 ERP "数据迁移" 是交付物动作,不是数据栈 — 必须有"ETL/数仓/湖仓/spark/flink"才认
        boolean isDataProject =
                t.contains("etl")
                || t.contains("数据湖") || t.contains("数据仓库") || t.contains("数仓")
                || t.contains("数据集市") || t.contains("数据中台") || t.contains("湖仓一体")
                || t.contains("湖仓") || t.contains("hive") || t.contains("hadoop")
                || t.contains("数据质量") || t.contains("bi");
        if (isDataProject) return "DATA";
        if (t.contains("经营贷") || t.contains("抵押贷") || t.contains("消费贷") || t.contains("按揭")
                || t.contains("信贷") || t.contains("授信") || t.contains("征信")
                || t.contains("担保") || t.contains("抵押登记") || t.contains("五级分类")) return "BANKING_LOAN";
        if (t.contains("核心系统") || t.contains("总账") || t.contains("客户主数据")
                || t.contains("cif") || t.contains("客户号") || t.contains("五级分类")
                || t.contains("不良") || t.contains("拨备") || t.contains("活期")
                || t.contains("定期") || t.contains("大额存单") || t.contains("结构性存款")
                || t.contains("存款") || t.contains("贷款核心")
                || t.contains("east") || t.contains("1104") || t.contains("超级网银")
                || t.contains("外汇") || t.contains("二代支付") || t.contains("人行")) return "BANKING_CORE";
        if (t.contains("证券") || t.contains("经纪") || t.contains("券商") || t.contains("交易柜台")
                || t.contains("自营") || t.contains("资管") || t.contains("投行") || t.contains("ipo")
                || t.contains("银证") || t.contains("三方存管") || t.contains("适当性")
                || t.contains("集中风控") || t.contains("中登") || t.contains("qfii")
                || t.contains("估值") || t.contains("净值")) return "SECURITIES";

        // 2) AI_AGENT 严格化: 必须显式含 agent 形态 (坐席/语音/打标/财报分析 等)
        //    仅含"大模型 + 智能体"不够,避免误判普通 AI 项目
        boolean hasAgentKw = t.contains("智能体") || t.contains("agent");
        boolean hasExplicitAgentForm =
                t.contains("坐席") || t.contains("语音") || t.contains("打标")
                || t.contains("财报分析") || t.contains("agentuniverse") || t.contains("多模态");
        if (hasAgentKw && hasExplicitAgentForm) return "AI_AGENT";
        if (t.contains("ai") || t.contains("模型") || t.contains("大模型")
                || t.contains("大语言模型") || t.contains("llm")) return "AI";

        // V4.24: SUPPLY_CHAIN 优先级应在 ERP 之前 (含"供应商/采购/库存/在途/运输")
        // ERP 判定过宽 ("供应链" 一词命中太容易), 会污染可视化 / 协同 / 跟踪类项目
        // 注意: 制造 ERP SOW 也含"供应链", 用"采购/库存/运输"具体动作词 + ERP 关键词反向过滤
        boolean hasSupplyAction = t.contains("供应商") || t.contains("采购") || t.contains("库存")
                || t.contains("在途") || t.contains("运输") || t.contains("物流")
                || t.contains("可视化") && (t.contains("运输") || t.contains("库存"));
        boolean hasErpContext = t.contains("sap") || t.contains("用友") || t.contains("金蝶")
                || t.contains("oracle ebs") || t.contains("财务模块")
                || (t.contains("erp") && !hasSupplyAction);  // 纯 ERP 词无供应动作才算 ERP
        boolean isSupplyChain = hasSupplyAction && !hasErpContext;
        if (isSupplyChain) return "SUPPLY_CHAIN";

        if (t.contains("erp") || t.contains("财务")) return "ERP";
// V4.24 fix-9: 通用 ERP / 数据 / 云原生 兜底行业(不踩强关键词)
        // ERP_SOW 含 "物料/采购/库存/销售" 但未出现 "供应商 + 在途/运输", 仍是 ERP
        // DATA_SOW 含 "ETL/指标/BI" 强关键词, 走 DATA
        // CLOUDNATIVE_SOW 含 "K8s/Istio" 强关键词, 走 CLOUDNATIVE
        boolean hasSupply = t.contains("供应商") && (t.contains("采购") || t.contains("库存"));
        boolean hasErpGeneric = !hasSupply && (t.contains("物料") || t.contains("用友") || t.contains("金蝶")
                || t.contains("财务模块") || t.contains("bom"));
        if (hasErpGeneric) return "ERP";

        // V4.24: DATA 优先级应在 BANKING_LOAN/BANKING_CORE 之前
        // 数据迁移 / ETL / 数仓 类 SOW 即使含 "信贷/支付" 关键词, 主要是数据项目
        boolean hasDataStack = (t.contains("etl") && !t.contains("sap"))
                || t.contains("数据湖") || t.contains("数据仓库") || t.contains("数仓")
                || t.contains("bi") || t.contains("数据集��") || t.contains("数据中台")
                || t.contains("hive") || t.contains("hadoop")
                || (t.contains("spark") || t.contains("flink")) && !hasSupply;
        if (hasDataStack) return "DATA";
        if (t.contains("数据") || t.contains("bi") || t.contains("报表") || t.contains("etl")) return "DATA";
        if (t.contains("k8s") || t.contains("docker") || t.contains("微服务")) return "CLOUDNATIVE";
        // V4.20: 老的 BANKING_CORE fallback(已在上面优先命中,这里只是兜底)
        if (t.contains("核心系统") || t.contains("总账") || t.contains("客户主数据")
                || t.contains("cif") || t.contains("客户号") || t.contains("五级分类")
                || t.contains("不良") || t.contains("拨备") || t.contains("活期")
                || t.contains("定期") || t.contains("大额存单") || t.contains("结构性存款")
                || t.contains("存款") || t.contains("贷款核心")
                || t.contains("清结算") || t.contains("二代支付") || t.contains("超级网银")
                || t.contains("1104") || t.contains("east") || t.contains("宏观审慎")
                || t.contains("mpa") || t.contains("外汇") || t.contains("跨境支付")) return "BANKING_CORE";
        if (t.contains("经营贷") || t.contains("抵押贷") || t.contains("消费贷")
                || t.contains("按揭") || t.contains("信贷") || t.contains("授信")
                || t.contains("征信") || t.contains("担保") || t.contains("抵押登记")
                || t.contains("调查报告") || t.contains("小微")) return "BANKING_LOAN";
        return "GENERIC";
    }

    private static List<String> extractModules(String text) {
        Matcher m = MODULE_PATTERN.matcher(text);
        Set<String> out = new LinkedHashSet<>();
        while (m.find()) {
            String mod = m.group(1);
            // 过滤噪音: 跳过纯技术词 + 太通用的 (系统/平台单独提)
            if (isNoiseToken(mod)) continue;
            out.add(mod);
            if (out.size() >= 16) break;   // 业务模块通常不会超 12
        }
        // V4.17 Step 48: 银行/信贷业务模块补充 (经营贷/抵押贷/调查报告 等 MODULE_PATTERN 抽不到)
        for (String banking : BANKING_MODULE_TOKENS) {
            if (text.contains(banking)) {
                out.add(banking);
                if (out.size() >= 16) break;
            }
        }
        // V4.17 Step 54: 保险业务模块补充
        for (String ins : INSURANCE_MODULE_TOKENS) {
            if (text.contains(ins)) {
                out.add(ins);
                if (out.size() >= 16) break;
            }
        }
        // V4.17 Step 55: 证券业务模块补充
        for (String sec : SECURITIES_MODULE_TOKENS) {
            if (text.contains(sec)) {
                out.add(sec);
                if (out.size() >= 16) break;
            }
        }
        // V4.17 Step 56: 银行核心系统模块补充
        for (String bc : BANKING_CORE_MODULE_TOKENS) {
            if (text.contains(bc)) {
                out.add(bc);
                if (out.size() >= 16) break;
            }
        }
        // V4.24: 资产托管业务模块补充
        for (String c : CUSTODY_MODULE_TOKENS) {
            if (text.contains(c)) {
                out.add(c);
                if (out.size() >= 16) break;
            }
        }
        // V4.24: 供应链可视化模块补充
        for (String sc : SUPPLY_CHAIN_MODULE_TOKENS) {
            if (text.contains(sc)) {
                out.add(sc);
                if (out.size() >= 16) break;
            }
        }
        return new ArrayList<>(out);
    }

    private static boolean isNoiseToken(String s) {
        // 通用名词 + 单字过滤
        if (s.length() < 2) return true;
        String[] noise = {"系统", "平台", "服务", "模块"};  // 太泛, 但保留带前缀的
        for (String n : noise) {
            if (s.equals(n)) return true;
        }
        return false;
    }

    private static List<String> extractTechStack(String text) {
        return extractTokens(text, TECH_TOKENS);
    }

    private static List<String> extractTokens(String text, String[] tokens) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String tk : tokens) {
            if (text.contains(tk) && seen.add(tk.toLowerCase())) {
                out.add(tk);
            }
        }
        return out;
    }

    private static Integer extractTimeline(String text) {
        Matcher m = TIMELINE_PATTERN.matcher(text);
        int maxWeeks = 0;
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            String unit = m.group(2);
            int weeks = switch (unit) {
                case "月", "个月", "人月" -> n * 4;        // 1 月 = 4 周 (按 4 周粒度)
                case "周"                 -> n;
                case "天"                 -> Math.max(1, n / 7);
                case "人天"               -> Math.max(1, n / 5);   // 1 人天 ≈ 1 工作日
                default                   -> 0;
            };
            maxWeeks = Math.max(maxWeeks, weeks);
        }
        return maxWeeks;
    }

    private static String extractTimelineRaw(String text) {
        Matcher m = TIMELINE_PATTERN.matcher(text);
        if (m.find()) return m.group();
        return "";
    }

    private static Long extractBudgetCents(String text) {
        Matcher m = BUDGET_PATTERN.matcher(text);
        long maxCents = 0;
        while (m.find()) {
            String num = m.group(1).replace(",", "");
            String unit = m.group(2);
            double v = Double.parseDouble(num);
            double cents = switch (unit == null ? "" : unit.toLowerCase()) {
                case "万", "w"  -> v * 1_000_000L;   // 1 万 = 10,000 元 = 1,000,000 分
                case "k"        -> v * 100_000L;     // 1k 元 = 100,000 分
                case "亿"       -> v * 100_000_000L * 100L;
                case "元", "rmb", "cny" -> v * 100;   // 1 元 = 100 分
                case "$", "usd" -> v * 700L * 100L;  // 粗估汇率 7
                default          -> v * 100;          // 默认按元
            };
            maxCents = Math.max(maxCents, (long) cents);
        }
        return maxCents;
    }

    private static String extractBudgetRaw(String text) {
        // 找带单位的那个 (万/亿/元/w/k/$), 跳过裸数字
        Matcher m = BUDGET_PATTERN.matcher(text);
        while (m.find()) {
            String unit = m.group(2);
            if (unit != null && !unit.isBlank()) {
                return m.group().trim();
            }
        }
        return "";
    }

    private static List<String> extractDeliverables(String text) {
        Matcher m = DELIVERABLE_LINE.matcher(text);
        List<String> out = new ArrayList<>();
        while (m.find()) {
            String line = m.group(1).trim();
            if (line.length() >= 2 && line.length() <= 60 && !line.contains("：") && !line.contains(":")) {
                out.add(line);
            }
            if (out.size() >= 12) break;
        }
        return out;
    }

    private static Map<String, List<String>> extractRiskSignals(String text) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        // V4.26: 优先用 RiskRuleCache (DB 配置), 回退到硬编码 Map (兼容单测/无 Spring 上下文场景)
        if (riskRuleCache != null) {
            Map<String, List<RiskSignal>> signalIdx = riskRuleCache.getSignalsByKeyword().get();
            for (Map.Entry<String, List<RiskSignal>> e : signalIdx.entrySet()) {
                if (text.contains(e.getKey())) {
                    for (RiskSignal s : e.getValue()) {
                        // 当前 industry 全 NULL, 全部放行
                        if (s.getIndustry() != null && !s.getIndustry().isBlank()) continue;
                        out.computeIfAbsent(s.getBucketCode(), k -> new ArrayList<>()).add(e.getKey());
                    }
                }
            }
            return out;
        }
        for (var e : RISK_SIGNAL_TO_BUCKET.entrySet()) {
            if (text.contains(e.getKey())) {
                out.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
            }
        }
        return out;
    }

    // ===== V4.26: RiskRuleCache 注入 (Spring 自动装配, 单测场景保持 null) =====

    private static volatile RiskRuleCache riskRuleCache;

    /** 由 InitiationAiWbsService / Spring 注入. 单测场景不注入, 走硬编码回退. */
    public static void setRiskRuleCache(RiskRuleCache cache) {
        riskRuleCache = cache;
    }
}
