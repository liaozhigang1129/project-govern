# 🚀 v4.0.0 Release Notes

**Released**: 2026-06-13 · **Tag**: `v4.0.0` · **Commit**: `c45df49`

> 🎯 **V4 大版本:成本 · 财务 · 预警 — 里程碑 + 成本引擎 + 3-way match**

---

## 📦 概览

PMO · PMS V4 是一次**里程碑式的版本**,核心交付:
- ✅ **成本引擎** (V4.0) — 工时 × 角色档 → 成本项
- ✅ **财务模块** (V4.2) — 合同/发票/付款/成本项 3-way match 闭环
- ✅ **预警数据层** (V4.3) — 6 类种子规则就绪,控制器收尾中
- ✅ **里程碑** (V3.1) — 七阶段字典 + 4 端点分析
- ✅ **测试 286/286 全绿**

---

## ✨ What's New

### 🧮 V4.0 — 成本引擎 (P0-A)

PMO 系统的**核心模块**,把工时数据自动换算成项目成本。

**数据流**:
```
Timesheet (工时) × RoleRate (角色档) → CostItem (成本项) → Finance Invoice (财务发票)
```

**关键实体**:
- `CostItem` — 成本项 (含 projectId / userId / period / hours / rate / amount / currency / source)
- `RoleRate` — 角色档 (role / level / hourlyRate / effectiveFrom / effectiveTo)
- `CostAllocation` — 分配规则 (项目/部门/期间分摊)

**端点**:
- `POST /api/cost/calculate` — 触发重算
- `GET  /api/cost/items` — 查成本项 (按 project/period/user 筛选)
- `GET  /api/cost/dashboard` — 成本看板 (按项目/部门/角色聚合)
- `GET  /api/cost/user-month` — 用户月度成本

**前端视图**:
- `CostDashboard.vue` — 成本看板
- `CostUserMonth.vue` — 用户月度成本

---

### 💰 V4.2 — 财务模块 (3-way match)

实现合同 ↔ 发票 ↔ 付款 ↔ 成本的**三方匹配闭环**。

**关键实体**:
- `Contract` — 合同 (甲方/乙方/金额/有效期/SLA)
- `Invoice` — 发票 (含 contractId / amount / taxRate / status)
- `Payment` — 付款 (含 invoiceId / paidAt / amount / method)
- `CostItem` — 成本项 (与 V4.0 共享,带 finance 引用)

**校验逻辑**:
```
Contract.total ≥ Σ Invoice.amount ≥ Σ Payment.amount
            =  Σ CostItem.allocatedAmount
```

**端点**:
- `POST /api/finance/contracts` — 创建合同
- `POST /api/finance/invoices` — 创建发票 (校验 ≤ 合同余额)
- `POST /api/finance/payments` — 创建付款 (校验 ≤ 发票余额)
- `GET  /api/finance/match/{invoiceId}` — 3-way match 校验

---

### ⚠️ V4.3 — 预警数据层

**6 类种子规则**就绪,控制器将在 V4.4 补齐:

| 规则 | 触发条件 | 数据源 |
|------|----------|--------|
| 成本超支 | `actualCost > budget × 1.1` | CostItem + Project |
| 进度滞后 | `SPI < 0.8` | WbsTask |
| 质量下滑 | `defectRate > threshold` | Risk + Quality |
| 资源过载 | `allocationRate > 1.2` | Workload + Timesheet |
| 风险升级 | `riskScore > 15` | Risk |
| 合规逾期 | `auditDue < today` | AuditLog |

---

### 🏁 V3.1 — 里程碑

七阶段字典:
```
INTAKE → ANALYSIS → PROPOSAL → APPROVAL → KICKOFF → EXECUTION → CLOSING
```

**端点**:
- `GET /api/milestone/progress` — 阶段进度
- `GET /api/milestone/health` — 健康度
- `GET /api/milestone/trend` — 趋势
- `GET /api/milestone/portfolio` — 项目组合分析

---

## 🔧 What's Improved

| 模块 | 版本 | 改进 |
|------|------|------|
| 风险 | V2.6/V2.7 | 矩阵视图 + 历史快照 |
| 组织 | V2.8/V2.9 | 用户/部门/角色三类 AdminController 拆分 |
| 工时+甘特+项目 | V2.11-V2.13 | 审批流 + 资源甘特 + 项目健康度 |
| 立项 | V3.0 | 5 子模块 (预算冻结/风险应对/资源计划/AI-WBS/SOW) |
| 通知 | P2 | 钉钉/飞书/企微 三通道 + SSE + 4 事件 |
| WBS | P3 | 拆解 + EVM + 网络图 + 任务级甘特 + 资源矩阵 |

---

## 🏗 Infrastructure

- **MySQL 迁移** — 测试 H2 → dev/prod MySQL (跨模块 admin/dingtalk/tools)
- **认证** — Jwt + RevokedToken 黑名单 + RBAC
- **测试** — 31 个测试类 · **286/286 全绿** · Jacoco 覆盖率
- **API 文档** — Swagger/OpenAPI

---

## 🎨 Frontend (V3.x + V4.x)

| 类别 | 数量 | 详情 |
|------|------|------|
| 视图 | **18** | Dashboard / Projects / Initiations / MilestoneAnalysis / WbsView / Workload / Gantt / Timesheets / RiskView / RiskMatrixView / CostDashboard / CostUserMonth / ImBindings / ... |
| API 客户端 | **17** | users / departments / roles / timesheet / workload / gantt / wbs / risk / cost / notification / im-binding / im-quiet-hours / sse / systemConfig / ... |
| 组件 | **13+** | GanttView / WbsTreeView / WbsGanttView / WbsNetworkView / WbsAssignmentMatrix / EvmTrendCard / RiskList / RiskMatrixView / RiskDetailDrawer / RiskFormDialog / MilestoneDrawer / NotificationCenter / ... |

---

## ⬆️ Upgrade Notes

### 从 V3.x 升级到 V4.0.0

**数据库迁移** (MySQL):
```sql
-- 1. 新增表
CREATE TABLE cost_item (...);     -- 成本项
CREATE TABLE role_rate (...);     -- 角色档
CREATE TABLE cost_allocation (...); -- 分配规则
CREATE TABLE contract (...);      -- 合同
CREATE TABLE invoice (...);       -- 发票
CREATE TABLE payment (...);       -- 付款
CREATE TABLE alert_rule (...);    -- 预警规则
CREATE TABLE alert_event (...);   -- 预警事件

-- 2. 新增字典
INSERT INTO sys_dict (type, code, name) VALUES
  ('MILESTONE_STAGE', 'INTAKE',    '立项受理'),
  ('MILESTONE_STAGE', 'ANALYSIS',  '需求分析'),
  ...
  ('MILESTONE_STAGE', 'CLOSING',   '项目收尾');

-- 3. 新增种子规则 (6 条预警)
INSERT INTO alert_rule (code, name, severity, expression) VALUES
  ('COST_OVERRUN', '成本超支', 'HIGH', 'actualCost > budget * 1.1'),
  ...
```

**配置变更** (`application.yml`):
```yaml
cost:
  engine:
    enabled: true
    defaultCurrency: CNY
    rounding: HALF_UP
  alert:
    scanInterval: 300  # 秒
finance:
  match:
    enabled: true
    toleranceRate: 0.001  # 0.1% 容差
notification:
  channels:
    dingtalk: ${DINGTALK_WEBHOOK:}
    feishu:   ${FEISHU_WEBHOOK:}
    wechat:   ${WECHAT_WEBHOOK:}
```

---

## 🐛 Known Issues

1. **预警控制器未交付** — V4.3 仅数据层,AlertController 将在 V4.4 补齐
2. **`uploads/` 未提交** — 建议 `.gitignore` 或单独 commit
3. **`3}` 误建文件** — 在 c14 历史里,可 rebase 清理

---

## 📊 Stats

| 维度 | 数据 |
|------|------|
| 后端模块 | **19** |
| Controller | **35** |
| 测试 | **31 类 · 286/286 全绿** |
| 前端视图 | **18** |
| 前端 API | **17** |
| 前端组件 | **13+** |
| 本版本新增 commit | **14** |
| Tags | **4** (v4.0.0 / -cost / -finance / -alert-data) |

---

## 🏷 Related Tags

```bash
git checkout v4.0.0            # 完整 V4 大版本
git checkout v4.0.0-cost       # 仅 V4.0 成本引擎
git checkout v4.0.0-finance    # 仅 V4.2 财务模块
git checkout v4.0.0-alert-data # 仅 V4.3 预警数据层
```

---

## 🙏 Contributors

- **PMO Bot** (`pmo-bot@local`) — V4 主交付
- **liaozhg** (`18069961@qq.com`) — 项目 owner

---

## 📜 License

Private repository · All rights reserved.

---

> **Full Changelog**: [CHANGELOG.md](./CHANGELOG.md)
> **Project README**: [README.md](./README.md)
> **成本引擎设计**: [docs/zhiyu-cost-engine.md](./docs/zhiyu-cost-engine.md)
> **PRD 成本控制**: [docs/PRD-cost-control.md](./docs/PRD-cost-control.md)