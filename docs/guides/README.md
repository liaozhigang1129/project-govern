---
status: draft
created: 2026-08-07
updated: 2026-08-07
summary: 用户手册目录占位(待 M6/M7 阶段补 PMO/PM/财务/执行 四角色实操指南)
---

# 用户手册(Guides)

> **状态:draft 占位**
> 本目录用于收录面向**最终用户**的实操手册,区别于:
> - `analysis/` — 调研性 spike 报告
> - `decisions/` — 架构决策(ADR)
> - `specs/` — 接口契约 / 数据模型
> - `plans/` — 工作包实现步骤
> - `dev/`(待建)— 面向**开发者**的部署 / 调试 / 扩展指南

## 计划收录(占位)

| 指南 | 角色 | 优先级 | 状态 |
|---|---|---|---|
| 立项流程操作手册 | PM | P1 | 待补 |
| 工时录入 + 提交审批 | 全员 | P1 | 待补 |
| 财务对账月结操作 | 财务 / PMO | P1 | 待补 |
| 预警查看 + ack / resolve | PMO / ADMIN | P2 | 待补 |
| 项目立项评审操作 | DEPT_LEAD / PMO / EXEC | P1 | 待补 |
| 成本看板 + 月度核算 | PMO / 财务 | P1 | 待补 |
| Gantt 图编辑与基线对比 | PM | P2 | 待补 |
| 风险登记 + 升级路径 | PM / PMO | P2 | 待补 |

> 上表为占位,实际手册内容在 v4.0.0 release 之后开始补。
> 优先级 P1 = 必交付(M4/M5 门禁前),P2 = 增值(M6/M7 阶段)。

## 命名规范

- `<topic>-<role-slug>.md`
- 例:`initiation-pm.md`、`timesheet-entry-user.md`、`cost-reconciliation-finance.md`
- 一份手册只覆盖**一个角色 + 一个主题**(避免巨型文件)

## front-matter 字段

| 字段 | 必填 | 取值 |
|---|---|---|
| `status` | 是 | `draft` / `active` / `superseded` |
| `created` | 是 | `YYYY-MM-DD` |
| `updated` | 是 | 同上 |
| `summary` | 是 | 30 字内一句话 |
| `audience` | 建议 | 目标角色,例如:`[PMO, FINANCE]` |

## 何时写到 guides(而非其他目录)

- ✅ **写到 guides**:面向用户操作步骤、配图、按钮点击顺序
- ❌ **不写到 guides**:
  - 后端 API 文档 → OpenAPI / Swagger UI(自动生成)
  - 架构设计 → `DESIGN.md` + `decisions/`
  - 部署/调试 → `dev/`

## 相关文档

- 老仓库参考:`项目经营台账/PMO-PMS使用手册.md`(归档在 `drafts/`,本次重启可作为 v0.1 模板)
- v0.1 seed:本目录的首批手册可基于老仓库内容精简后落地(2026-Q3 计划)
