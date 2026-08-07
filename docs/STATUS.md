---
status: active
created: 2026-08-07
updated: 2026-08-07
summary: 全局项目计划执行情况(里程碑进度、积压、风险、当前快照)
---

# 项目状态(STATUS)

> 全局项目计划执行情况的**单一事实来源**。本文档回答"项目现在到哪了 / 在做什么 / 卡在哪"——
> 不回答"任务怎么分解"(那是 [WBS.md](WBS.md))、不回答"接口契约长啥样"(那是 [specs/](specs/))、不回答"版本间变更史"(那是 [CHANGELOG.md](CHANGELOG.md))。
>
> **更新节奏**:每次里程碑/门禁评审后更新一次(sift 同款节奏)。代码侧任务状态变化**不**写这里;
> WBS.md 也只登记任务结构、不带 `status` 字段。详细规则见 [README.md](README.md) 与 ADR [003](decisions/003-docs-status-wbs-split.md)。

---

## 1. 当前快照(One-liner)

project-govern 当前处于 **v4.0.0 基线盘点 + 启动 v5 (AI·移动·治理) 立项**阶段。代码侧 19 个后端模块 / 35 个 Controller / 78 JUnit 测试 / 33 OpenAPI paths 已就绪;M1-M3 老基线已通过,M4-M6 启动中。下一次门禁 = **M4 阶段门禁**(预期 2026-09-04)。

---

## 2. 里程碑进度

> 字段:`里程碑 / 主题 / 当前状态 / 完成度 / 验收节点 / 关键风险`
> 详细工作包清单见 [WBS.md](WBS.md)。

| 里程碑 | 主题 | 状态 | 完成度 | 验收节点 | 关键风险 |
|---|---|---|---|---|---|
| **M1** | 立项与项目主数据(老 V2.x) | ✅ done | 100% | 已通过 2026-06-13 v4.0.0 release | — |
| **M2** | 工时 / 甘特 / 通知中心(老 P1.5+P2) | ✅ done | 100% | 已通过 2026-06-07 P1.5 收尾 | — |
| **M3** | 风险 / WBS / EVM(老 P3) | ✅ done | 100% | 已通过 2026-06-13 v4.0.0 release | — |
| **M4** | 成本引擎 / 财务 3-way match(老 V4.0/V4.2) | 🟡 active | 85% | M4 门禁:财务闭环 + 角色档上线 | 财务-成本对账口径未定 |
| **M5** | 预警数据层 + 控制器(老 V4.3→V4.4) | 🟡 active | 60% | M5 门禁:6 条规则触发可见 | 预警控制器收尾中 |
| **M6** | IM 通知多通道(企业微信/钉钉/飞书)(老 P2-A) | 🟡 active | 70% | M6 门禁:3 通道灰度 + 失败隔离 | IM 平台回调接入待评估 |
| **M7** | v5 立项:AI·移动·治理(老 P3plus-v2) | ⏸ draft | 0% | 立项评审通过 | 范围未冻结 |

> 状态图例:✅ done / 🟡 active / ⏸ draft / ⏸ paused / ❌ abandoned / 🔁 superseded

---

## 3. 当前在制品(WIP)

| 工作包 | 里程碑 | 负责人 | 起点 | 计划交付 | 阻塞 |
|---|---|---|---|---|---|
| WP-M4-03 财务-成本对账 | M4 | 待分配 | 2026-08-07 | 2026-08-21 | 对账口径待定 |
| WP-M5-02 预警控制器 | M5 | 待分配 | 2026-08-07 | 2026-08-28 | 无 |
| WP-M6-03 IM 回调接入评估 | M6 | 待分配 | 2026-08-07 | 2026-08-21 | IM 平台 OAuth 待评估 |

---

## 4. 风险登记(当前)

| ID | 风险 | 影响 | 缓解 | 负责人 |
|---|---|---|---|---|
| R-001 | 财务-成本对账口径未对齐 | M4 门禁延期 | 8/14 前开一次口径评审会 | PMO |
| R-002 | IM 平台 OAuth 接入工作量未评估 | M6 范围漂移 | 先做技术 spike,再决定是否进 M6 | 架构组 |
| R-003 | 文档老基线未结构化(本次重构首次落地) | 代理上下文加载混乱 | 本轮 STATUS/WBS/AGENTS 整改落地 | 代理(本次会话) |

---

## 5. 关键决策(快照)

> **只列当前生效决策的指针**;决策原文在 [decisions/](decisions/),按编号排序。

| 决策 | 编号 | 状态 |
|---|---|---|
| 采用 Spring Boot 3.3 + Vue 3.5 作为 v4+ 基线 | [001](decisions/001-spring-boot-vue-baseline.md) | ✅ accepted |
| 数据库分 MySQL(生产)+ PostgreSQL(CI)双轨 | [002](decisions/002-mysql-pg-dual-track.md) | ✅ accepted |
| 文档采用 sift 风格 STATUS + WBS 双轨 | [003](decisions/003-docs-status-wbs-split.md) | ✅ accepted |

> 决策编号从 001 开始,只追加不修改;被推翻时旧条目标 `superseded` 并指向新条目。

---

## 6. 下一步门禁 / 评审

| 时间 | 节点 | 准入材料 |
|---|---|---|
| 2026-08-21 | M6 中期评审 | IM 通道灰度方案 + 失败隔离证据 |
| 2026-08-28 | M5 阶段门禁 | 6 条预警规则触发截图 + 控制器覆盖率 |
| 2026-09-04 | M4 阶段门��� | 财务-成本对账报告 + 角色档上线证明 |

---

## 7. 已盘点基线数据(承接 v4.0.0)

> 仅在 STATUS.md 重启时盘点一次,后续版本变化追加到 [CHANGELOG.md](CHANGELOG.md)。

- 后端模块:19 个(admin/alert/cost/dashboard/dict/dingtalk/finance/healthadvisor/initiation/member/milestone/notification/org/project/risk/timesheet/wbs/workflow/workload + common + tools)
- Controller:35 个 / Service+Repository:80+ / Entity:50+
- 测试:78 JUnit(老仓库口径;本次重启后跑通需重计)
- OpenAPI:33 paths / 37 schemas / 10 tags
- 前端:18 视图 / 17 API 客户端 / 13+ 组件
- Flyway:PostgreSQL ~40 个版本 / MySQL ~36 个版本(以 V4.13 为最高)
- 数据库表:A1 数据字典覆盖 79 张表(详见 `drafts/扩展文档/A1-数据字典/`)

---

## 8. 更新日志(本文件)

- **2026-08-07**:首版。基于老仓库 v4.0.0 重启,基线盘点 + M4-M6 进度初始化。落地 STATUS/WBS 双轨决策(003)。
