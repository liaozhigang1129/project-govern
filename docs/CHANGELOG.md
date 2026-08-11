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

### M7-04 v5 可视化与 AI 看板(2026-08-11)

- **新增** [WP-M7-04 可视化与 AI 看板 plan](plans/2026-08-11-wp-m7-04-ai-dashboard.md):19113 字节 / 16 步
  - **3 端 / 14 视图**: 桌面端 9 视图 (8 角色仪表盘 + 数据质量看板) + 移动端 4 视图 (工时/任务/通知/我的) + 共享 1 视图
  - **1 通用 + 8 角色配置**架构: 1 个 `DashboardView.vue` 避免 8 份重复,8 角色默认配置走 `system_config`
  - **3 套 Widget 渲染器**: `KpiCardRenderer` / `ChartRenderer`(ECharts 6.1 + 8 chartType) / `TableRenderer`(虚拟列表 el-table-v2)
  - **数据质量看板**: 3 指标 (完整率/准确率/时效性) + 3 异常检测规则 (超时未更新/孤儿任务/超载人员) + 后端 5 API 复用 Alert 引擎
  - **移动端 H5**: 4 Tab 路由 + `MobileLayout.vue` + 响应式断点 < 768px
  - **离线缓存**: IndexedDB (idb 7.x) + 5 周历史 + CRDT 冲突解决 (last-write-wins)
  - **扫码模块**: ZXing JS + 任务二维码格式 `pmo-task://<taskId>?projectId=<p>`
  - **Web Vitals 监控**: web-vitals 5.x 上报 (LCP/FID/CLS/INP/TTFB)
  - **A11y 自动化**: @axe-core/playwright 4.x + CI 集成 (0 critical / 0 serious)
  - **D8 门禁**: LCP < 2.5s / FID < 100ms / CLS < 0.1 / H5 首屏 < 1s / 60fps 滑动
- **新增 npm 依赖**: @zxing/browser + @zxing/library + web-vitals 5 + @axe-core/playwright 4 + idb 7
- **修订** [`docs/WBS.md`](WBS.md): WP-M7-04 状态 → 🟡 active,plan 引用
- **注**: 等 WP-M7-03 后端 API 落地后才能启动前端消费

### M7-01 D+7 整合会议 agenda(2026-08-11)

- **新增** [D+7 整合会议 agenda](reviews/2026-08-18-wp-m7-01-d7-integration-meeting.md):10018 字节 / 60 min / 8 项决策拍板
  - **会议信息**: 2026-08-18(周二)14:00-15:00,线下 + 钉钉/飞书,Sponsor 必到
  - **8 项决策逐项议程**: 每项 5-6 min (1 min 陈述 + 2 min 意见摘要 + 2 min 讨论 + 1 min 拍板)
    - D1 AI 模型: 集成第三方 (通义/文心) - 选 A
    - D2 AI 上线: 影子模式 4 周 → 灰度 → 全量 - 选 A
    - D3 移动形态: Web H5 + 响应式 (本期不原生) - 选 A (关键决策,讨论时间 8 min)
    - D4 报表实现: 后端聚合 + ECharts + 4 格式导出 - 选 A
    - D5 数据集: 预聚合 + 物化 (禁实时跨表 JOIN) - 选 A
    - D6 订阅分发: Email + IM + 链接分享 (3 通道) - 选 A
    - D7 数据安全: 加密 + 用户水印 + TTL 24h - 选 A
    - D8 验收门禁: 6 项 v5 专项门禁 - 选 A
  - **会前准备** (D+1~D+6): 评审意见汇总到 [review.md](reviews/2026-08-07-wp-m7-01-review.md) (9 角色 checklist)
  - **会后 Actions** (D+7 24h 内): A1-A10 10 个 action, 包括签字版 ADR-005 + 4 个 sub-plan 启动实施 + V7.0 Flyway 迁移
  - **风险预案**: Sponsor 临时不到 / 3 项以上决策不通过 / 评审意见迟到 / 安全合规强保留意见
- **新增** [评审意见汇总模板](reviews/2026-08-07-wp-m7-01-review.md):144 行,D+1~D+6 异步评审 9 角色 checklist
- **修订** [`docs/WBS.md`](WBS.md): WP-M7-01 加 D+7 agenda + review 模板引用
- **修正日期 bug**: review.md window 由 D+1(2026-08-08) ~ D+6(2026-08-13) 修正为 D+1(2026-08-10) ~ D+6(2026-08-17) (跳过周末)

### M7-01 D+7 会议预演 + V7.0 验证报告(2026-08-11)

- **新增** [D+7 会议预��报告](reviews/2026-08-13-wp-m7-01-d7-meeting-dry-run.md):9065 字节
  - **8 项决策讨论时间预估**: 基线 45 min(决策) / 乐观 30 min / 悲观 54 min
  - **3 个高风险拖延决策**:
    - D3 移动 App(70% 拖延概率,6-12 min,3 个 Sponsor 关键问题)
    - D7 数据安全(60% 拖延概率,4-8 min,安全/合规可能强保留)
    - D4 报表/导出(40% 拖延概率,4-7 min,自研可持续性)
  - **时间压缩预案**: 方案 A(Sponsor 6 项无异议 + 2 项重点讨论)= 28 min 决策 + 10 min 开场/闭幕 = 47 min
  - **3 个时间优化建议**: 文档预签 / 异步预审(下次 v5.1)/ 会议录制纪要
  - **7 个风险预案**: Sponsor 不到 / D3 拖延 / D7 强保留 / 2+ 项不通过 / 评审迟到 / 网络故障 / 设备故障
  - **关键 bug 发现**: plan §2.1 写 D+1 ~ D+7 但周末未跳过, 正确日期为 2026-08-10(周一) ~ 2026-08-18(周二)
- **新增** [D+7 无异议项逐字通过话术](reviews/2026-08-13-wp-m7-01-d7-no-objection-scripts.md):5560 字节
  - 6 项无异议话术(D1/D2/D4/D5/D6/D8):平均 75 秒/项,总 7.6 min
  - 关键数字(SLA / 工时 / 阈值):准确,可直接引用
  - 控时技巧:15 秒沉默规则 / 2 次追问规则 / 3 次挑战规则
  - 异常情形话术:Sponsor 临时变卦 / 角色挑战 / 信息缺失
  - 修订时间表:6 项无异议 7.6 min + D3/D7 16 min + 开闭/范围 10 min + buffer 23 min
- **新增** [V7.0 Flyway 迁移成功报告](reviews/2026-08-11-v7-flyway-migration-success.md):242 行
  - 8 表 + 12 索引 + 6 扩展列 全部就位
  - 后端 ZhiyuApplication 启动成功 11.122s + health 200
- **修正 sponsor pre-brief 日期**: D+6 2026-08-13 → D+6 2026-08-17(周一, 跳过周末)
- **修正 review.md 日期**: D+1(2026-08-08) → D+1(2026-08-10 周一)

### WP-M7-02 V7.1 Flyway 种子数据 + D+7 深度议程 + Sponsor 推送(2026-08-11)

- **新增** [V7.1 PG 种子数据](../backend/src/main/resources/db/migration-pg/V7.1__reporting_seed.sql):172 行
  - 5 dataset + 41 dataset_field + 8 dashboard + 9 report_template + 12 system_config
  - 本地验证:dashboard 8 / dataset 5 / dataset_field 41 / report_template 9 / system_config 12 全部就位
  - 幂等验证:再跑 1 次 INSERT 0 0, 计数不变(ON CONFLICT (code) DO NOTHING ✓)
- **新增** [V7.1 MySQL 种子数据](../backend/src/main/resources/db/migration-mysql/V7.1__reporting_seed.sql):161 行
  - 字段与 PG 1:1 对应, 使用 INSERT IGNORE 幂等
  - 字段类型与 PG 同步 (BIGINT/NUMERIC/INT/DATE/VARCHAR/TEXT/JSON/BOOLEAN/TIMESTAMPTZ)
- **新增** [D+7 6 项无异议项逐字通过话术](reviews/2026-08-13-wp-m7-01-d7-no-objection-scripts.md):5560 字节
  - 6 项无异议话术(D1/D2/D4/D5/D6/D8):平均 75 秒/项, 总 7.6 min
  - 关键数字(SLA / 工时 / 阈值):准确, 可直接引用
  - 控时技巧:15 秒沉默规则 / 2 次追问规则 / 3 次挑战规则
  - 异常情形话术:Sponsor 临时变卦 / 角色挑战 / 信息缺失
  - 修订时间表:6 项无异议 7.6 min + D3/D7 16 min + 开闭/范围 10 min + buffer 23 min
- **新增** [D+7 D3+D7 深度议程](reviews/2026-08-13-wp-m7-01-d3-d7-deep-scripts.md):7257 字节
  - D3 移动 App 4 阶段(陈述 + Q1/Q2/Q3 + 反问 + 拍板)
  - D7 数据安全 4 阶段(陈述 + Q1/Q2/Q3 + 反问 + 拍板)
  - Q1-Q3 答案:IM 双通道 / 审计 30 天 / KPI 验证
  - 应急方案(超时 / 强保留 / 反问 3 次)
  - PMO 控时清单(秒级时间表)
- **新增** [Sponsor 推送信](reviews/2026-08-14-sponsor-push-letter.md):3834 字节
  - 800 字, 5 min 读完
  - 3 项附件(pre-brief + 话术 + 深度议程, 合计 20 min)
  - 2 项具体动作(标注 8 项决策 + 准时入会)
  - 标注提交方式:邮件 / 钉钉 / GitHub PR
  - 截止时间:D+6 2026-08-17 23:59(周一, 已修正周末)

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
