package com.company.zhiyu.module.initiation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * V4.17 Fix-3: 通用兜底行业 (CRM / ERP / 数据 / 云原生) 的 WP 模板数据。
 * <p>key = "<code>industry:mCode</code>", value = 该阶段下 WP 模板列表 (与专属行业模板同结构)</p>
 * <p>每条模板字段: name / role / hours / deliv / requiredKws</p>
 * <p><b>设计原则</b>:
 * <ul>
 *   <li>每阶段 4-6 个真实业务 WP</li>
 *   <li>requiredKws 关键词门控 — SOW 没提到就不生成</li>
 *   <li>role 准确 — 不再全是 FULLSTACK</li>
 *   <li>deliverable 是"业务交付物"而非"评审通过"</li>
 * </ul>
 * </p>
 */
public final class GenericIndustryWps {

    private GenericIndustryWps() {}

    public static Map<String, List<Map<String, Object>>> build() {
        Map<String, List<Map<String, Object>>> m = new HashMap<>();
        // ====================== CRM ======================
        put(m, "CRM", "1", List.of(
            tpl("客户访谈 + 需求调研", "BA", 32, "客户访谈纪要 + 业务需求清单", List.of("客户", "业务")),
            tpl("业务流程梳理 (BPMN)", "BA", 24, "BPMN 流程图 + 现状/将来对比", List.of("流程", "业务")),
            tpl("干系人识别 + 影响力分析", "PM", 16, "干系人矩阵 + RACI 表", List.of()),
            tpl("CRM 选型 + 自研 vs SaaS 评估", "PM", 24, "选型报告 + POC 结论", List.of("选型", "对比", "评估")),
            tpl("需求文档编写 + 评审", "BA", 24, "PRD 文档 + 评审纪要", List.of()),
            tpl("数据迁移评估 (客户/商机/合同历史数据)", "DATA", 16, "数据迁移评估报告 + 数据量清单", List.of("数据", "迁移"))
        ));
        put(m, "CRM", "2", List.of(
            tpl("客户主数据建模 (CIF / 标签)", "BA", 24, "客户主数据模型 + 标签体系", List.of("客户", "主数据")),
            tpl("商机管理流程设计 (阶段 / 赢率 / 漏斗)", "BA", 24, "商机阶段定义 + 漏斗规则", List.of("商机", "销售")),
            tpl("销售漏斗报表设计", "BA", 16, "漏斗报表需求文档", List.of("报表", "漏斗")),
            tpl("移动端原型设计 (iOS / Android)", "FRONTEND", 40, "移动端原型 + 交互稿", List.of("移动", "app", "手机")),
            tpl("UI 设计系统 + 视觉规范", "FRONTEND", 32, "UI Kit + 视觉规范文档", List.of("ui", "视觉", "设计"))
        ));
        put(m, "CRM", "3", List.of(
            tpl("客户主数据 CRUD 模块开发", "SR", 40, "客户管理 API + 页面", List.of("客户", "主数据")),
            tpl("商机管理模块 (新建/跟进/转客户)", "SR", 40, "商机 API + 列表/详情页", List.of("商机")),
            tpl("销售活动管理 (电话/拜访/邮件)", "SR", 32, "活动记录 + 日程", List.of("活动", "拜访")),
            tpl("合同/订单模块开发", "SR", 40, "合同 API + 审批流", List.of("合同", "订单")),
            tpl("客户 360 视图聚合", "SR", 32, "客户详情聚合页 + API", List.of("客户视图", "画像")),
            tpl("前端移动端开发 (Flutter / RN)", "FRONTEND", 80, "移动端 APP + 离线缓存", List.of("移动", "app"))
        ));
        put(m, "CRM", "4", List.of(
            tpl("内部接口联调 (订单中心 / 财务)", "QA", 24, "内部联调测试报告", List.of()),
            tpl("外部接口联调 (短信 / 邮件 / 支付)", "QA", 32, "外部接口联调报告", List.of("短信", "邮件", "支付")),
            tpl("端到端业务流程联调 (线索→商机→合同)", "QA", 40, "E2E 联调报告", List.of()),
            tpl("性能压测 (1000 并发用户)", "QA", 16, "性能压测报告", List.of("性能", "压测"))
        ));
        put(m, "CRM", "5", List.of(
            tpl("UAT 验收测试 (业务方主导)", "QA", 32, "UAT 测试报告 + 缺陷清单", List.of("uat", "验收")),
            tpl("灰度发布 (5% -> 20% -> 100%)", "SR", 16, "灰度发布报告", List.of("灰度")),
            tpl("客户经理培训 + 操作手册", "PM", 16, "培训手册 + 视频", List.of("培训", "手册")),
            tpl("生产环境部署 + 监控告警", "SR", 16, "上线报告 + 监控仪表盘", List.of("部署", "上线"))
        ));
        put(m, "CRM", "6", List.of(
            tpl("项目验收会 + 交付物清单核对", "PM", 8, "验收会议纪要", List.of()),
            tpl("运维移交 + SLA 签订", "PM", 8, "移交清单 + SLA 协议", List.of()),
            tpl("风险复盘 + 经验沉淀", "PM", 8, "复盘文档", List.of()),
            tpl("财务结算 (按工时费率核算)", "PM", 8, "结算单", List.of())
        ));
        // ====================== ERP ======================
put(m, "ERP", "1", List.of(
            tpl("业务蓝图设计 (财务/供应链/生产流程)", "BA", 40, "业务蓝图文档 + 流程图", List.of("蓝图", "流程", "业务")),
            tpl("现状调研 + 痛点清单", "BA", 32, "调研报告 + 痛点清单", List.of()),
            tpl("ERP 模块选型 (SAP/Oracle/用友/金蝶)", "PM", 24, "选型对比报告", List.of()),
            tpl("关键用户访谈 (财务总监/采购经理/车间主任)", "BA", 24, "用户访谈纪要", List.of()),
            tpl("干系人识别 + 决策委员会成立", "PM", 16, "决策委员会章程", List.of())
        ));
        put(m, "ERP", "2", List.of(
            tpl("主数据建模 (物料/客户/供应商/会计科目)", "BA", 40, "主数据模型文档", List.of()),
            tpl("财务模块配置 (总账/应收应付/资产)", "BA", 40, "财务配置方案", List.of("财务")),
            tpl("供应链模块配置 (采购/库存/销售)", "BA", 40, "供应链配置方案", List.of("供应链")),
            tpl("生产模块配置 (BOM/工艺路线/车间)", "BA", 32, "生产配置方案", List.of()),
            tpl("权限矩阵设计 (角色/菜单/字段级)", "BA", 24, "权限矩阵表", List.of())
        ));
        put(m, "ERP", "3", List.of(
            tpl("历史数据迁移 (物料/客户/供应商/单据)", "DATA", 80, "数据迁移工具 + 校验报告", List.of()),
            tpl("主数据初始化 + 导入", "DATA", 24, "主数据导入脚本 + 校验报告", List.of()),
            tpl("期初余额录入 + 试算平衡", "BA", 24, "期初余额表 + 试算平衡报告", List.of()),
            tpl("接口开发 (HR/OA/MES/PLM)", "SR", 40, "接口对接文档 + 测试报告", List.of()),
            tpl("报表开发 (资产负债表/利润表/自定义)", "SR", 40, "报表模板 + 自定义报表", List.of())
        ));
        put(m, "ERP", "4", List.of(
            tpl("关键用户培训 (财务/采购/销售/生产)", "PM", 40, "培训签到 + 培训反馈", List.of()),
            tpl("操作手册编写 + 视频录制", "PM", 24, "操作手册 + 培训视频", List.of("手册", "文档", "培训")),
            tpl("考试认证 + 上岗证发放", "PM", 16, "考试成绩单 + 上岗证", List.of("考试", "认证", "培训"))
        ));
        put(m, "ERP", "5", List.of(
            tpl("并行期切换 (新+旧系统并行 1 个月)", "SR", 40, "并行运行报告 + 差异清单", List.of("并行", "切换")),
            tpl("用户问题收集 + 工单处理", "SR", 40, "问题清单 + SLA 达成报告", List.of("问题", "工单")),
            tpl("数据对账 (新旧系统数据一致性)", "QA", 24, "对账报告", List.of("对账")),
            tpl("最终切换 + 旧系统下线", "SR", 16, "切换报告 + 下线确认", List.of("切换", "下线"))
        ));
        put(m, "ERP", "6", List.of(
            // V4.17 Step 41: 验收阶段必须有 1 个 WP 名字含 SOW 实际交付物 (例如 "交付物验证: 业务蓝图文档")
            //   由 generateDraft 的 contextual 旁路生成, 不走模板 requiredKws 门控
            tpl("运维移交 + 二线支持签订", "PM", 8, "移交清单", List.of()),
            tpl("风险复盘 + 经验沉淀", "PM", 8, "复盘文档", List.of()),
            tpl("财务结算", "PM", 8, "结算单", List.of())
        ));
        // ====================== 数据 ======================
        put(m, "数据", "1", List.of(
            tpl("业务数据源盘点 (库/表/接口/文件)", "DATA", 24, "数据源清单 + 数据量评估", List.of("数据", "源")),
            tpl("数据探查 (空值/重复/分布/异常值)", "DATA", 32, "数据探查报告 + 数据画像", List.of("探查", "画像")),
            tpl("指标体系梳理 (原子/派生/复合指标)", "BA", 32, "指标字典 + DWD/DWS/ADS 分层", List.of("指标", "体系")),
            tpl("数据治理评估 (元数据/质量/安全)", "DATA", 24, "数据治理评估报告", List.of("治理", "质量")),
            tpl("需求评审 + 技术选型 (Hive/Spark/Flink)", "PM", 16, "选型报告", List.of())
        ));
        put(m, "数据", "2", List.of(
            tpl("指标体系设计 (维度/度量/血缘)", "BA", 40, "指标体系文档 + ER 图", List.of("指标", "维度")),
            tpl("数据分层架构设计 (ODS/DWD/DWS/ADS)", "DATA", 24, "分层架构文档", List.of("分层", "架构")),
            tpl("命名规范 + 元数据管理规范", "DATA", 16, "规范文档", List.of()),
            tpl("数据建模 (维度建模/范式建模)", "DATA", 32, "数据模型文档", List.of("建模", "维度模型"))
        ));
        put(m, "数据", "3", List.of(
            tpl("ETL 开发 (数据抽取/清洗/转换/加载)", "DATA", 80, "ETL 脚本 + 调��任务", List.of("etl", "抽取", "清洗")),
            tpl("数据质量校验规则开发", "DATA", 32, "质量规则 + 异常告警", List.of("质量", "校验")),
            tpl("缓慢变化维处理 (SCD Type 2)", "DATA", 24, "SCD 处理脚本", List.of("scd", "缓慢变化")),
            tpl("数据血缘追踪", "DATA", 24, "血缘图谱", List.of("血缘", "追踪"))
        ));
        put(m, "数据", "4", List.of(
            tpl("BI 报表开发 (Tableau/帆软/PowerBI)", "DATA", 56, "报表模板 + 仪表盘", List.of("报表", "bi")),
            tpl("自助分析平台搭建", "SR", 32, "自助 BI 平台", List.of("自助", "分析")),
            tpl("数据 API 开发 (指标查询服务)", "SR", 24, "数据 API + 文档", List.of("api", "查询")),
            tpl("大屏可视化开发", "FRONTEND", 32, "可视化大屏", List.of("大屏", "可视化"))
        ));
        put(m, "数据", "5", List.of(
            tpl("SQL 性能调优 (慢查询/索引/分区)", "DATA", 32, "调优报告 + 执行计划", List.of("性能", "调优", "sql")),
            tpl("数据倾斜处理", "DATA", 24, "数据倾斜解决方案", List.of("倾斜", "性能")),
            tpl("存储成本优化 (冷热分层/压缩)", "DATA", 24, "存储优化报告", List.of("存储", "压缩")),
            tpl("作业调度优化 (Airflow/DolphinScheduler)", "SR", 24, "调度优化报告", List.of("调度", "airflow"))
        ));
        put(m, "数据", "6", List.of(
            tpl("数据交付 + 用户培训", "PM", 16, "培训手册 + 验收报告", List.of("培训")),
            tpl("项目验收 + 交付物核对", "PM", 8, "验收会议纪要", List.of()),
            tpl("运维移交 (数据团队)", "PM", 8, "移交清单", List.of()),
            tpl("财务结算", "PM", 8, "结算单", List.of())
        ));
        // ====================== 云原生 ======================
        put(m, "云原生", "1", List.of(
            tpl("架构设计 (微服务/服务网格/事件驱动)", "AR", 40, "架构设计文档 + C4 图", List.of("架构", "微服务")),
            tpl("技术选型 (K8s/Istio/Kafka/Consul)", "AR", 24, "技术选型报告", List.of("选型", "技术")),
            tpl("容量评估 + 性能基线", "SR", 24, "容量评估报告", List.of("容量", "性能")),
            tpl("安全架构设计 (零信任/SASE)", "SR", 24, "安全架构文档", List.of("安全", "零信任"))
        ));
        put(m, "云原生", "2", List.of(
            tpl("K8s 集群搭建 (多 AZ/高可用)", "SR", 40, "K8s 集群 + 灾备", List.of("k8s", "集群", "高可用")),
            tpl("CI/CD 流水线搭建 (GitLab CI/ArgoCD)", "SR", 32, "CI/CD 流水线", List.of("ci", "cd", "流水线")),
            tpl("镜像仓库搭建 (Harbor/Quay)", "SR", 16, "镜像仓库 + 签名策略", List.of("镜像", "仓库")),
            tpl("监控告警搭建 (Prometheus/Grafana)", "SR", 24, "监控告警平台", List.of("监控", "告警")),
            tpl("日志平台搭建 (ELK/Loki)", "SR", 24, "日志平台", List.of("日志", "elk"))
        ));
        put(m, "云原生", "3", List.of(
            tpl("应用容器化改造 (Docker/多阶段构建)", "SR", 56, "Dockerfile + 镜像 + 漏洞扫描", List.of("docker", "容器化", "镜像")),
            tpl("Helm Chart 编写 + 模板化", "SR", 32, "Helm Chart + 版本管理", List.of("helm", "chart")),
            tpl("配置中心 (Nacos/Apollo)", "SR", 24, "配置中心 + 灰度规则", List.of("配置", "nacos")),
            tpl("服务网格 (Istio/Linkerd) 接入", "SR", 32, "服务网格配置 + 流量治理", List.of("istio", "服务网格"))
        ));
        put(m, "云原生", "4", List.of(
            tpl("可观测性落地 (Metrics/Logs/Traces)", "SR", 40, "可观测性平台 + 告警规则", List.of("可观测", "tracing", "监控")),
            tpl("日志聚合 + 结构化 (ELK/Loki)", "SR", 32, "日志聚合 + 全文检索", List.of("日志", "elk", "loki")),
            tpl("链路追踪 (SkyWalking/Jaeger)", "SR", 24, "链路追踪系统", List.of("链路", "tracing")),
            tpl("告警规则 + OnCall 值班", "SR", 16, "告警规则 + 值班表", List.of("告警", "oncall", "oncall"))
        ));
        put(m, "云原生", "5", List.of(
            tpl("安全加固 (镜像扫描/RBAC/NetworkPolicy)", "SR", 32, "安全加固报告", List.of("安全", "rbac", "扫描")),
            tpl("灾备演练 + 业务连续性", "SR", 24, "灾备演练报告", List.of("灾备", "演练")),
            tpl("混沌工程演练 (Chaos Monkey/Litmus)", "SR", 16, "混沌工程报告", List.of("混沌", "chaos")),
            tpl("性能压测 (k6/wrk/Locust)", "QA", 24, "性能压测报告", List.of("性能", "压测"))
        ));
        put(m, "云原生", "6", List.of(
            tpl("生产环境发布 + 灰度 5% -> 100%", "SR", 16, "发布报告", List.of("发布", "灰度")),
            tpl("运维移交 (SRE 团队)", "PM", 8, "移交清单", List.of()),
            tpl("项目验收 + 交付物核对", "PM", 8, "验收会议纪要", List.of()),
            tpl("财务结算", "PM", 8, "结算单", List.of())
        ));
        return m;
    }

    private static void put(Map<String, List<Map<String, Object>>> m, String industry, String mCode, List<Map<String, Object>> tpls) {
        m.put(industry + ":" + mCode, tpls);
    }

    private static Map<String, Object> tpl(String name, String role, int hours, String deliv, List<String> requiredKws) {
        Map<String, Object> t = new HashMap<>();
        t.put("name", name);
        t.put("role", role);
        t.put("hours", hours);
        t.put("deliv", deliv);
        t.put("requiredKws", requiredKws == null ? List.of() : requiredKws);
        return t;
    }
}
