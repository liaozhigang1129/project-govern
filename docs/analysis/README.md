---
status: active
created: 2026-08-07
updated: 2026-08-07
summary: 调研 / spike / 决策分析报告索引(IM 平台对比 / 容量评估 / 技术选型等)
---

# 分析报告(Analysis)

> 本目录收录**调研性 / spike 性**的技术分析报告。区别于:
> - `decisions/` — 已落地的架构决策(编号 ADR,只追加)
> - `specs/` — 接口契约 / 数据模型 / 业务规则
> - `plans/` — 具体工作包的实现步骤

## 当前收录(2026-08-07)

| 报告 | 主题 | 关联工作包 | 决策落地 |
|---|---|---|---|
| [2026-08-07-im-oauth-flow-comparison.md](2026-08-07-im-oauth-flow-comparison.md) | 三平台(企业微信/钉钉/飞书)OAuth 2.0 + 卡片回调可行性对比 | WP-M6-03 | [ADR 004](../decisions/004-im-callback-deferred.md) |
| [2026-08-07-im-oauth-decision-proposal.md](2026-08-07-im-oauth-decision-proposal.md) | IM OAuth 接入决策建议(选 B 推迟 v5)+ 工作量估算 + Mermaid 架构 | WP-M6-03 | [ADR 004](../decisions/004-im-callback-deferred.md) |

## 命名规范

- `<YYYY-MM-DD>-<topic-slug>.md`
- 例:`2026-08-07-im-oauth-flow-comparison.md`、`2026-08-15-cost-engine-benchmark.md`
- 不用版本号 / 不用 `v1` 后缀;演进通过新文件 + 旧文件改 `status: superseded`

## 文件 front-matter 字段

| 字段 | 必填 | 取值 |
|---|---|---|
| `status` | 是 | `draft` / `active` / `final` / `superseded` / `abandoned` |
| `created` | 是 | `YYYY-MM-DD` |
| `updated` | 是 | `YYYY-MM-DD` 或 `YYYY-MM-DDTHH:MM` |
| `summary` | 是 | 30 字内一句话说明 |

## 何时写到 analysis(而非 specs / decisions)

- ✅ **写到 analysis**:spike 报告 / 调研 / 选型对比 / 容量评估 / 性能 benchmark
- ❌ **不写到 analysis**:
  - 已确定的架构决策 → `decisions/NNN-xxx.md`(走 ADR 流程)
  - 接口契约 / 数据模型 → `specs/`
  - 具体实现步骤 → `plans/`
  - 评审记录 → `reviews/`

## 关联到 ADR / Plan

每份分析报告应该在顶部"文档目标"段说明:
- 关联的 WBS 工作包(若有)
- 是否产出 ADR(若有 → 在 STATUS 决策表追加)
- 是否产出 Plan(若有 → 在 WBS 对应工作包 Plan 字段填文件名)

## 历史归档

`commit-splits/` 子目录保留老仓库 v2.x 时期的 commit 拆包归档,**非正式文档**,仅供回溯。

## 维护规则

- 新增:在下方表格追加一行
- 更新:`updated` 字段更新,顶部表格不动(行内容可补充)
- 推翻:旧文件改 `status: superseded` + 顶部加指向新文件链接,**不删除**
