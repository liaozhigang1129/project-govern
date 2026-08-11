# 📋 Changelog — project-govern

All notable changes to **project-govern** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

> 本文件**只追加**。允许修正事实性错误(如版本号、失效链接),不得改写已发布条目的结论——改结论需追加新条目并注明它推翻了哪一条。
>
> 当前快照(里程碑进度 / WIP / 风险)在 [STATUS.md](STATUS.md);任务分解在 [WBS.md](WBS.md)。

---

## [Unreleased]

### M7 立项评审启动(2026-08-07)

- **新增** [WP-M7-01 立项评审 plan](plans/2026-08-07-wp-m7-01-v5-scope-freeze.md):264 行,8 项关键决策 + 10 周里程碑 + 5 项风险 + 5 项北极星指标
- **新增** [ADR-005 v5 范围与关键决策](decisions/005-m7-v5-scope.md):proposed(待 D+7 拍板)
  - D1 AI 模型形态:**集成第三方**(通义 / 文心)
  - D2 AI 预测上线:**影子模式 4 周 → 灰度 → 全量**
  - D3 移动 App 形态:**Web H5 + 响应式**(本期不原生)
  - D4 报表实现:**后端聚合 + ECharts + 4 格式导出**(PDF/Excel/CSV/PNG)
  - D5 数据集查询:**预聚合 + 物化**(禁实时跨表 JOIN)
  - D6 订阅分发:Email + IM + 链接分享(3 通道)
  - D7 数据安全:导出加密 + 用户水印 + TTL 24h
  - D8 验收门禁:6 项 v5 专项门禁
- **激活** 3 份 active spec:
  - [specs/reporting.md](specs/reporting.md)— 报表 / BI / 导出(123 行)
  - [specs/reporting-api.md](specs/reporting-api.md)— 报表 API 契约(150 行)
  - [specs/mobile-h5.md](specs/mobile-h5.md)— 移动端 H5(197 行)
- **ML 模型配置修复**:4 个 plist(brand `com.pmo.ai.*` → `com.projectgovern.ai.*`)+ install.sh / healthcheck.sh(路径自推断 + 品牌同步)
- **更新** [WBS.md](WBS.md):WP-M7-01 状态从 draft → active
- **更新** [STATUS.md](STATUS.md):M7 状态从 draft 0% → active 10%,WP-M7-01 评审中

### 品牌重命名(2026-08-07)

- **重命名**:系统由 `pmo-pms` (PMO Project Management System) 重命名为 **project-govern** —— 治理视角的项目全生命周期管理平台。
  - 包路径 `com.company.zhiyu` → `com.hex.projectgovern`
  - Maven group `com.hex` / artifact `project-govern-backend`
  - 数据库 `zhiyu_pms` → `project_govern`、DB 密码 `zhiyu_dev_2025` → `project_govern_dev_2025`
  - Docker 镜像/容器/网络 `zhiyu-*` → `project-govern-*`
  - 邮件 from `知驭 ZhiYu <noreply@zhiyu.local>` → `project-govern <zg.liao@goupwith.com>`
  - OpenAPI title `知驭 ZhiYu API` → `project-govern API`
  - JWT dev secret、Postman 集合/环境名同步更新

### 文档规范整改(2026-08-07)

- **新增** [`docs/STATUS.md`](STATUS.md):全局项目计划执行情况的单一事实来源
- **重写** [`docs/WBS.md`](WBS.md):只登记任务分解,移除进度/状态/风险字段
- **新增** [`docs/PRD.md`](PRD.md):基线 PRD 重写,承接 v4.0.0 release
- **新增** [`docs/DESIGN.md`](DESIGN.md):架构设计重写,WHY 而非 WHAT
- **新增** [`docs/README.md`](README.md):文档地图与命名约定
- **新增** 3 份 ADR:`001-spring-boot-vue-baseline.md`、`002-mysql-pg-dual-track.md`、`003-docs-status-wbs-split.md`
- **精简** `AGENTS.md`:项目现状段下沉到 STATUS.md,AGENTS.md 只留指针
- **重组** docs/ 目录结构,按 sift 规范十个子目录落地;老 `pmo-pms-requirements/` 31 个子目录归档到 `drafts/扩展文档/`

### M7-02 v5 数据模型增量(2026-08-11)

- **新增** [WP-M7-02 数据模型 plan](plans/2026-08-11-wp-m7-02-v5-data-model.md):21163 字节 / 10 步 / 8 新表 + 6 表扩展 + 5 状态机 + 12 索引
  - **8 张新表**(`reporting` 模块):
    - `dashboard` / `dashboard_widget` / `dataset` / `dataset_field`
    - `report_template` / `report_export` / `report_snapshot` / `report_subscription`
  - **6 张已有表扩展**(非破坏性):`project`(`health_score`) / `milestone`(EVM 三字段) / `wbs_task`(`progress_percent`) / `risk`(`heat_score`) / `app_user`(`default_dashboard_id`) / `initiation_ai_wbs_draft`(复合索引)
  - **5 个状态机 enum**:`DashboardStatus` / `DatasetStatus` / `ReportExportStatus` / `ReportSnapshotStatus` / `SubscriptionStatus`
  - **12 个索引**(D5 预聚合路径优化)
  - **1 个 Flyway 迁移**:`V7.0__reporting_schema.sql`(PG + MySQL 双轨,`INFORMATION_SCHEMA` 守卫幂等)
  - **1 个种子数据**:`V7.1__reporting_seed.sql`(8 角色 dashboard + 9 报表 template + 5 dataset + 8 system_config 映射)
- **修订** [`docs/WBS.md`](WBS.md):WP-M7-02 状态 → 🟡 active,plan 引用 + 头部 draft 提示
- **注**: plan 落地后需等 WP-M7-01 D+7 整合会议拍板(ADR-005 proposed → accepted)才能正式启动 V7.0 迁移

### M7-03 v5 报表后端 + 4 格式导出(2026-08-11)

- **新增** [WP-M7-03 报表后端 + 导出 plan](plans/2026-08-11-wp-m7-03-reporting-export.md):30403 字节 / 14 步
  - **5 控制器**: `DashboardController` / `DatasetController` / `ReportController` / `ReportExportController` / `ReportSubscriptionController`
  - **5 服务**: `DashboardService` / `DatasetService` / `ReportService` / `ReportExportService` / `ReportSubscriptionService`
  - **4 导出器(策略模式)**: `PdfExporter`(OpenPDF + Thymeleaf) / `ExcelExporter`(POI SXSSF 流式) / `CsvExporter`(Commons CSV + UTF-8 BOM) / `PngExporter`(Playwright + Chromium)
  - **1 调度器**: `ReportSnapshotScheduler`(@Scheduled cron `0 0 1 * * *` 每天 01:00 物化)
  - **1 异步任务引擎**: `@Async("reportingTaskExecutor")` + ThreadPoolTaskExecutor(4-16 线程,队列 100)
  - **12 API 端点**: 仪表盘 CRUD 8 / 数据集查询 2 / 报表运行 2 / 导出 2 / 订阅 1
  - **D5 决策核心**: `DatasetService` 优先走 `report_snapshot`,回退 `dataset.sql_template`;严查跨表 JOIN
  - **D6 决策**: Email + IM + 链接分享 3 通道,失败 3 次指数退避(1s/5s/25s)
  - **D7 决策**: 加密 URL + 用户水印 + TTL 24h(订阅)/ 7d(主动导出)
  - **D8 门禁**: 同步导出 1000 行 / 5MB 上限,异步 SLA < 60s
- **新增 Maven 依赖**: OpenPDF 1.3.39 / Commons CSV 1.10.0 / Playwright 1.40.0 / Thymeleaf starter
- **修订** [`docs/WBS.md`](WBS.md): WP-M7-03 状态 → 🟡 active,plan 引用
- **注**: 等 V7.0 Flyway 迁移完成后才能正式启动 API 实现

---

## [4.0.0] — 2026-06-13 · 🎯 **V4 大版本:成本 + 财务 + 预警**

> 里程碑已落地,成本引擎上线,财务模块闭环,预警数据层就绪。
> 
> 对应老仓库 pmo-pms tag v4.0.0 (commit c45df49) 与 2026-06-13 release notes。

### ✨ 新增模块 (Highlights)

| 模块 | 版本 | 说明 |
|------|------|------|
| **立项三级审批** | V6.0 | DEPT_LEAD → PMO_ADMIN → EXEC 三级审批引擎 |
| **工时周报** | V2.13 | 周报提交 + 审批 + 14 天锁 + 跨周保留 |
| **通知中心** | V2.7 | 钉钉/飞书/企微 + SSE 实时推送 + 邮件 + 4 事件 |
| **钉钉 OA 同步** | V2.13 | 部门/考勤/请假 webhook 对接 (phase1) |
| **健康度看板** | V2.10 | GREEN/YELLOW/RED 三级, 里程碑加权计算 |
| **WBS 编辑器** | V3.0 | 任务拖拽 + 依赖 + EVM + 资源分配 |
| **里程碑** | V3.1 | 七阶段字典 (`INTAKE / ANALYSIS / PROPOSAL / APPROVAL / KICKOFF / EXECUTION / CLOSING`) + 4 端点分析 |
| **成本引擎** | V4.0 | 工时 × 角色档 → 成本项, P0-A 核心交付 |
| **财务** | V4.2 | 合同/发票/付款/成本项 (3-way match) |
| **预警** | V4.3 | 预警实体 + 仓库 + 6 种子规则 — 数据层完成 |
| **风险** | V2.6/V2.7 | 风险矩阵视图 + 历史快照 + 8 项目种 |
| **AI 辅助** | V3.0 | AI 预测/草稿/合同审查 (影子模式 4 周) |

### 🔧 功能增强

- **立项审批升级 (WP-M7-03~07)** — InitiationService 全量委托 ApprovalEngine, ApprovalRecord 双写兼容
- **组织** — 用户/部门/角色 三类 AdminController 拆分 (V2.8/V2.9)
- **工时 + 甘特** — 工时审批流、资源甘特、项目健康度 (V2.11-V2.13)
- **立项全流程** — 5 子模块 (预算冻结/风险应对/资源计划/AI-WBS/SOW 文件) (V3.0)
- **通知** — 多通道 IM + SSE 实时推送 + 4 事件 (P2)

### 🐞 Bug Fixes (V4.0~V4.0.x)

- 工时计算 14 天锁 LocalDate.now() 漂移 (P2 #16)
- InitiationService.decide 引擎 APPROVED 误判终态 (P2 #19/WP-M7-06)
- MilestoneAiAdvisor scoreOverdue int 除法丢失精度 (P2 #24)
- H2 测试 schema 缺 MilestoneStatus/InitiationStatus 字典 (P2 #24)
- PG seed 重跑失败 (MySQL INSERT IGNORE / PG ON CONFLICT) (P2 #17)
- 项目类型 seed 缺 DELIVERY (P2 #24)

### 🏗 基础设施

- **数据库**: MySQL 8.0 (生产) + PostgreSQL 16 (CI) + H2 (测试)
- **后端栈**: Spring Boot 2.7 → 3.3, Java 17 → 21, JPA/Hibernate, Flyway 迁移
- **鉴权**: JWT + RevokedToken 黑名单 + RBAC (22 端点 @RequireRoles)
- **测试**: 78 个测试类, 集成 + 单元双层, H2 in-memory
- **其他**: CORS + 全局异常处理 + Swagger/OpenAPI 文档 + Clock bean 注入 (P2 #16)

### 🎨 前端

- 18 个视图 / 17 个 API 客户端 / 13+ 个可复用组件
- Vue 3 + Vite + TypeScript + Element Plus + Pinia + ECharts
- ESLint + Prettier, 354 处 any 待清理 (P2 #27)

### 📚 文档

- PRD (成本控制) + 成本引擎设计 + MVP 设计 + 提案 + commit-split 拆批清单
- 扩展文档 A0-A6 (数据字典/API规范/UI原型/数据迁移/上线计划/培训赋能) 共 25 份
- README 完整项目文档 (15KB)
- CHANGELOG.md (本文件) 重启后从 4.0.0 重新维护

---

## [3.x] — 历史版本

| 版本 | 主题 | 关键模块 |
|------|------|----------|
| 3.0 | 立项全流程 | initiation (5 子模块) |
| 2.13 | 项目增强 | project (健康度/状态机) |
| 2.12 | 甘特图 | workload/gantt |
| 2.11 | 工时 | timesheet (审批流) |
| 2.9 | 角色 | org (RoleAdminController) |
| 2.8 | 用户/部门 | org (UserAdmin + DepartmentAdmin) |
| 2.7 | 风险快照 | risk |
| 2.6 | 风险矩阵 | risk |

---

## 累计基线数据(截至 v4.0.0)

> 完整盘点见 [STATUS.md §7](STATUS.md)。此处仅做变更日志的"事实快照"。

| 维度 | 数据 |
|------|------|
| 后端模块数 | 19 个 |
| Controller 数 | 35 个 |
| Service + Repository | 估计 80+ |
| 实体 (Entity) | 估计 50+ |
| 测试类 | 78 个 |
| OpenAPI | 33 paths / 37 schemas / 10 tags |
| Flyway | PG ~40 版本 / MySQL ~36 版本 |
| 数据表 | A1 数据字典覆盖 79 张表 |
| 前端 | 18 视图 / 17 API 客户端 / 13+ 组件 |
