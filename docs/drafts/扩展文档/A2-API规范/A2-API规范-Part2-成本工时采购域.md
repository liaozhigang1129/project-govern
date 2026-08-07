# A2 OpenAPI 3.0 规范 Part2 — 成本/工时/风险/变更/缺陷/文档/流程/报表/Webhook

> 本 Part 涵盖 A2.4 ~ A2.9 全部剩余域。
> 文件命名建议：`zhiyu-api-v1.yaml`（与 Part1 合并为单一规范）。

---

## A2.4 成本、预算、工时、采购域

### A2.4.1 端点总览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| **预算** | | |
| GET | `/projects/{id}/budgets` | 项目预算列表 |
| POST | `/projects/{id}/budgets` | 新建预算 |
| GET | `/budgets/{id}` | 预算详情 |
| PATCH | `/budgets/{id}` | 更新 |
| DELETE | `/budgets/{id}` | 删除（仅 DRAFT） |
| POST | `/budgets/{id}/submit` | 提交审批 |
| POST | `/budgets/{id}/approve` | 审批通过 |
| POST | `/budgets/{id}/reject` | 驳回 |
| POST | `/budgets/{id}/close` | 关闭 |
| GET | `/budgets/{id}/versions` | 版本历史 |
| POST | `/budgets/{id}/versions` | 创建新版本 |
| POST | `/budgets/{id}/adjust` | 预算调整（关联变更单） |
| GET | `/budgets/{id}/consumption` | 占用/实际/剩余 |
| **工时** | | |
| GET | `/time-entries` | 工时列表（按人/项目/日期范围） |
| POST | `/time-entries` | 新建（提交） |
| POST | `/time-entries/bulk` | 批量提交 |
| PATCH | `/time-entries/{id}` | 修改（锁定前） |
| DELETE | `/time-entries/{id}` | 撤回 |
| POST | `/time-entries/{id}/submit` | 提交审批 |
| POST | `/time-entries/{id}/approve` | 审批 |
| POST | `/time-entries/{id}/reject` | 驳回 |
| POST | `/time-entries/{id}/lock` | 锁定周期 |
| POST | `/time-entries/{id}/unlock` | 解锁（需走流程） |
| GET | `/time-entries/timesheet?userId=...&from=...&to=...` | 周/月报视图 |
| GET | `/time-entries/summary?dimension=user,project&from=...&to=...` | 汇总 |
| **报销** | | |
| GET | `/expense-reports` | 报销单列表 |
| POST | `/expense-reports` | 新建 |
| PATCH | `/expense-reports/{id}` | 编辑 |
| POST | `/expense-reports/{id}/submit` | 提交 |
| POST | `/expense-reports/{id}/approve` | 审批 |
| POST | `/expense-reports/{id}/pay` | 标记已付 |
| POST | `/expense-reports/{id}/withdraw` | 撤回 |
| **采购** | | |
| GET | `/procurements` | PR/PO/合同列表 |
| POST | `/procurements` | 新建 |
| PATCH | `/procurements/{id}` | 更新 |
| POST | `/procurements/{id}/submit` | 提交 |
| POST | `/procurements/{id}/approve` | 审批 |
| GET | `/procurements/{id}/milestones` | 付款节点 |
| POST | `/procurements/{id}/milestones` | 新增节点 |
| PATCH | `/procurements/{id}/milestones/{msId}` | 更新 |
| POST | `/procurements/{id}/milestones/{msId}/pay` | 节点付款 |
| GET | `/vendors` | 供应商列表 |
| POST | `/vendors` | 新建供应商 |
| PATCH | `/vendors/{id}` | 更新 |
| **EVM** | | |
| GET | `/projects/{id}/evm?from=...&to=...` | 挣值趋势 |
| GET | `/projects/{id}/evm/latest` | 最新快照 |

### A2.4.2 核心 Schema

```yaml
Budget:
  type: object
  properties:
    id: { type: string }
    projectId: { type: string }
    wbsId: { type: string, nullable: true }
    name: { type: string }
    category: { type: string, enum: [LABOR, OUTSOURCE, EQUIPMENT, TRAVEL, TRAINING, CONSULT, CONTINGENCY, TAX, OTHER] }
    currency: { type: string }
    plannedAmount: { type: number }
    committedAmount: { type: number }
    actualAmount: { type: number }
    remainingAmount: { type: number }
    controlStrategy: { type: string, enum: [HARD, SOFT] }
    alertThreshold: { type: number, format: float, minimum: 0, maximum: 1 }
    status: { type: string, enum: [DRAFT, PENDING, APPROVED, REJECTED, CLOSED] }
    ownerId: { type: string }
    periodStart: { type: string, format: date, nullable: true }
    periodEnd: { type: string, format: date, nullable: true }

TimeEntry:
  type: object
  required: [userId, projectId, workDate, hours]
  properties:
    id: { type: string }
    userId: { type: string }
    workDate: { type: string, format: date }
    projectId: { type: string }
    wbsId: { type: string, nullable: true }
    workItemId: { type: string, nullable: true }
    hours: { type: number, format: float, minimum: 0.25, maximum: 24 }
    overtime: { type: boolean }
    leaveType: { type: string, enum: [ANNUAL, SICK, PERSONAL, MATERNITY, PARENTAL, UNPAID, OTHER], nullable: true }
    description: { type: string, maxLength: 1000 }
    status: { type: string, enum: [DRAFT, SUBMITTED, APPROVED, REJECTED, LOCKED] }
    approverId: { type: string, nullable: true }
    billingRate: { type: number, nullable: true }
    costAmount: { type: number, nullable: true }
    source: { type: string, enum: [WEB, MOBILE, API, IM, EMAIL, IMPORT] }

ExpenseReport:
  type: object
  properties:
    id: { type: string }
    reportNo: { type: string }
    applicantId: { type: string }
    projectId: { type: string }
    wbsId: { type: string, nullable: true }
    category: { type: string, enum: [TRAVEL, MEAL, ACCOMMODATION, SUPPLIES, TRAINING, OTHER] }
    amount: { type: number }
    currency: { type: string }
    expenseDate: { type: string, format: date }
    status: { type: string, enum: [DRAFT, SUBMITTED, IN_REVIEW, APPROVED, REJECTED, PAID, CLOSED] }
    lines:
      type: array
      items: { $ref: '#/components/schemas/ExpenseLine' }

Procurement:
  type: object
  properties:
    id: { type: string }
    docType: { type: string, enum: [PR, PO, CONTRACT, SOW] }
    docNo: { type: string }
    projectId: { type: string }
    vendorId: { type: string }
    title: { type: string }
    amount: { type: number }
    currency: { type: string }
    status: { type: string, enum: [DRAFT, IN_REVIEW, APPROVED, REJECTED, ACTIVE, CLOSED, CANCELLED] }
    startDate: { type: string, format: date, nullable: true }
    endDate: { type: string, format: date, nullable: true }
    milestones:
      type: array
      items: { $ref: '#/components/schemas/ProcurementMilestone' }

EvmSnapshot:
  type: object
  properties:
    projectId: { type: string }
    snapshotDate: { type: string, format: date }
    pv: { type: number }
    ev: { type: number }
    ac: { type: number }
    bac: { type: number }
    sv: { type: number }
    cv: { type: number }
    spi: { type: number }
    cpi: { type: number }
    vac: { type: number }
    eac: { type: number }
    tcpi: { type: number }
```

### A2.4.3 关键操作示例

**批量提交工时（移动端常用）**
```yaml
POST /time-entries/bulk
Request:
  entries:
    - { workDate: "2025-04-14", projectId: "p-001", workItemId: "wi-100", hours: 4, description: "接口开发" }
    - { workDate: "2025-04-14", projectId: "p-001", workItemId: "wi-101", hours: 3, description: "单元测试" }
    - { workDate: "2025-04-14", projectId: "p-002", workItemId: "wi-200", hours: 1, description: "需求评审" }
Response 207:
  results:
    - { index: 0, status: 201, id: te-001 }
    - { index: 1, status: 201, id: te-002 }
    - { index: 2, status: 422, error: { code: PMS_BUSINESS_RULE_VIOLATED, message: "工时已超日上限 12h" } }
```

**工时汇总（按维度）**
```yaml
GET /time-entries/summary?dimension=user,project&from=2025-04-01&to=2025-04-30&projectId=p-001
Response 200:
  {
    "totals": { "hours": 1280, "cost": 1920000, "currency": "CNY" },
    "rows": [
      { "userId": "u-301", "projectId": "p-001", "hours": 160, "cost": 240000 },
      { "userId": "u-302", "projectId": "p-001", "hours": 168, "cost": 252000 }
    ]
  }
```

**预算占用查询（用于 UI 进度条）**
```yaml
GET /budgets/b-001/consumption
Response 200:
  {
    "planned": 5000000, "committed": 1200000, "actual": 800000, "remaining": 3000000,
    "utilization": 0.40, "alertLevel": "GREEN", "controlStrategy": "HARD"
  }
```

**EVM 趋势**
```yaml
GET /projects/p-001/evm?from=2025-01-01&to=2025-04-30
Response 200:
  {
    "projectId": "p-001",
    "series": [
      { "date": "2025-01-31", "pv": 500000, "ev": 480000, "ac": 510000, "spi": 0.96, "cpi": 0.94 },
      { "date": "2025-02-28", "pv": 1100000, "ev": 1000000, "ac": 1080000, "spi": 0.91, "cpi": 0.93 },
      { "date": "2025-03-31", "pv": 1800000, "ev": 1700000, "ac": 1750000, "spi": 0.94, "cpi": 0.97 }
    ],
    "latest": { "spi": 0.94, "cpi": 0.97, "eac": 5154000, "vac": -154000 }
  }
```

### A2.4.4 业务规则（入参校验）

- `hours`：单条 0.25 ≤ h ≤ 24；同一人同日累计 ≤ 24（系统可配 12 软上限）；
- `workDate`：不允许 > 今天 +7；不允许在锁定周期内；
- `projectId` 必填；`workItemId` 与 `wbsId` 至少一个（按租户配置强制）；
- 工时 → 预算占用：硬控制下若预算 remaining < hours，返回 422 `PMS_BUDGET_EXCEEDED`；
- 报销金额 ≤ 预算 remaining，否则软控制返回警告但允许；
- 预算关闭后不允许新增占用/实际；
- 跨币种：按 ERP 实时汇率换算到项目主币种，存储 `cost_amount`。

---
