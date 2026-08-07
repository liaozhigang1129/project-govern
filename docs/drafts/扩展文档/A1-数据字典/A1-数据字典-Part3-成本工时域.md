# A1 数据字典 Part3 — 成本、工时、采购域

> 承接 Part1/Part2。本节覆盖 A1.4 成本域全部表。

## A1.4 成本、工时、采购域

### A1.4.1 budgets（预算主表）

| 字段 | 类型 | 必填 | 枚举/约束 | 说明 |
| --- | --- | --- | --- | --- |
| id | S(32) | ✅ | PK | |
| project_id | Ref | ✅ | FK→projects | |
| wbs_id | Ref | — | FK→wbs_nodes | 节点级预算 |
| phase | S(50) | — | | 阶段（启动/规划/执行/收尾） |
| name | S(200) | ✅ | | |
| category | E | ✅ | LABOR, OUTSOURCE, EQUIPMENT, TRAVEL, TRAINING, CONSULT, CONTINGENCY, TAX, OTHER | 类别 |
| currency | S(3) | ✅ | ISO 4217 | |
| planned_amount | M | ✅ | >=0 | 计划金额 |
| committed_amount | M | ✅ | default 0 | 占用金额（已申请未付） |
| actual_amount | M | ✅ | default 0 | 实际金额 |
| remaining_amount | M | ✅ | default 0 | 剩余（=planned-committed-actual） |
| control_strategy | E | ✅ | HARD, SOFT | 硬/软控制 |
| alert_threshold | D(0-1) | ✅ | default 0.8 | 预警阈值（80%） |
| status | E | ✅ | DRAFT, PENDING, APPROVED, REJECTED, CLOSED | |
| owner_id | Ref | ✅ | | |
| period_start | D | — | | 预算期间 |
| period_end | D | — | | |
| notes | Txt | — | | |

**索引**：
- `idx_budget_project (project_id)`
- `idx_budget_category (project_id, category)`
- `idx_budget_status (status)`

### A1.4.2 budget_versions（预算版本）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| budget_id | Ref | ✅ | |
| version | S(20) | ✅ | V0.1 / V1.0 |
| snapshot | J | ✅ | 完整预算快照 |
| change_reason | Txt | — | |
| related_change_id | Ref | — | 关联变更单 |
| status | E | ✅ | DRAFT, IN_REVIEW, APPROVED, SUPERSEDED |
| approved_by | Ref | — | |
| approved_at | DT | — | |
| is_current | B | ✅ | false |

**唯一**：`uniq_bv (budget_id, version)`。

### A1.4.3 budget_lines（预算明细行）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| budget_id | Ref | ✅ | |
| name | S(200) | ✅ | |
| cost_type | E | ✅ | 同 budget.category |
| planned_amount | M | ✅ | |
| planned_quantity | D | — | |
| unit | S(20) | — | 人天/件/项 |
| unit_price | M | — | |
| notes | Txt | — | |

### A1.4.4 budget_approvals（预算审批流）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| budget_id | Ref | ✅ | |
| workflow_instance_id | Ref | — | 关联流程实例 |
| step | I | ✅ | |
| approver_id | Ref | ✅ | |
| decision | E | — | APPROVED, REJECTED, RETURNED |
| comment | Txt | — | |
| decided_at | DT | — | |
| amount_at_decision | M | ✅ | 决策时金额 |

### A1.4.5 time_entries（工时单）

| 字段 | 类型 | 必填 | 枚举/约束 | 说明 |
| --- | --- | --- | --- | --- |
| id | S(32) | ✅ | PK | |
| tenant_id | S(32) | ✅ | | |
| user_id | Ref | ✅ | | |
| work_date | D | ✅ | | 工作日 |
| project_id | Ref | ✅ | FK→projects | |
| wbs_id | Ref | — | | |
| work_item_id | Ref | — | FK→work_items | |
| hours | D | ✅ | >0, <=24 | 时长 |
| overtime | B | ✅ | false | 加班 |
| leave_type | E | — | ANNUAL, SICK, ... | 请假时填 |
| description | Txt | — | | 描述 |
| status | E | ✅ | DRAFT, SUBMITTED, APPROVED, REJECTED, LOCKED | |
| approver_id | Ref | — | | |
| approved_at | DT | — | | |
| period_locked | B | ✅ | false | 周期已锁定 |
| source | E | ✅ | WEB, MOBILE, API, IM, EMAIL, IMPORT | |
| external_id | S(128) | — | | |
| billing_rate | M | — | | 当时费率 |
| cost_amount | M | — | | 当时成本（=hours*rate） |

**索引**：
- `idx_te_user_date (user_id, work_date)`
- `idx_te_project_date (project_id, work_date)`
- `idx_te_workitem (work_item_id)`
- `idx_te_status (status, work_date)`
- `uniq_te_external (source, external_id)` 部分唯一

**业务规则**：
- 同一人同日跨项目总时长 ≤ 24h（系统可配 12h 软限制）；
- 工时单必填 `project_id` 和 `work_item_id`/`wbs_id` 之一；
- 周期锁定后不允许修改，需走"解锁"审批。

### A1.4.6 time_entry_approvals（工时审批）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| time_entry_id | Ref | ✅ | |
| approver_id | Ref | ✅ | |
| decision | E | — | APPROVED, REJECTED, RETURNED |
| comment | Txt | — | |
| decided_at | DT | — | |

### A1.4.7 expense_reports（报销单）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| tenant_id | S(32) | ✅ | |
| report_no | S(40) | ✅ | UNIQUE |
| applicant_id | Ref | ✅ | |
| project_id | Ref | ✅ | |
| wbs_id | Ref | — | |
| category | E | ✅ | TRAVEL, MEAL, ACCOMMODATION, SUPPLIES, TRAINING, OTHER |
| amount | M | ✅ | |
| currency | S(3) | ✅ | |
| expense_date | D | ✅ | |
| description | Txt | — | |
| attachments | Arr<Ref> | — | 发票等 |
| status | E | ✅ | DRAFT, SUBMITTED, IN_REVIEW, APPROVED, REJECTED, PAID, CLOSED |
| workflow_instance_id | Ref | — | |
| paid_at | DT | — | |
| paid_by | Ref | — | |
| erp_doc_no | S(100) | — | ERP 单据号 |

### A1.4.8 expense_lines（报销明细行）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| report_id | Ref | ✅ | |
| category | E | ✅ | |
| amount | M | ✅ | |
| tax_amount | M | — | |
| invoice_no | S(80) | — | |
| invoice_file | Ref | — | 附件 |
| remark | Txt | — | |

### A1.4.9 procurements（采购单/PR/PO/合同）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| tenant_id | S(32) | ✅ | |
| doc_type | E | ✅ | PR, PO, CONTRACT, SOW |
| doc_no | S(40) | ✅ | UNIQUE |
| project_id | Ref | ✅ | |
| vendor_id | Ref | ✅ | FK→vendors |
| title | S(200) | ✅ | |
| amount | M | ✅ | |
| currency | S(3) | ✅ | |
| status | E | ✅ | DRAFT, IN_REVIEW, APPROVED, REJECTED, ACTIVE, CLOSED, CANCELLED |
| start_date | D | — | |
| end_date | D | — | |
| owner_id | Ref | ✅ | |
| approver_id | Ref | — | |
| workflow_instance_id | Ref | — | |
| erp_doc_no | S(100) | — | |
| attachments | Arr<Ref> | — | |
| terms | Txt | — | |

### A1.4.10 procurement_milestones（采购里程碑/付款节点）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| procurement_id | Ref | ✅ | |
| name | S(200) | ✅ | |
| amount | M | ✅ | |
| plan_date | D | ✅ | |
| actual_date | D | — | |
| status | E | ✅ | PENDING, ACHIEVED, PAID, OVERDUE |
| deliverable_id | Ref | — | 关联交付物 |

### A1.4.11 vendors（供应商/承包商）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| tenant_id | S(32) | ✅ | |
| code | S(50) | ✅ | UNIQUE |
| name | S(200) | ✅ | |
| type | E | ✅ | SUPPLIER, CONTRACTOR, CONSULTANT, OUTSOURCER |
| tax_no | S(80) | — | |
| bank_account | S(100) | — | 加密 |
| contact | J | — | JSON：name, phone, email, address |
| status | E | ✅ | ACTIVE, BLOCKED, ARCHIVED |
| qualification | J | — | 资质 JSON |
| external_id | S(128) | — | ERP 主数据 ID |

### A1.4.12 cost_actuals（实际成本归集，来源 ERP）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| project_id | Ref | ✅ | |
| wbs_id | Ref | — | |
| cost_type | E | ✅ | |
| amount | M | ✅ | |
| currency | S(3) | ✅ | |
| occurred_at | D | ✅ | |
| source | E | ✅ | TIME, EXPENSE, PROCUREMENT, ERP_SYNC, OTHER |
| source_doc_id | S(128) | — | 来源单据 |
| cost_center | S(40) | — | |
| posted_at | DT | — | 过账时间 |
| period | S(7) | ✅ | YYYY-MM |

**索引**：
- `idx_ca_project_period (project_id, period)`
- `idx_ca_type (project_id, cost_type, period)`

### A1.4.13 evm_metrics（挣值度量快照）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| project_id | Ref | ✅ | |
| snapshot_date | D | ✅ | |
| pv | M | ✅ | 计划值 |
| ev | M | ✅ | 挣值 |
| ac | M | ✅ | 实际成本 |
| bac | M | ✅ | 完工预算 |
| sv | M | ✅ | ev - pv |
| cv | M | ✅ | ev - ac |
| spi | D | ✅ | ev / pv |
| cpi | D | ✅ | ev / ac |
| vac | M | ✅ | bac - eac |
| eac | M | ✅ | bac / cpi |
| tcpi | D | ✅ | (bac - ev) / (bac - ac) |
| schedule_variance_pct | D | ✅ | |
| cost_variance_pct | D | ✅ | |
| computed_at | DT | ✅ | |

**唯一**：`uniq_evm (project_id, snapshot_date)`。

---
