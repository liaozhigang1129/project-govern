# A1 数据字典 Part4 — 风险、问题、变更、缺陷、评审、审计

> 本节覆盖 A1.5 风险/质量域全部表。

## A1.5 风险、问题、变更、缺陷、评审、审计域

### A1.5.1 risks（风险登记册）

| 字段 | 类型 | 必填 | 枚举/约束 | 说明 |
| --- | --- | --- | --- | --- |
| id | S(32) | ✅ | PK | |
| project_id | Ref | ✅ | | |
| code | S(40) | ✅ | UNIQUE(tenant_id, code) | 风险编号 |
| title | S(200) | ✅ | | |
| description | Txt | — | | |
| category | E | ✅ | TECH, RESOURCE, SCHEDULE, COST, SCOPE, QUALITY, COMPLIANCE, EXTERNAL, OTHER | 风险类别 |
| source | E | ✅ | INTERNAL, EXTERNAL | 风险来源 |
| probability | I(1-5) | ✅ | | 概率 P |
| impact | I(1-5) | ✅ | | 影响 I |
| score | I | ✅ | =P*I | 风险值 |
| level | E | ✅ | LOW, MEDIUM, HIGH, EXTREME | 等级（按阈值自动） |
| trigger_condition | Txt | — | | 触发条件 |
| owner_id | Ref | ✅ | | 风险责任人 |
| strategy | E | ✅ | AVOID, MITIGATE, TRANSFER, ACCEPT, EXPLOIT, SHARE | 应对策略 |
| status | E | ✅ | IDENTIFIED, ANALYZING, RESPONDING, CLOSED, MATERIALIZED | 状态 |
| occurred_at | DT | — | | 风险事件化时间 |
| closed_at | DT | — | | |
| close_reason | Txt | — | | |
| next_review_at | D | — | | 下次复核 |
| lessons_learned | Txt | — | | 经验教训 |
| tags | Arr<S> | — | | |
| linked_items | Arr<Ref> | — | | 关联任务/变更/问题 |

**索引**：
- `idx_risk_project_status (project_id, status)`
- `idx_risk_level (level, status)`
- `idx_risk_owner (owner_id, status)`
- `idx_risk_review (next_review_at)`

### A1.5.2 risk_responses（风险应对措施）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| risk_id | Ref | ✅ | |
| action | Txt | ✅ | 应对措施 |
| type | E | ✅ | PREVENTIVE, CONTINGENT |
| owner_id | Ref | ✅ | 责任人 |
| due_date | D | ✅ | |
| status | E | ✅ | TODO, IN_PROGRESS, DONE, OVERDUE, CANCELLED |
| cost_estimate | M | — | 预计费用 |
| effectiveness | E | — | HIGH, MEDIUM, LOW |
| closed_at | DT | — | |
| work_item_id | Ref | — | 转任务跟踪 |

**唯一**：`uniq_rr (risk_id, action)`。

### A1.5.3 risk_reviews（风险复评记录）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| risk_id | Ref | ✅ | |
| reviewed_at | DT | ✅ | |
| reviewer_id | Ref | ✅ | |
| old_p/old_i | I | ✅ | 复评前 |
| new_p/new_i | I | ✅ | 复评后 |
| new_level | E | ✅ | |
| comment | Txt | — | |
| trigger | E | — | PERIODIC, EVENT, GATE |

### A1.5.4 issues（问题）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| project_id | Ref | ✅ | |
| code | S(40) | ✅ | |
| title | S(200) | ✅ | |
| description | Txt | — | |
| type | E | ✅ | BLOCKER, MAJOR, MINOR, INFO |
| category | E | ✅ | 同 risks.category |
| severity | E | ✅ | S1, S2, S3, S4 |
| priority | E | ✅ | P0, P1, P2, P3 |
| owner_id | Ref | ✅ | |
| discovered_at | DT | ✅ | |
| discovered_by | Ref | ✅ | |
| root_cause | E | — | PEOPLE, PROCESS, TECH, EXTERNAL, OTHER |
| root_cause_detail | Txt | — | |
| impact_scope | Txt | — | |
| solution | Txt | — | |
| sla_due | DT | — | |
| status | E | ✅ | OPEN, IN_PROGRESS, RESOLVED, VERIFIED, CLOSED, REOPENED |
| resolved_at | DT | — | |
| verified_at | DT | — | |
| closed_at | DT | — | |
| converted_to_risk_id | Ref | — | 问题→风险 |
| linked_work_item_id | Ref | — | |
| linked_change_id | Ref | — | |

**索引**：
- `idx_issue_project_status (project_id, status)`
- `idx_issue_owner (owner_id, status)`
- `idx_issue_sla (sla_due, status)`

### A1.5.5 change_requests（变更申请单）

| 字段 | 类型 | 必填 | 枚举/约束 | 说明 |
| --- | --- | --- | --- | --- |
| id | S(32) | ✅ | | |
| project_id | Ref | ✅ | | |
| code | S(40) | ✅ | UNIQUE | |
| title | S(200) | ✅ | | |
| type | E | ✅ | SCOPE, SCHEDULE, COST, RESOURCE, QUALITY, BASELINE, CONTRACT, OTHER | |
| priority | E | ✅ | LOW, MEDIUM, HIGH, CRITICAL | |
| impact_level | E | ✅ | MINOR, MAJOR, CRITICAL | 走不同审批链 |
| reason | Txt | ✅ | | 变更原因 |
| description | Txt | ✅ | | 详细描述 |
| impact_scope | Txt | — | | 范围影响 |
| impact_schedule_days | I | — | | 进度影响（天） |
| impact_cost | M | — | | 成本影响 |
| impact_resource | Txt | — | | 资源影响 |
| impact_quality | Txt | — | | 质量影响 |
| impact_risk | Txt | — | | 风险影响 |
| proposed_solution | Txt | ✅ | | 提议方案 |
| alternatives | Txt | — | | 备选方案 |
| status | E | ✅ | DRAFT, SUBMITTED, IN_REVIEW, APPROVED, REJECTED, IMPLEMENTING, COMPLETED, CANCELLED | |
| submitted_by | Ref | ✅ | | |
| submitted_at | DT | ✅ | | |
| ccb_meeting_at | DT | — | | CCB 会议时间 |
| decision | E | — | APPROVED, REJECTED, DEFERRED | |
| decision_at | DT | — | | |
| decision_comment | Txt | — | | |
| effective_date | D | — | | 生效日期 |
| baseline_id | Ref | — | | 影响的基线 |
| new_baseline_id | Ref | — | | 变更后基线 |
| is_emergency | B | ✅ | false | 紧急变更 |

**索引**：
- `idx_cr_project_status (project_id, status)`
- `idx_cr_type (type)`
- `idx_cr_impact (impact_level)`

### A1.5.6 change_request_items（变更关联项）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| change_id | Ref | ✅ | |
| item_type | E | ✅ | WBS, TASK, MILESTONE, BUDGET, RESOURCE, DELIVERABLE, RISK |
| item_id | S(32) | ✅ | |
| change_type | E | ✅ | ADD, MODIFY, REMOVE |
| before | J | — | 变更前值 |
| after | J | — | 变更后值 |

### A1.5.7 defects（缺陷）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| project_id | Ref | ✅ | |
| code | S(40) | ✅ | |
| title | S(500) | ✅ | |
| description | Txt | ✅ | 富文本 |
| steps_to_reproduce | Txt | — | |
| expected | Txt | — | |
| actual | Txt | — | |
| severity | E | ✅ | S1, S2, S3, S4 |
| priority | E | ✅ | P0, P1, P2, P3 |
| type | E | ✅ | FUNCTIONAL, PERFORMANCE, SECURITY, UI, COMPATIBILITY, DATA, OTHER |
| environment | E | — | DEV, TEST, STAGING, PROD |
| module | S(100) | — | |
| version | S(50) | — | 影响的版本 |
| fixed_version | S(50) | — | |
| reporter_id | Ref | ✅ | |
| assignee_id | Ref | — | |
| status | E | ✅ | NEW, ASSIGNED, IN_PROGRESS, FIXED, VERIFIED, CLOSED, REOPENED, WONT_FIX |
| resolution | E | — | FIXED, WONT_FIX, DUPLICATE, CANNOT_REPRODUCE, BY_DESIGN |
| sla_response_due | DT | — | |
| sla_fix_due | DT | — | |
| responded_at | DT | — | |
| fixed_at | DT | — | |
| verified_at | DT | — | |
| closed_at | DT | — | |
| root_cause | E | — | |
| work_item_id | Ref | — | 关联任务 |
| release_id | Ref | — | 关联版本/发布 |
| source | E | ✅ | MANUAL, IM, EMAIL, ALM_SYNC, AUTO_TEST |
| external_id | S(128) | — | ALM 同步 ID |
| alm_system | S(50) | — | 同步源系统 |

**索引**：
- `idx_def_project_status (project_id, status)`
- `idx_def_severity (severity, status)`
- `idx_def_sla (sla_fix_due, status)`
- `uniq_def_external (alm_system, external_id)` 部分唯一

### A1.5.8 reviews（评审）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| project_id | Ref | ✅ | |
| type | E | ✅ | CHARTER, STAGE_GATE, DESIGN, CODE, ACCEPTANCE, CLOSING, OTHER |
| name | S(200) | ✅ | |
| planned_at | DT | ✅ | |
| actual_at | DT | — | |
| location | S(200) | — | 线下/线上链接 |
| chair_id | Ref | ✅ | 主持人 |
| reviewer_ids | Arr<Ref> | ✅ | |
| materials | Arr<Ref> | — | 评审材料 |
| criteria | J | ✅ | 评审标准（多维度） |
| status | E | ✅ | PLANNED, IN_PROGRESS, COMPLETED, CANCELLED |
| decision | E | — | PASS, CONDITIONAL, FAIL, DEFERRED |
| decision_at | DT | — | |
| summary | Txt | — | 评审纪要 |
| from_phase | S(50) | — | |
| to_phase | S(50) | — | 阶段门专用 |

### A1.5.9 review_scores（评审打分）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| review_id | Ref | ✅ | |
| reviewer_id | Ref | ✅ | |
| dimension | S(50) | ✅ | 维度名（自定义） |
| score | D | ✅ | 1-10 |
| comment | Txt | — | |
| concerns | Txt | — | 关注点/反对意见 |
| decision | E | — | APPROVE, REJECT, ABSTAIN |

**唯一**：`uniq_rs (review_id, reviewer_id, dimension)`。

### A1.5.10 action_items（行动项/评审输出）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| review_id | Ref | — | 评审产出 |
| source_type | E | — | REVIEW, MEETING, RETRO, AUDIT, OTHER |
| source_id | Ref | — | |
| title | S(500) | ✅ | |
| description | Txt | — | |
| owner_id | Ref | ✅ | |
| due_date | D | ✅ | |
| priority | E | ✅ | |
| status | E | ✅ | OPEN, IN_PROGRESS, DONE, VERIFIED, CANCELLED, OVERDUE |
| work_item_id | Ref | — | 转任务 |
| verified_by | Ref | — | |
| verified_at | DT | — | |
| closed_at | DT | — | |

### A1.5.11 audit_logs（操作日志）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(64) | ✅ | UUIDv7 |
| tenant_id | S(32) | ✅ | |
| user_id | Ref | ✅ | 操作人 |
| user_name | S(100) | ✅ | 冗余 |
| action | E | ✅ | CREATE, READ, UPDATE, DELETE, LOGIN, LOGOUT, EXPORT, PRINT, APPROVE, REJECT, ESCALATE, ASSIGN, ... |
| target_type | E | ✅ | PROJECT, TASK, RISK, ISSUE, BUDGET, ... |
| target_id | S(32) | ✅ | |
| target_summary | S(500) | — | 冗余（用于列表展示） |
| before | J | — | 变更前 |
| after | J | — | 变更后 |
| diff | J | — | 字段级差异 |
| ip | S(64) | ✅ | |
| user_agent | S(500) | — | |
| geo | S(100) | — | 国家/城市 |
| device | E | — | WEB, MOBILE, API, IM, BOT |
| request_id | S(64) | ✅ | 链路追踪 |
| occurred_at | DT | ✅ | 毫秒级 |
| sensitive | B | ✅ | false |
| reason | Txt | — | 必填原因（敏感操作时） |

**索引**：
- `idx_audit_tenant_time (tenant_id, occurred_at DESC)`
- `idx_audit_user (user_id, occurred_at DESC)`
- `idx_audit_target (target_type, target_id, occurred_at DESC)`
- `idx_audit_sensitive (sensitive, occurred_at)`

**保留策略**：≥ 6 年（按行业合规）；分区按月。

---
