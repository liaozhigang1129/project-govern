# 📋 Changelog — PMO · PMS

All notable changes to **pmo-pms** (PMO 项目管理系统) will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [4.0.0] — 2026-06-13 · 🎯 **V4 大版本:成本 + 财务 + 预警**

> 里程碑已落地,成本引擎上线,财务模块闭环,预警数据层就绪。

### ✨ 新增模块 (Highlights)

| 模块 | 版本 | 说明 |
|------|------|------|
| **里程碑** | V3.1 | 七阶段字典 (`INTAKE / ANALYSIS / PROPOSAL / APPROVAL / KICKOFF / EXECUTION / CLOSING`) + 4 端点分析 (进度/健康/趋势/项目组合) |
| **成本引擎** | V4.0 | 工时 × 角色档 → 成本项,P0-A 核心交付 |
| **财务** | V4.2 | 合同/发票/付款/成本项 (3-way match:合同 ↔ 发票 ↔ 付款 ↔ 成本) |
| **预警** | V4.3 | 预警实体 + 仓库 + 6 种子规则 (成本/进度/质量/风险/资源/合规) — 数据层完成 |

### 🔧 功能增强

- **V2.6/V2.7 风险** — 风险矩阵视图 + 历史快照
- **V2.8/V2.9 组织** — 用户/部门/角色 三类 AdminController 拆分
- **V2.11-V2.13 工时/甘特/项目** — 工时审批流、资源甘特、项目健康度
- **V3.0 立项** — 全流程 5 子模块 (预算冻结/风险应对/资源计划/AI-WBS/SOW 文件)
- **P2 通知** — 多通道 IM (钉钉/飞书/企微) + SSE 实时推送 + 4 事件 (timesheet/finance/alert/system)
- **P3 WBS** — 任务拆解 + EVM (BAC/PV/EV/AC/CPI/SPI) + 网络图 + 任务级甘特 + 资源分配矩阵

### 🏗 基础设施

- **跨模块**:MySQL 迁移 (H2 测试 → MySQL dev/prod) + admin/dingtalk/tools 通用模块
- **认证**:Jwt 鉴权 + RevokedToken 黑名单 + RBAC
- **测试**:测试 schema 分离 + 31 个测试类,**286/286 全绿** (含 Jacoco 覆盖率报告)
- **跨域**:CORS + 全局异常处理 + Swagger/OpenAPI 文档

### 🎨 前端 (V3.x + V4.x 配套 UI)

- **18 个视图**:Dashboard / Projects / ProjectDetail / Initiations / MilestoneAnalysis /
  WbsView / Workload / Gantt / Timesheets / TimesheetApprovals / RiskView / RiskMatrixView /
  RiskHealthView / CostDashboard / CostUserMonth / ImBindings / ImQuietHours / Login
- **17 个 API 客户端**:client / users / departments / roles / timesheet / workload / gantt /
  wbs / risk / cost / notification / im-binding / im-quiet-hours / sse / systemConfig /
  milestoneAnalysis
- **13+ 个可复用组件**:GanttView / WbsTreeView / WbsGanttView / WbsNetworkView /
  WbsAssignmentMatrix / EvmTrendCard / RiskList / RiskMatrixView / RiskDetailDrawer /
  RiskFormDialog / MilestoneDrawer / NotificationCenter + 子目录 initiation/wbs

### 🧰 工程化

- **Makefile** — 一键 `make test / build / run / clean / coverage`
- **scripts/** — commit-split、run-without-test、init-mysql 等
- **docs/** — PRD (成本控制) + 成本引擎设计 + MVP 设计 + 提案 + commit-split 拆批清单
- **README** — 完整项目文档 (15KB)

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

## [Unreleased]

### 🚧 进行中

- **预警控制器** (V4.3 → V4.4) — 实体已就绪,补 AlertController 端点 + 触发器
- **工时 → 成本引擎 P0 · 1 周落地** — 完整优化版:实时计算 + 月度快照 + 多币种 + 部门维度

### 📋 计划

- CI/CD — GitHub Actions (maven-test.yml)
- Docker compose — 一键起 MySQL + 后端 + 前端
- OpenAPI 客户端生成 — 自动同步前端 API
- 国际化 (i18n) — zh-CN / en-US
- 钉钉/飞书 深度集成 — 工作台免登 + 消息卡片回调

---

## 📊 累计数据 (截至 v4.0.0)

| 维度 | 数据 |
|------|------|
| 后端模块数 | **19 个** (admin/alert/cost/dashboard/dict/dingtalk/finance/healthadvisor/initiation/member/milestone/notification/org/project/risk/timesheet/wbs/workflow/workload + common + tools) |
| Controller 数 | **35 个** |
| Service + Repository | 估计 80+ |
| 实体 (Entity) | 估计 50+ |
| 测试类 | **31 个** |
| 测试通过率 | **286/286 (100%)** |
| 前端视图 | **18 个** (含 2 个 .bak 备份) |
| 前端 API 客户端 | **17 个** |
| 前端组件 | **13+ 个** (含 initiation/wbs 子目录) |
| 总提交数 | **73** (含历史) / **14** (本会话拆分) |
| Tag 数 | **4** (v4.0.0 / v4.0.0-cost / v4.0.0-finance / v4.0.0-alert-data) |
| 文档 | **README + 4 PRD + commit-split 清单 + 工程脚本** |

---

## 🏷 Tags

```
v4.0.0            ← 主版本,完整 V4 大版本
v4.0.0-cost       ← V4.0 成本引擎起点 (1cb1705)
v4.0.0-finance    ← V4.2 财务模块 (2fc58b5)
v4.0.0-alert-data ← V4.3 预警数据层 (a3ffa2e)
```

---

> **里程碑** · 自 2026-05 立项 → V4.0.0 发布:约 6 周 · 73 commits · 286 测试

---

## [4.19.0] — 2026-06-17 · 🚀 **V4.19 立项向导:资源/风险/预算全面联动**

> 立项全流程 Step 4/5/6 全部打通系统人员联动,合同金额双向同步,AI WBS apply 改为幂等。

### ✨ 功能增强

| 改进 | 位置 | 说明 |
|------|------|------|
| 选人联动系统人员 | `Step4ResourceAndDelivery.vue` | 新增 `el-select` 走 `/api/users/options` + `/api/users?keyword=` 远程搜索;选人后自动带入 `userName / roleCode / hourlyRate`(从 `app_user.default_hourly_rate`) |
| 合同金额双向同步 | `Step4ResourceAndDelivery.vue` + `InitiationController` | 改 Step 4 合同金额 → 立即 PATCH `/api/initiations/{id}` → 同步到立项表 → emit 回 Step 1 |
| Step 6 后端实时聚合 | `Step6BudgetAndMargin.vue` | 切到 Step 6 自动调 4 个接口:立项表(合同) + resource-plans(资源) + risks(风险) + budget-freeze/latest(快照);不再依赖 Wizard props |
| 全量 DELETE 端点 | `InitiationResourcePlanController` / `InitiationRiskResponseController` | 新增 `DELETE /initiations/{id}/resource-plans` 和 `.../risks`,前端 Step 4/5 全量覆盖不再走 N 次单条删除 |
| applyDraft 幂等 | `InitiationAiWbsService.applyDraft()` | 已 applied 过的 draft 不再抛 409,返回 `{idempotent: true, note: "..."}`,前端重试/双击/网络抖动都不会失败 |

### 🐛 Bug 修复

| 现象 | 根因 | 修复 |
|------|------|------|
| `保存资源计划失败: Internal error: Request method 'DELETE' is not supported, 后端服务暂时不可达(500)` | 前端调 `api.delete('/resource-plans')` 无参,后端只有 `DELETE /{planId}` 单条接口 | 后端新增 `DELETE /initiations/{id}/resource-plans` 全量端点 + `deleteAllByInitiation()` Service 方法 |
| `应用失败: Draft already applied at 2026-06-17T03:12:53Z` | `applyDraft()` 硬抛 409 | 改为幂等返回,`{idempotent: true}` |
| `Step 4 资源派遣人员不联动系统人员` | 老组件用 `el-input` 手填姓名 | 改为 `el-select remote filterable`,走 `/api/users/options` |
| `Step 6 预算不联动资源派遣/风险应对/合同金额` | 老组件用 Wizard props 传值,容易过期 | 改为 Step 6 mount 时实时拉 4 个后端接口 |

### 📦 技术细节

- **后端 jar** 已 docker cp 到 `pmo-backend:/app/app.jar` 并重启加载
- **前端 dist** 已 docker cp 到 `pmo-frontend:/usr/share/nginx/html/assets/` 并 `nginx -s reload`
- **git**: `b3572fc feat(initiation): V4.19 立项向导资源派遣/风险/预算 全面联动`

### 🧪 E2E 验证记录

```
Step 4 全量删除 + 重建: ✅ cost=60000
Step 5 全量删除 + 重建: ✅ cost=8000
Step 6 冻结:            ✅ totalCost=73000  margin=815888.88  marginPct=91.79%
apply 二次幂等:          ✅ idempotent=True  note="之前已应用过,本次为幂等返回"
```