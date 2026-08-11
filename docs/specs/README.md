---
status: active
created: 2026-08-07
updated: 2026-08-07
summary: specs 索引(架构/数据/API/算法/安全/前端/测试/部署 + 成本引擎 + 成本控制 PRD)
---

# specs/ — 模块规格索引

> 本目录按主题拆分自 [`legacy/pmo-pms-mvp-design.md`](legacy/pmo-pms-mvp-design.md) 与 [`legacy/pmo-pms-cost-engine.md`](legacy/pmo-pms-cost-engine.md)。
> 拆分原则:**引用不复制**(sift 规范);同一份事实只写一次,其余地方链接。
> legacy/ 下完整原文保留,作为对照源(不删不改)。

---

## 目录

| Spec | 内容 | 来源 |
|---|---|---|
| [architecture.md](architecture.md) | 系统架构总览(部署拓扑 + 模块分层 + 关键约定) | mvp-design §1 §2 §3 |
| [data-model.md](data-model.md) | 数据模型(ER + Flyway + 软删除 + 审计) | mvp-design §4 |
| [api-contract.md](api-contract.md) | API 契约(响应 + OpenAPI + DTO + 错误码) | mvp-design §5 |
| [algorithms.md](algorithms.md) | 关键算法(3 级审批 + 加权进度 + 4 级费率 + KPI) | mvp-design §6 + cost-engine §3 |
| [security.md](security.md) | 安全设计(JWT + RBAC + CORS + 密码 + 审计) | mvp-design §7 |
| [frontend.md](frontend.md) | 前端架构(目录 + 路由 + Pinia + Axios + ECharts) | mvp-design §8 |
| [testing.md](testing.md) | 测试策略(3 层金字塔 + 跑测命令) | mvp-design §9 |
| [deployment.md](deployment.md) | CI/CD 与部署(5 jobs + Docker + 环境变量) | mvp-design §10 |
| [cost-engine.md](cost-engine.md) | 成本引擎(数据 + 4 级费率 + 12 端点 + CSV) | cost-engine(单一主题) |
| [cost-control-prd.md](cost-control-prd.md) | 成本控制 PRD(F1-F5 + 验收 + 风险) | PRD-cost-control(单一主题) |
| [reporting.md](reporting.md) | 报表、仪表盘与数据分析(8 角色 × 9 类报表 + 自助 BI + 数据质量) | drafts/10-报表分析/10-报表与仪表盘.md |
| [reporting-api.md](reporting-api.md) | 报表域 API 契约(仪表盘 / 数据集 / 报表 / 导出 / 订阅) | drafts/A2-API规范/Part6 |
| [mobile-h5.md](mobile-h5.md) | 移动端 H5(任务执行人工时填报)— 入口/周视图/扫码/离线/A11y | drafts/A3-UI原型/Part2B §8 |

---

## 与其他文档的关系

- **WBS.md 的工作包 Spec 字段** —— 优先引用本目录文件;若暂时只有 legacy 内容,标 `specs/legacy/xxx`
- **DESIGN.md** —— 顶层架构理由,链接到本目录各 spec;不再重复叙述
- **testing/** —— 测试方案从对应 spec 派生(spec 改了,测试方案同步)
- **CHANGELOG.md** —— spec 文件创建/废弃时记录(但 status 变化是 git 历史,不重复 changelog)

---

## 状态

- 所有 spec 当前 `status: active`(已落地实现)
- 后续 spec 拆分原则:
  - 单一主题
  - 引用不复制(链接到其他 spec)
  - 1200 行提醒线(超过考虑再拆)
