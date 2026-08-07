---
status: active
created: 2026-08-07
updated: 2026-08-07
summary: PMO·PMS 产品需求(场景、状态模型、模块清单、成功标准)
---

# 产品需求(PRD)

> 本文档回答"做什么、为什么、做到什么程度",不回答"怎么实现"(那是 [DESIGN.md](DESIGN.md))与"接口契约长啥样"(那是 [specs/](specs/))。
>
> **当前版本基线**:v4.0.0(2026-06-13 release,`c45df49`)。本文为基线 PRD 重写,承接老仓库 `drafts/扩展文档/A1-数据字典/`、`A2-API规范/`、`A3-UI原型/`、`pmo-pms-proposal.md` 与 `PRD-cost-control.md` 的内容沉淀。

---

## 1. 一句话定位

PMO·PMS 是**治理视角的项目全生命周期管理系统**,覆盖:**立项审批 → 项目执行 → 里程碑跟踪 → 健康度监控 → 成本 / 工时 / 风险 / 通知**。服务于 PMO 治理委员会、项目经理(PM)、部门负责人、资源管理员与一线执行人。

---

## 2. 角色与场景

### 2.1 五类核心角色

| 角色 | 职责 | 典型场景 |
|------|------|----------|
| **PMO_ADMIN** | 治理委员会 / 系统管理员 | 配置字典、审批策略灰度、查看全公司 Dashboard |
| **PM**(Project Manager) | 项目立项与执行 | 提交立项、跟踪里程碑、填报工时、查看健康度 |
| **DEPT_LEAD**(部门负责人) | 一级审批 + 资源调度 | 审批立项(DEPT_LEAD 阶段)、分配资源 |
| **EXEC**(执行人 / 团队成员) | 完成任务、填报工时 | 接收任务、填报工时、提交工时审批 |
| **VIEWER** | 只读观察者 | 审计、跨部门了解 |

### 2.2 核心场景(用户故事)

| ID | 作为 | 我想 | 以便 |
|---|---|---|---|
| US-01 | PM | 在系统中提交立项,附带预算、SOW、风险清单 | 走完 3 级审批进入执行期 |
| US-02 | DEPT_LEAD | 在待办里看到立项,一键通过/驳回/补料 | 完成一级审批 |
| US-03 | PMO_ADMIN | 在 PMO 阶段审核立项,核对预算冻结 | 完成二级审批 |
| US-04 | EXEC | 看到自己被分配的 WBS 任务,按周填报工时 | 工时进入成本引擎 |
| US-05 | PM | 在 Gantt 上看所有项目里程碑,自动范围适配 | 一眼看到甘特错位风险 |
| US-06 | PMO_ADMIN | 在 Dashboard 看 KPI / 健康度分布 | 治理视角掌握全公司项目状态 |
| US-07 | EXEC | 收到 IM(钉钉/飞书/企微)通知,有审批待办时秒回 | 不漏审批 |
| US-08 | PM | 在成本页看本月本部门工时→成本换算结果 | 跟预算对齐 |
| US-09 | PMO_ADMIN | 收到预警(进度落后 / 成本超支 / 风险升级) | ��前介入 |
| US-10 | VIEWER | 审计日志看操作轨迹 | 复盘与合规 |

---

## 3. 状态模型

### 3.1 立项三级审批

```
       提交立项
          │
          ▼
   ┌──────────────┐
   │  PENDING     │
   │  DEPT_LEAD   │ ◄──────────────────────┐
   └──────┬───────┘                        │
          │ 通过                            │
          ▼                                 │
   ┌──────────────┐                         │
   │  PENDING     │                         │
   │   PMO        │ ────── 驳回 ────────────┤
   └──────┬───────┘                         │
          │ 通过                            │
          ▼                                 │
   ┌──────────────┐                         │
   │  PENDING     │                         │
   │   EXEC       │ ────── 驳回 ────────────┤
   └──────┬───────┘                         │
          │ 通过                            │
          ▼                                 │
   ┌──────────────┐                         │
   │  APPROVED    │                         │
   └──────────────┘                         │
                                            │
                                  ┌─────────┴────────┐
                                  │ REJECTED (终态)   │
                                  └──────────────────┘
```

### 3.2 里程碑七阶段(V3.1+)

```
INTAKE → ANALYSIS → PROPOSAL → APPROVAL → KICKOFF → EXECUTION → CLOSING
```

每个阶段含 PENDING / IN_PROGRESS / DONE 三个子状态;项目加权进度 = Σ(权重 × 完成度) / Σ权重。

### 3.3 工时审批流

```
提交工时 → DEPT_LEAD 一级审批 → 入成本池
                │
                └── 拒绝 → 重提 / 撤回
```

### 3.4 健康度 5 级字典

`EXCELLENT / GOOD / NORMAL / WARNING / CRITICAL`,由 `health-advisor` 服务根据"进度落后 + 延期天数 + 风险升级"自动建议,可手动 override。

---

## 4. 模块清单

> 字段:`模块 / 所属里程碑 / 状态 / 关键端点数 / 文档指针`。

### 4.1 治理主线(M1-M3,基线已通过)

| 模块 | 里程碑 | 状态 | 端点 | 文档 |
|------|--------|------|------|------|
| **项目主数据** | M1 | done | 5 | `specs/legacy/pmo-pms-mvp-design.md` §3 |
| **立项三级审批** | M1 | done | 5 | `specs/legacy/pmo-pms-proposal.md` §3.1 |
| **里程碑 + 加权进度** | M1 | done | 5 | [README §4.2](../README.md) |
| **Dashboard KPI** | M1 | done | 4 | `specs/openapi/openapi.json` |
| **工时审批** | M2 | done | 5+ | `drafts/扩展文档/P1.5-收尾/P1.5-收尾一页纸.md` |
| **Gantt API** | M2 | done | 1 | `reviews/2026-06-09-p2-c-gantt-axis-fix-pmo-hex.md` |
| **通知中心(邮件)** | M2 | done | 4 | 同上 |
| **风险矩阵 + 快照** | M3 | done | 4 | `drafts/扩展文档/P3-WBS-EVM-Risk-PRD/` |
| **WBS 任务拆解** | M3 | done | — | 同上 |
| **EVM 指标** | M3 | done | — | 同上 |

### 4.2 财务主线(M4-M5,active)

| 模块 | 里程碑 | 状态 | 文档 |
|------|--------|------|------|
| **角色档(Role Rate)** | M4 | active 85% | `specs/legacy/pmo-pms-cost-engine.md` §2 |
| **成本引擎核心** | M4 | active 85% | 同上 §3 |
| **财务-成本对账** | M4 | active 85% | 同上 §5 |
| **预警实体 + 6 种子规则** | M5 | active 60% | `specs/legacy/PRD-cost-control.md` §3 |
| **预警控制器** | M5 | active 60% | 同上 §4 |

### 4.3 集成主线(M6,active)

| 模块 | 里程碑 | 状态 | 文档 |
|------|--------|------|------|
| **用户-IM 绑定** | M6 | active 70% | `drafts/扩展文档/P2-A-IM通知/P2-A-IM通知.md` §3 |
| **NotificationDispatcher** | M6 | active 70% | 同上 |
| **IM 平台回调接入** | M6 | paused 0% | —(评估中) |

### 4.4 v5 立项草案(M7,draft)

| 模块 | 里程碑 | 状态 | 文档 |
|------|--------|------|------|
| **AI 预测 + 智能推荐** | M7 | draft 0% | `drafts/扩展文档/P3plus-v2-立项/P3plus-PR-4-可视化与AI.md` |
| **多租户 / 移动端 / 集成** | M7 | draft 0% | 同上 |

---

## 5. 成功标准(基线 v4.0.0)

> 每条标准都需可观测、可验证、可在 CI 中跑通。

### 5.1 功能成功标准

| 维度 | 标准 | 验证手段 |
|------|------|----------|
| 立项闭环 | DEPT_LEAD/PMO/EXEC 三级流转无遗漏 | `docs/api-testing/postman/smoke.sh` 16 端点 |
| 通知到达 | 4 类事件(立项 / 工时 / 财务 / 预警)通过邮件 + IM 通道到达目标用户 | 业务冒烟 + IM mock |
| 成本计算 | Timesheet × RoleRate → CostItem 准确,与财务 3-way match 一致 | 对账报告 |
| 预警触发 | 6 类规则按触发条件自动告警,通道独立 | `docs/testing/postman/smoke.sh` + 规则 fixture |
| 甘特可视 | 跨项目 Gantt 自动范围锚定里程碑,不挤边缘 | `reviews/2026-06-09-p2-c-gantt-axis-fix-pmo-hex.md` |
| EVM 指标 | BAC/PV/EV/AC/CPI/SPI 6 指标随 WBS 进度实时刷新 | 前端 ECharts 验证 |

### 5.2 非功能成功标准

| 维度 | 标准 | 验证手段 |
|------|------|----------|
| **性能** | 列表查询 P95 < 500ms,Dashboard 聚合 P95 < 1s | JMeter / k6 基准 |
| **可用性** | 4 job CI 全绿,Docker compose 一键起 | `.github/workflows/ci.yml` |
| **可观测** | AuditLog 全留痕,JWT 黑名单 + 操作人/资源/动作/结果/IP | `common/audit` 模块 |
| **兼容性** | MySQL 8.0(生产)+ PostgreSQL 16(CI)双轨无差异 | Flyway 双 schema + 视图健康检查 |
| **安全** | RBAC 全覆盖,@RequireRoles 22 端点,JWT 双 token + HttpOnly cookie | `common.security` 18 case |

### 5.3 工程化成功标准

- **测试**:JUnit 78+ 类 / Postman 29 请求 / Cypress 30 case / business-smoke 7 步全绿
- **覆盖率**:Jacoco 行覆盖 ≥ 70%(待基线重测后确认)
- **文档**:5 顶层 + 3 ADR + 5 reviews + 5 drafts(模块)结构化沉淀(本轮整理后)
- **CI**:backend-test / frontend-build / api-smoke / docker-build 4 job 全绿

---

## 6. 不做(明确出范围)

为防止 scope creep,以下显式列为出范围,做之前需 ADR 批准:

- **IM 卡片"一键审批"按钮**:需 IM 平台 OAuth + 加密回调,MVP 不做
- **移动端原生 App**:v5 立项再评估
- **多租户(数据隔离)**:v5 立项再评估
- **AI 预测模型训练流水线**:v5 立项再评估(预置模型在 `models/`)
- **国际化(i18n)**:暂未规划

---

## 7. 验收方式

每个里程碑在 [STATUS.md §2](STATUS.md) 有独立门禁节点,通过标准为:

1. WBS.md 中该里程碑所有 WP 验收标准全部满足;
2. CI 4 jobs 全绿;
3. 评审存档写入 `docs/reviews/`(若涉及方向性变更,先写 ADR);
4. STATUS.md 更新该里程碑状态为 ✅ done,并把变更摘要追加到 CHANGELOG.md。

---

## 8. 变更历史

| 日期 | 版本 | 摘要 |
|------|------|------|
| 2026-08-07 | v5.0-draft | 基线 PRD 重写,承接 v4.0.0 release,落地 STATUS/WBS 双轨 |
| 2026-06-13 | v4.0.0 | 老仓库 release,详见 `drafts/RELEASE-NOTES-legacy-v4.0.0.md` |
| 2026-06-07 | P1.5 | 通知中心 + Gantt API,详见 `drafts/扩展文档/P1.5-收尾/P1.5-收尾一页纸.md` |
