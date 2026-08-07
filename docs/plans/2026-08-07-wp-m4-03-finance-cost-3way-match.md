---
status: active
created: 2026-08-07
updated: 2026-08-07
summary: WP-M4-03 财务-成本 3-way match 对账实现计划
---

# Plan · WP-M4-03 财务-成本对账

> 对应 WBS 工作包:[`WP-M4-03`](../WBS.md#wp-m4-03-财务-成本对账)
> 对应里程碑:**M4**(成本引擎 / 财务 3-way match)
> 对应 ADR:—
> 当前状态:**active**(2026-08-07 启动)
> 阻塞项:R-001 对账口径未对齐(见 [STATUS.md §4](../STATUS.md))

---

## 1. 目标与范围

### 1.1 一句话

实现 `合同 ↔ 发票 ↔ 付款 ↔ 成本项` 的 3-way match 闭环,**任一对账维度不一致即产生差异告警**,PMO 在 `CostDashboard` 一眼能看到对账健康度。

### 1.2 范围内

- 数据模型:`cost_reconciliation` 表 + 字段(contract_id / invoice_id / payment_id / cost_item_id / match_status / diff_amount / diff_reason / reconciled_at / reconciled_by)
- 实时对账触发:发票入账 / 付款确认 / 成本月结三个时机任一发生即跑对账
- 对账服务:`ReconciliationService.reconcile()` 三向匹配,返回 `MatchResult`
- 差异告警:`alert_rules` 新增 `cost_diff` 类型,差异金额 > 阈值(默认 ¥100)即触发
- API:`GET /api/finance/reconciliation?projectId=&status=&from=&to=` 列表;`POST /api/finance/reconciliation/retry/{id}` 重跑单条
- 前端:`CostDashboard` 加对账健康度卡片(绿灯率 / 差异总额 / 待处理条数)

### 1.3 出范围

- 跨币种对账(留 M7 多币种 / 多租户)
- 银行流水自动对账(留 v5 立项评估)
- 财务-ERP 对接(留外部集成评估)

---

## 2. 实现步骤

> 顺序执行,每步独立 commit。

### T-01 数据模型 + Flyway 迁移

- 新建 `V5.0__cost_reconciliation.sql`(`migration-pg` 与 `migration-mysql` 各一份)
- 字段:`id / project_id / contract_id / invoice_id / payment_id / cost_item_id / match_status(MATCHED|PARTIAL|MISMATCH|PENDING) / diff_amount(默认 0) / diff_reason(TEXT) / reconciled_at / reconciled_by / created_at / updated_at`
- 索引:`idx_project_status (project_id, match_status)`,`idx_reconciled_at (reconciled_at DESC)`
- 实体:`com.hex.projectgovern.module.finance.CostReconciliation` + Repository
- 验证:`mvn -B compile` + `make docs-lint`

### T-02 对账服务核心

- `ReconciliationService.reconcileByProject(projectId)`:
  - 拉项目下所有 `contract` / `invoice` / `payment` / `cost_item`
  - 三向匹配算法:按 `project_id` + `amount` 容差(默认 ¥0.01)JOIN
  - 输出 `MatchResult { matched: [...], partial: [...], mismatch: [...], pending: [...] }`
  - 写 `cost_reconciliation` 表(状态 `MATCHED` / `PARTIAL` / `MISMATCH`)
- `diff_reason` 自动生成(算法选最匹配的差异解释模板)
- 单测:`ReconciliationServiceTest` 覆盖 4 种状态至少各 2 case
- 验证:`mvn -B test` 全绿

### T-03 触发钩子

- `InvoiceService.confirm()` 入账成功 → `eventPublisher.publishEvent(new InvoiceConfirmedEvent(invoiceId))`
- `PaymentService.confirm()` 付款确认 → `PaymentConfirmedEvent`
- `CostItemService.monthlySettle()` 月结 → `CostMonthlySettledEvent(projectId, period)`
- `ReconciliationEventListener` 监听三类事件 → 调用 `ReconciliationService.reconcileByProject(...)`
- 失败隔离:`@Async` + try-catch,失败仅 warn log,不回滚主业务
- 单测:`ReconciliationEventListenerTest` 验证三类事件触发对账
- 验证:`mvn -B test` 全绿

### T-04 告警规则

- `alert_rules` 表新增规则 `cost_diff > 100`:
  - `trigger_sql`:检测 `cost_reconciliation WHERE match_status='MISMATCH' AND diff_amount > 100 AND created_at > NOW() - INTERVAL '1 day'`
  - `notify_channel`:邮件 + IM
- `AlertRuleController` 加种子规则 POST 端点(幂等)
- 单测:`AlertRuleCostDiffTest` 验证阈值触发
- 验证:`make smoke`

### T-05 API

- `FinanceController`:
  - `GET /api/finance/reconciliation`(查询参数 `projectId` / `status` / `from` / `to`,分页 20)
  - `POST /api/finance/reconciliation/retry/{id}`(重新对账)
  - `GET /api/finance/reconciliation/health?projectId=`(健康度聚合:绿灯率 / 差异总额 / 待处理数)
- `@RequireRoles("PMO_ADMIN")` 仅 PMO_ADMIN / FINANCE 可读
- `@AuditLog` 写操作
- 单测:`FinanceReconciliationControllerTest` 4 case
- 验证:`make smoke` + OpenAPI 导出

### T-06 前端

- `frontend/src/views/CostDashboard.vue` 加 3 个卡片:
  - **对账健康度**:绿灯率 (matched / total)
  - **差异总额**:SUM(diff_amount)
  - **待处理**:COUNT WHERE status IN (PARTIAL, MISMATCH)
- `frontend/src/api/finance.ts` 新增 `reconcile` API
- 路由 + 菜单权限同步
- 验证:`pnpm build` + `pnpm exec vue-tsc --noEmit` 全绿

### T-07 E2E 冒烟

- `scripts/business-smoke.sh` 新增 7 步业务冒烟(创建合同 → 创建发票 → 创建付款 → 创建成本项 → 触发对账 → 验证对账结果 → 触发差异告警)
- 验证:`bash scripts/business-smoke.sh`

---

## 3. 验收标准

### 3.1 必过(挡道)

| 项 | 标准 |
|---|---|
| 后端单测 | `mvn -B test` 全绿,新增 `ReconciliationServiceTest` ≥ 8 case |
| CI 4 job | backend-test / backend-build / frontend-build / integration-smoke / **docs-lint** 5 个全绿 |
| 数据库迁移 | PG + MySQL 各一份 Flyway 迁移,CI 双轨跑通 |
| API 文档 | OpenAPI paths +5(列表 / 重试 / 健康度 / 告警规则 POST / ...) |

### 3.2 应过(不挡但记)

| 项 | 标准 |
|---|---|
| 业务冒烟 | `business-smoke.sh` 7 步全绿 |
| 前端构建 | `pnpm build` 无 warning |
| 覆盖率 | Jacoco 新增模块行覆盖 ≥ 70% |

### 3.3 不做

- 跨币种对账(留 M7)
- 银行流水对账(留 v5)
- 财务-ERP 对接(留评估)

---

## 4. 风险与缓解

| ID | 风险 | 缓解 | 负责人 |
|---|---|---|---|
| R-001 | 对账口径(金额容差 / 状态判定)未对齐 | T-01 前开一次 1h 口径评审会,产出"对账口径 v1.0" markdown | PMO + 财务代表 |
| R-004 | 大数据量性能(全公司项目 × 月度 × 三向匹配) | T-02 用 `EXPLAIN ANALYZE` 在 100k cost_item 量级下验证 P95 < 1s | 后端 |
| R-005 | 触发钩子重复触发(同一发票 5 分钟内收 3 个事件) | T-03 加幂等键 `(invoice_id + period + version)`,Redis 短时去重 | 后端 |

---

## 5. 进度节点(预估)

| 节点 | 日期 | 准入 |
|---|---|---|
| T-01 数据模型 | 2026-08-10 | mvn compile + docs-lint |
| T-02 对账服务 | 2026-08-14 | mvn test 全绿 |
| T-03 触发钩子 | 2026-08-17 | mvn test 全绿 |
| T-04 告警规则 | 2026-08-19 | smoke |
| T-05 API | 2026-08-21 | OpenAPI 导出 |
| T-06 前端 | 2026-08-25 | pnpm build |
| T-07 E2E | 2026-08-28 | business-smoke |
| **M4 门禁** | **2026-09-04** | 上述 7 步全绿 + 评审 |

---

## 6. 完成后处置

- WBS.md:本工作包 `Plan:` 字段填本文件名,验收标准已通过即可在 STATUS.md 标 ✅
- STATUS.md:更新 M4 进度 `85% → 100%`,风险 R-001 关闭,门禁移到 M5
- CHANGELOG.md:追加 `[M4.0]` 段,简述对账功能
- ADR:若对账口径算法产生方向性变化(比如改金额容差策略),追加 `004-*.md`
