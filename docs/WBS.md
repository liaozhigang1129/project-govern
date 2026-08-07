---
status: active
created: 2026-08-07
updated: 2026-08-07
summary: 工作分解结构(里程碑 → 工作包 → 任务),仅登记任务结构,不写进度
---

# 工作分解结构(WBS)

> 本文档**只登记任务分解**:里程碑(M) → 工作包(WP) → 任务(T)。
> **不写**进度/状态/风险——这些一律在 [STATUS.md](STATUS.md)。
> **不写**实现步骤——那是 [plans/](plans/)。
> **不写**接口契约——那是 [specs/](specs/)。
>
> 工作包字段:`WP-ID / 名称 / 所属里程碑 / 前置依赖 / 验收标准 / Spec 指针 / Plan 指针`。
> 状态字段**不**写在工作包上;只在工作分解层级出现"已盘点/未启动"等结构性标记。

---

## 命名约定

- **M**:里程碑(Milestone),大写字母 `M<n>`,如 `M1`、`M6`。
- **WP**:工作包(Work Package),`WP-M<n>-<nn>`,如 `WP-M4-03`。
- **T**:任务(Task),`T-WP-M<n>-<nn>-<kk>`,如 `T-WP-M4-03-01`。工作包内部顺序编号。
- 工作包**可独立验收、可独立分配**;任务是工作包内部的执行步骤,不必单独验收。
- 工作包与 specs/、plans/ 的对应关系通过指针标注,不复制内容。

---

## M1 — 立项与项目主数据(老 V2.x 基线) ✅

> 老基线 v2.x 已交付,代码与 schema 已落盘,本次重启沿用。

### WP-M1-01 项目主数据 CRUD

- **前置依赖**:无
- **验收标准**:项目创建/查询/更新/软删可用,编码唯一性约束生效
- **Spec**:见 `specs/legacy/pmo-pms-proposal.md` §3.1、`specs/legacy/pmo-pms-mvp-design.md` §3
- **Plan**:—

### WP-M1-02 立项三级审批状态机

- **前置依赖**:WP-M1-01
- **验收标准**:DEPT_LEAD → PMO → EXEC 三级流转,驳回可回到 PENDING DEPT_LEAD
- **Spec**:见 [README.md](../README.md) §4.1 + `specs/legacy/pmo-pms-mvp-design.md` §3.2
- **Plan**:—

### WP-M1-03 里程碑 + 加权进度

- **前置依赖**:WP-M1-01
- **验收标准**:七阶段字典落地,加权进度公式 Σ(权重 × 完成度) / Σ权重
- **Spec**:见 [README.md](../README.md) §4.2
- **Plan**:—

### WP-M1-04 Dashboard KPI

- **前置依赖**:WP-M1-01、WP-M1-03
- **验收标准**:KPI / 状态分布 / 健康度分布 / 在建项目 4 个端点返回真实 SQL 聚合
- **Spec**:见 `specs/openapi/openapi.json` Dashboard tag
- **Plan**:—

---

## M2 — 工时 / 甘特 / 通知中心(老 P1.5 + P2) ✅

> P1.5 收尾 2026-06-07,P2 通知中心 2026-06-07,均已交付。

### WP-M2-01 工时审批流

- **前置依赖**:WP-M1-01
- **验收标准**:工时提交 → 一级审批 → 入成本池 全链路,审批拒绝可重提
- **Spec**:见 `drafts/扩展文档/P1.5-收尾/P1.5-收尾一页纸.md` §3.1
- **Plan**:—

### WP-M2-02 人员负载矩阵

- **前置依赖**:WP-M2-01
- **验收标准**:`v_active_user` / `v_user_weekly_load` 视图存在,`GET /api/workload/users` 兜底加固
- **Spec**:见 `reviews/2026-06-09-p2-b-workload-views-fix-pmo-hex.md`
- **Plan**:—

### WP-M2-03 通知中心(邮件通道)

- **前置依赖**:无
- **验收标准**:通知持久化、铃铛/分页/已读,3 事件(提交/决定/补料)→ MailService
- **Spec**:见 `drafts/扩展文档/P1.5-收尾/P1.5-收尾一页纸.md` §3.1
- **Plan**:—

### WP-M2-04 Gantt API + 坐标轴锚定

- **前置依赖**:WP-M1-03
- **验收标准**:`GET /api/gantt` 自动范围含里程碑时间窗,单 PM 视图不挤边缘
- **Spec**:见 `reviews/2026-06-09-p2-c-gantt-axis-fix-pmo-hex.md`
- **Plan**:—

---

## M3 — 风险 / WBS / EVM(老 P3) ✅

> P3 WBS / EVM / Risk 已交付,见 `drafts/扩展文档/P3-WBS-EVM-Risk-PRD/`。

### WP-M3-01 WBS 任务拆解

- **前置依赖**:WP-M1-01
- **验收标准**:WBS 树形结构 + 任务级甘特 + 资源分配矩阵
- **Spec**:见 `drafts/扩展文档/P3-WBS-EVM-Risk-PRD/PR-3-核心功能-任务与资源.md`
- **Plan**:—

### WP-M3-02 EVM 指标

- **前置依赖**:WP-M3-01
- **验收标准**:BAC / PV / EV / AC / CPI / SPI 6 指标可查询,趋势视图可见
- **Spec**:见 `drafts/扩展文档/P3-WBS-EVM-Risk-PRD/PR-4-风险与可视化.md`
- **Plan**:—

### WP-M3-03 风险矩阵 + 历史快照

- **前置依赖**:WP-M1-01
- **验收标准**:风险矩阵视图 + 历史快照(V2.6/V2.7),含 4 项目 seed
- **Spec**:见 `drafts/扩展文档/P3-WBS-EVM-Risk-PRD/PR-2-数据模型.md` 风险域
- **Plan**:—

---

## M4 — 成本引擎 / 财务 3-way match(老 V4.0 / V4.2)

> 当前 **🟡 active, 85%**;M4 门禁 = 财务闭环 + 角色档上线。

### WP-M4-01 角色档(Role Rate)

- **前置依赖**:WP-M2-01
- **验收标准**:RoleRate 表 + 管理端点,部门/角色双维度
- **Spec**:见 `specs/legacy/pmo-pms-cost-engine.md` §2
- **Plan**:—

### WP-M4-02 成本引擎核心

- **前置依赖**:WP-M2-01、WP-M4-01
- **验收标准**:Timesheet × RoleRate → CostItem 实时计算,月度快照持久化
- **Spec**:见 `specs/legacy/pmo-pms-cost-engine.md` §3
- **Plan**:—

### WP-M4-03 财务-成本对账

- **前置依赖**:WP-M4-02
- **验收标准**:合同 ↔ 发票 ↔ 付款 ↔ 成本 3-way match 闭环,差异告警
- **Spec**:见 `specs/legacy/pmo-pms-cost-engine.md` §5
- **Plan**:— **【当前阻塞:对账口径未对齐,R-001】**

---

## M5 — 预警数据层 + 控制器(老 V4.3 → V4.4)

> 当前 **🟡 active, 60%**;数据层就绪,控制器收尾中。

### WP-M5-01 预警实体 + 6 种子规则

- **前置依赖**:WP-M3-02、WP-M4-02
- **验收标准**:6 类规则(成本/进度/质量/风险/资源/合规)数据可写入
- **Spec**:见 `specs/legacy/PRD-cost-control.md` §3
- **Plan**:—

### WP-M5-02 预警控制器 + 触发器

- **前置依赖**:WP-M5-01
- **验收标准**:`AlertController` 端点齐全,触发器按规则类型可观测
- **Spec**:见 `specs/legacy/PRD-cost-control.md` §4
- **Plan**:—

---

## M6 — IM 通知多通道(企业微信 / 钉钉 / 飞书,老 P2-A)

> 当前 **🟡 active, 70%**;M6 门禁 = 3 通道灰度 + 失败隔离。

### WP-M6-01 用户-IM 绑定 + 总开关

- **前置依赖**:WP-M2-03
- **验收标准**:`user_im_binding` / `user_im_quiet_hours` 表 + 总开关
- **Spec**:见 `drafts/扩展文档/P2-A-IM通知/P2-A-IM通知.md` §3
- **Plan**:—

### WP-M6-02 NotificationDispatcher 路由 + 扇出

- **前置依赖**:WP-M6-01
- **验收标准**:通道粒度独立开关,失败隔离(任一通道异常不影响其他)
- **Spec**:见 `drafts/扩展文档/P2-A-IM通知/P2-A-IM通知.md` §3
- **Plan**:—

### WP-M6-03 IM 平台回调接入评估

- **前置依赖**:WP-M6-02
- **验收标准**:spike 报告:OAuth 工作量、卡片回调可行性、是否进 M6 范围
- **Spec**:—
- **Plan**:— **【当前阻塞:评估未启动,R-002】**

---

## M7 — v5 立项:AI · 移动 · 治理(老 P3plus-v2)

> 当前 **⏸ draft, 0%**;立项评审中。

### WP-M7-01 v5 立项评审

- **前置依赖**:M4/M5/M6 阶段门禁通过
- **验收标准**:v5 范围冻结、AI / 移动 / 治理三轴确认
- **Spec**:见 `drafts/扩展文档/P3plus-v2-立项/P3plus-PR-0-项目索引与总览.md`
- **Plan**:—

### WP-M7-02 v5 数据模型增量

- **前置依赖**:WP-M7-01
- **验收标准**:8 新表 + 6 表扩展,5 状态机,12 索引
- **Spec**:见 `drafts/扩展文档/P3plus-v2-立项/P3plus-PR-2-数据模型.md`
- **Plan**:—

### WP-M7-03 v5 核心功能(AI 预测 / 智能推荐 / 异常 / 多租户 / 移动)

- **前置依赖**:WP-M7-02
- **验收标准**:5 模块功能详述落地
- **Spec**:见 `drafts/扩展文档/P3plus-v2-立项/P3plus-PR-3-核心功能.md`
- **Plan**:—

### WP-M7-04 v5 可视化与 AI 看板

- **前置依赖**:WP-M7-03
- **验收标准**:6 智能视图 + 3 预测模型 + 智能报告
- **Spec**:见 `drafts/扩展文档/P3plus-v2-立项/P3plus-PR-4-可视化与AI.md`
- **Plan**:—

---

## 跨里程碑基础工作包

### WP-INFRA-01 测试 schema 分离

- **前置依赖**:—
- **验收标准**:H2 (PG mode) 测试库 + MySQL dev/prod + CI 双轨
- **Spec**:见 `specs/openapi/openapi.json` + `scripts/dev-up.sh`
- **Plan**:—

### WP-INFRA-02 CI 4 jobs

- **前置依赖**:WP-INFRA-01
- **验收标准**:backend-test / frontend-build / api-smoke / docker-build 4 jobs 全绿
- **Spec**:见 `.github/workflows/ci.yml`
- **Plan**:—

### WP-INFRA-03 文档规范(SIFT 风格 STATUS/WBS 双轨)

- **前置依赖**:—
- **验收标准**:AGENTS.md / docs/README.md / STATUS.md / WBS.md / CHANGELOG.md 五件套就位
- **Spec**:见 [README.md](README.md)
- **Plan**:— **【本次会话交付】**

---

## 附录:与文档其他位置的对应关系

- **当前进度 / 风险 / 决策快照** → [STATUS.md](STATUS.md)
- **里程碑背景与历史** → `drafts/CHANGELOG-legacy-v1-v4.md` 与 [CHANGELOG.md](CHANGELOG.md)
- **实现计划** → [plans/](plans/)(空目录,按需新增)
- **架构决策原文** → [decisions/](decisions/)(按编号)
- **接口契约** → [specs/](specs/)
- **测试方案** → [testing/](testing/)
- **评审存档** → [reviews/](reviews/)
