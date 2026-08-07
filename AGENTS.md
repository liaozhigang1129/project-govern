# AGENTS.md — PMO · PMS 代理导航

> 治理视角的项目全生命周期管理系统:**立项审批 → 项目执行 → 里程碑跟踪 → 健康度监控 → 成本 / 工时 / 风险 / 通知**。

## 先读

- 文档地图、命名约定、上下文加载规则：[docs/README.md](docs/README.md)
- 产品需求：[docs/PRD.md](docs/PRD.md)
- 架构与设计：[docs/DESIGN.md](docs/DESIGN.md)
- 全局状态与里程碑进度：[docs/STATUS.md](docs/STATUS.md)
- 工作分解(只列任务结构):[docs/WBS.md](docs/WBS.md)
- 版本演进：[docs/CHANGELOG.md](docs/CHANGELOG.md)
- 架构决策原文：[docs/decisions/](docs/decisions/)

## 项目定位

PMO·PMS 是 **Spring Boot 3.3 + Java 21 + Vue 3.5** 的前后端分离项目,治理视角的项目全生命周期管理系统。当前进度、风险、决策快照一律在 [STATUS.md](docs/STATUS.md);任务分解在 [WBS.md](docs/WBS.md);版本历史在 [CHANGELOG.md](docs/CHANGELOG.md)。**AGENTS.md 不放现状与进度,只放指针与规则。**

代理默认只在 `status: active | draft` 的文档中工作;`done / abandoned / superseded`、`reviews/`、`CHANGELOG.md` 默认不加载,仅在回溯类任务中显式读取。

## 上下文规则(摘要)

- 默认上下文集:仅 `status: active | draft` 的文档。
- 不全量加载 `docs/`;按 `docs/README.md` 的"按任务类型的默认上下文集"表选读。
- 引用不复制:事实只写一次,其余地方链接。

## 工作纪律

- **代码改动**:必须对应 `specs/` 下的接口/数据契约;先改 spec,再改代码,再补测试。
- **文档改动**:`specs/` 跟随代码同步;`plans/` 完成后置 `done`;`decisions/` 只追加不修改;`reviews/` 存档即终态。
- **changelog**:正文不留版本对比,版本间变更只追加到 `docs/CHANGELOG.md`。
- **PR / commit**:遵循 14-commit 拆分模板(见 `docs/analysis/commit-splits/`),按模块拆,每个 commit 独立可回滚。
