---
status: active
created: 2026-08-07
updated: 2026-08-07
summary: 架构决策记录(ADR)目录索引 — 编号机制 + 当前生效清单 + 模板引用
---

# 架构决策记录(Decisions / ADR)

> **目录用途**:记录对项目架构 / 技术栈 / 流程产生**长期影响**的决策,区别于:
> - `analysis/` — spike / 调研性报告
> - `specs/` — 接口契约 / 数据模型
> - `plans/` — 工作包实现步骤

## 当前生效决策(2026-08-07 共 4 份)

| 编号 | 标题 | 状态 | 影响工作包 |
|---|---|---|---|
| [001](001-spring-boot-vue-baseline.md) | v4+ 基线采用 Spring Boot 3.3 + Java 21 + Vue 3.5 | ✅ accepted | M1-M6 全栈 / WP-INFRA-02 CI |
| [002](002-mysql-pg-dual-track.md) | 数据库分 MySQL(生产)+ PostgreSQL(CI)双轨 | ✅ accepted | M1-M5 数据持久化 / WP-INFRA-01 测试 schema |
| [003](003-docs-status-wbs-split.md) | 文档采用 sift 风格 STATUS + WBS 双轨 | ✅ accepted | WP-INFRA-03 文档规范 |
| [004](004-im-callback-deferred.md) | IM 平台 OAuth + 卡片回调推迟到 v5 立项评估 | ✅ accepted | WP-M6-03 IM 回调 |

> 编号只追加不修改;被推翻时旧条目标 `superseded` 并指向新条目。
> 编号顺序由 `scripts/docs-lint.sh` 自动校验(连续或允许间断)。

## 编号机制

1. **格式**:`NNN-<topic-slug>.md`,3 位数字 + 连字符 + 小写连字符 slug
2. **起点**:001
3. **追加**:每次新决策递增,**不重用**已废弃编号
4. **删除**:**永远不删**;废弃时改 `status: superseded` + 顶部加指向新条目链接
5. **顺序**:按时间排列(可间断,但 docs-lint 会 warn)

## 何时需要写 ADR

- ✅ **必须写 ADR**:
  - 选/换技术栈(框架、数据库、构建工具)
  - 影响模块边界 / 数据流方向的架构决策
  - 推翻之前 ADR 的反向决策
  - 影响流程 / 工作方式(例如文档治理、CI 流程)
- ❌ **不需要写 ADR**:
  - 业务功能增删(走 WBS 工作包 + Plan)
  - Bug 修复(走 commits)
  - spike 报告本身(走 `analysis/`,但 spike 落地时常产出 ADR)

## 写作流程

1. **copy 模板**:`cp _template.md docs/decisions/NNN-<slug>.md`
2. **填字段**:背景 / 决定 / 理由(3 个 option 对比) / 后果(正负两面) / 关联
3. **加 front-matter**:`status: proposed`(待评审)
4. **PMO 评审**:通过后改 `status: accepted`;不通过改 `status: rejected` 或归档
5. **更新 STATUS §5 决策表**:加一行
6. **更新 WBS §"当前生效决策清单"**:在工作包 `**ADR**:` 字段加引用

## 模板

新建 ADR 时复制 [`_template.md`](_template.md),按模板填字段。

## ADR ↔ 其他文档的引用

| 引用方 | 位置 | 格式 |
|---|---|---|
| [STATUS.md §5](../STATUS.md) | 决策快照表 | `[NNN](`decisions/<编号>-<slug>.md`)` |
| [WBS.md §"当前生效决策清单"](../WBS.md) | 每个工作包 `**ADR**:` 字段 | `[NNN](`decisions/<编号>-<slug>.md`)` |
| [README.md](../README.md) | 顶层索引 | 决策章节列前 4 个 |
| 分析报告顶部 | 关联 ADR | `产出 ADR [NNN](`decisions/<编号>-<slug>.md`)` |

## 推翻流程

需要推翻既有 ADR 时:

1. 写新 ADR,编号递增,front-matter 标 `status: supersedes <旧编号>`
2. 旧 ADR front-matter 改 `status: superseded`,正文顶部加 `> 本决策已被 [NNN](NNN-xxx.md) 推翻` 链接
3. STATUS 决策表保留旧行(标 superseded),加新行
4. 提交 PR 评审(决策级变更需 PMO + 架构组双签)

## 维护规则

- 不允许修改历史 ADR 的正文(即使发现笔误);只能加 `> UPDATE:` 注释段落
- 关联文档(WBS / STATUS)引用 ADR 时使用 markdown 相对链接 ``decisions/<编号>-<slug>.md``
- ADR 长度建议 ≤ 200 行;超长决策拆为 1 篇主 ADR + 多篇附属 ADR
