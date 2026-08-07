# 📋 Changelog — 知驭 ZhiYu

All notable changes to **zhiyu-pms** (知驭 · 项目治理平台) will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

> 本文件**只追加**。允许修正事实性错误(如版本号、失效链接),不得改写已发布条目的结论——改结论需追加新条目并注明它推翻了哪一条。
>
> 当前快照(里程碑进度 / WIP / 风险)在 [STATUS.md](STATUS.md);任务分解在 [WBS.md](WBS.md)。

---

## [Unreleased]

### 品牌重命名(2026-08-07)

- **重命名**:系统由 `pmo-pms` (PMO Project Management System) 重命名为 **知驭 ZhiYu** —— 治理视角的项目全生命周期管理平台。
  - 中文名 **知驭**:知(洞察)驭(掌控),寓意 PMO 洞察项目、掌控全局
  - 包路径 `com.company.pmo` → `com.company.zhiyu`
  - Maven artifact `pmo-pms-backend` → `zhiyu-pms-backend`
  - Spring 应用名 `zhiyu-pms-backend`
  - 数据库 `pmo_pms` → `zhiyu_pms`、DB 密码同步更新
  - Docker 镜像/容器/网络: `pmo-mysql / pmo-backend / pmo-frontend / pmo-mailpit / pmo-pg / pmo-net` → `zhiyu-*`
  - JWT dev secret、邮件签名、Postman 集合、环境变量同步更新
  - Legacy 设计文档重命名:`pmo-pms-proposal.md` → `zhiyu-proposal.md` 等
- **影响范围**:514 个文件(1436 行新增 / 1434 行删除),后端 `mvn compile + test-compile` BUILD SUCCESS,399 主源 + 22 测试源全部就绪。

### 文档规范整改(2026-08-07)

- **新增** [`docs/STATUS.md`](STATUS.md):全局项目计划执行情况的单一事实来源
- **重写** [`docs/WBS.md`](WBS.md):只登记任务分解,移除进度/状态/风险字段
- **新增** [`docs/PRD.md`](PRD.md):基线 PRD 重写,承接 v4.0.0 release
- **新增** [`docs/DESIGN.md`](DESIGN.md):架构设计重写,WHY 而非 WHAT
- **新增** [`docs/README.md`](README.md):文档地图与命名约定
- **新增** 3 份 ADR:`001-spring-boot-vue-baseline.md`、`002-mysql-pg-dual-track.md`、`003-docs-status-wbs-split.md`
- **精简** `AGENTS.md`:项目现状段下沉到 STATUS.md,AGENTS.md 只留指针
- **重组** docs/ 目录结构,按 sift 规范十个子目录落地;老 `zhiyu-requirements/` 31 个子目录归档到 `drafts/扩展文档/`

---

## [4.0.0] — 2026-06-13 · 🎯 **V4 大版本:成本 + 财务 + 预警**

> 里程碑已落地,成本引擎上线,财务模块闭环,预警数据层就绪。

### ✨ 新增模块 (Highlights)

| 模块 | 版本 | 说明 |
|------|------|------|
| **里程碑** | V3.1 | 七阶段字典 (`INTAKE / ANALYSIS / PROPOSAL / APPROVAL / KICKOFF / EXECUTION / CLOSING`) + 4 端点分析 |
| **成本引擎** | V4.0 | 工时 × 角色档 → 成本项,P0-A 核心交付 |
| **财务** | V4.2 | 合同/发票/付款/成本项 (3-way match) |
| **预警** | V4.3 | 预警实体 + 仓库 + 6 种子规则 — 数据层完成 |

### 🔧 功能增强

- **V2.6/V2.7 风险** — 风险矩阵视图 + 历史快照
- **V2.8/V2.9 组织** — 用户/部门/角色 三类 AdminController 拆分
- **V2.11-V2.13 工时/甘特/项目** — 工时审批流、资源甘特、项目健康度
- **V3.0 立项** — 全流程 5 子模块 (预算冻结/风险应对/资源计划/AI-WBS/SOW 文件)
- **P2 通知** — 多通道 IM (钉钉/飞书/企微) + SSE 实时推送 + 4 事件
- **P3 WBS** — 任务拆解 + EVM + 网络图 + 任务级甘特 + 资源分配矩阵

### 🏗 基础设施

- MySQL 迁移 (H2 测试 → MySQL dev/prod) + admin/dingtalk/tools 通用模块
- Jwt 鉴权 + RevokedToken 黑名单 + RBAC(22 端点 @RequireRoles)
- 测试 schema 分离 + 78 个测试类
- CORS + 全局异常处理 + Swagger/OpenAPI 文档

### 🎨 前端

- 18 个视图 / 17 个 API 客户端 / 13+ 个可复用组件

### 📚 文档(老仓库口径)

- PRD (成本控制) + 成本引擎设计 + MVP 设计 + 提案 + commit-split 拆批清单
- 扩展文档 A0-A6(数据字典/API规范/UI原型/数据迁移/上线计划/培训赋能)共 25 份
- README 完整项目文档 (15KB)

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
