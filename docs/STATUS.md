---
status: active
created: 2026-08-07
updated: 2026-08-07T20:00
last_head: 7cc3721
summary: 全局项目计划执行情况(里程碑进度、积压、风险、当前快照)
---

# 项目状态(STATUS)

> 全局项目计划执行情况的**单一事实来源**。本文档回答"项目现在到哪了 / 在做什么 / 卡在哪"——
> 不回答"任务怎么分解"(那是 [WBS.md](WBS.md))、不回答"接口契约长啥样"(那是 [specs/](specs/))、不回答"版本间变更史"(那是 [CHANGELOG.md](CHANGELOG.md))。
>
> **更新节奏**:每次里程碑/门禁评审后更新一次(sift 同款节奏)。代码侧任务状态变化**不**写这里;
> WBS.md 也只登记任务结构、不带 `status` 字段。详细规则见 [README.md](README.md) 与 ADR [003](decisions/003-docs-status-wbs-split.md)。
>
> **会话封板指针**:`last_head = 7cc3721` (`git log --oneline -20` 查看后续提交)
> 本字段在每个会话收尾时由最后一条 commit 同步更新。代理在新会话开始时,应先
> `git fetch` + `git log --oneline -5 origin/main` 与本字段比对,确认从正确基线继续。

---

## 1. 当前快照(One-liner)

project-govern 当前处于 **v4.0.0 重启 + 文档治理 + CI 治理**阶段。本次会话已完成基线盘点(M1-M3)、重命名(`pmo-pms` → `project-govern`)、文档骨架(sift 风格 + STATUS/WBS 分轨)、主题化 spec 拆分(10 份 + 索引)、3 个 in-flight 工作包(WP-M4-03 财务对账 / WP-M5-02 预警控制器 / WP-M6-03 IM spike 决策推迟)的实现+实施+决策落地、4 份 ADR(001/002/003/004)、docs-lint 脚本 + CI 集成、ESLint 9 + Prettier 3 前端静态检查 + CI 集成,代码层 0 错误构建通过。下一次门禁 = **M4 阶段门禁**(预期 2026-09-04)。

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

> **本会话调整**(2026-08-07):M4-M6 三条均启动中,且为本次会议唯一重点在制品(WP-M4-03 / WP-M5-02 / WP-M6-03);M7 仍为草案。

> 状态图例:✅ done / 🟡 active / ⏸ draft / ⏸ paused / ❌ abandoned / 🔁 superseded

---

## 3. 当前在制品(WIP)

> 本轮会话唯一交付块。3 个工作包计划文件已落地 `plans/2026-08-07-wp-*.md`,起点与计划交付日期同步锁定。详细任务结构见 [WBS.md](WBS.md) 对应章节。

| 工作包 | 里程碑 | 负责人 | 起点 | 计划交付 | 阻塞 |
|---|---|---|---|---|---|
| **WP-M4-03** 财务-成本对账 | M4 | 待分配 | 2026-08-07 | 2026-08-21 | 对账口径待定 |
| **WP-M5-02** 预警控制器 | M5 | 待分配 | 2026-08-07 | 2026-08-28 | 无 |
| **WP-M6-03** IM 回调接入评估 | M6 | 待分配 | 2026-08-07 | 2026-08-21 | IM 平台 OAuth 待评估 |

---

## 4. 风险登记(当前)

| ID | 风险 | 影响 | 缓解 | 负责人 |
|---|---|---|---|---|
| R-001 | 财务-成本对账口径未对齐 | M4 门禁延期 | 8/14 前开一次口径评审会 | PMO |
| R-002 | IM 平台 OAuth 接入工作量未评估 | ~~M6 范围漂移~~ | ✅ 本会话 spike 关闭 → 转为 [ADR 004](decisions/004-im-callback-deferred.md)(推迟 v5) | 架构组 |
| R-003 | 文档老基线未结构化 | 代理上下文加载混乱 | ✅ 本轮 STATUS/WBS/AGENTS 整改完成;主题 spec 拆分完成;docs-lint CI 护栏已就位 | 代理(本次会话) |
| R-004 | 前端 `no-explicit-any` 存量 warning(352 处) | 代码质量债 | CI 现以 `--max-warnings 1000` 上限告警,不挡;后续逐文件清理 | 前端 |
| R-005 | 前端 prettier 与 vue 模板多行 `@event=` 属性表达式冲突 | `pnpm format` 会破坏已折叠好的 handler | CI 中 `format:check` 以 `continue-on-error` 运行(仅告警),本地提供 `pnpm format` 手动调 | 前端 |
| R-018 | v5 立项拖延(本会话决策:卡片回调推迟到 v5) | IM 卡片审批延期 | 季度 review 跟踪,ADR 004 锁定 | PMO |
| R-019 | 用户反馈"M6 无卡片审批能力" | 用户期望管理 | CHANGELOG / Release Notes 明确说明 v5 路线图 | PMO |

---

## 5. 关键决策(快照)

> **只列当前生效决策的指针**;决策原文在 [decisions/](decisions/),按编号排序。

| 决策 | 编号 | 状态 |
|---|---|---|
| 采用 Spring Boot 3.3 + Vue 3.5 作为 v4+ 基线 | [001](decisions/001-spring-boot-vue-baseline.md) | ✅ accepted |
| 数据库分 MySQL(生产)+ PostgreSQL(CI)双轨 | [002](decisions/002-mysql-pg-dual-track.md) | ✅ accepted |
| 文档采用 sift 风格 STATUS + WBS 双轨 | [003](decisions/003-docs-status-wbs-split.md) | ✅ accepted |
| IM 平台 OAuth + 卡片回调推迟到 v5 立项评估 | [004](decisions/004-im-callback-deferred.md) | ✅ accepted |

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
- **构建验证(本会话)**:Java 后端 `mvn clean compile` + `mvn test-compile` BUILD SUCCESS(399+50 源文件);前端 `pnpm build` 成功(打包后 ~987KB gz=317KB);`vue-tsc --noEmit` 0 错误;ESLint 0 错误 349 warning

---

## 8. 更新日志(本文件)

> 封板指针:`last_head` 表示本节最后一条日志时同步的 git commit hash。
> 新会话开始时,先 `git log --oneline origin/main | head` 看看后续提交,
> 然后从本节上一条 `last_head` 之后的工作继续。

- **2026-08-07**(last_head=`7cc3721`):docs/analysis/README + docs/guides/README 占位 + docs/decisions/README + ADR 模板落地;docs-lint 增强(跳过代码块 + 行内 code + 排除 README/模板)。
- **2026-08-07**(last_head=`8523d53`):WP-M4-03 财务对账(8 步)+ WP-M5-02 预警控制器(6 步)+ WP-M6-03 IM spike 决策推迟 全部落地;前端 prettier/vue 多语句 @event 冲突根治(17 处提取 method);WBS 9 工作包加 ADR 引用 + 末尾决策清单。
- **2026-08-07**(last_head=`23072ed`):docs-lint CI + ESLint 9 / Prettier 3 / vue-tsc 前端静态检查 + CI 集成。
- **2026-08-07**(last_head=`85a039f`):首版。基于老仓库 v4.0.0 重启,基线盘点 + M4-M6 进度初始化。落地 STATUS/WBS 双轨决策(003)。
- **2026-08-07(同日)**:项目重命名 `pmo-pms` → `project-govern`(Java 包 `com.hex.projectgovern`、DB `project_govern`、OpenAPI title 同步);创建 3 份 ADR(001/002/003);创建 10 份主题化 spec + 索引;创建 3 份 in-flight 工作包实现计划(WP-M4-03 / WP-M5-02 / WP-M6-03);落地 docs-lint 脚本 + CI 集成(github action job 5);落地 ESLint 9 + Prettier 3 前端静态检查 + CI 集成(job 6);补全风险登记 R-003/R-004/R-005。
- **2026-08-07(下午)**:WP-M4-03 财务-成本 3-way match 全量落地(V5.0/V5.1 双轨 Flyway + ReconciliationService + 事件链路 + 告警 COST_DIFF + 通知分发 + REST API + 前端 + smoke 冒烟);WP-M5-02 预警控制器全量落地(AlertController + 规则引擎抽象 + 6 类规则 + 5min 调度 + 前端 + smoke);WP-M6-03 spike 决策落地(ADR 004 推迟 v5 + R-002 关闭 + 新增 R-018/R-019)。
