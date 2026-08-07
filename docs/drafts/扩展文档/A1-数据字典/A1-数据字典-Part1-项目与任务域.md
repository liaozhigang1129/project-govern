# A1 数据字典（Data Dictionary）

> 与 SRS §14 数据模型对齐。命名约定：表名用复数蛇形，字段名用蛇形，主键 `id`（UUID/雪花/自增可选）。
> 类型简记：`S`=string, `I`=int, `L`=long, `B`=bool, `D`=date, `DT`=datetime, `T`=time, `M`=money(decimal 18,4), `J`=json, `E`=enum, `Txt`=text, `Ref`=外键, `Arr`=array。

## A1.0 通用字段

| 字段 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| id | S(32) | ✅ | uuid | 主键 |
| tenant_id | S(32) | ✅ | — | 租户隔离 |
| created_at | DT | ✅ | now() | 创建时间（UTC） |
| created_by | S(32) | ✅ | — | 创建人 |
| updated_at | DT | ✅ | on update | 更新时间 |
| updated_by | S(32) | ✅ | — | 更新人 |
| is_deleted | B | ✅ | false | 软删 |
| deleted_at | DT | — | — | 软删时间 |
| version | I | ✅ | 1 | 乐观锁版本号 |
| ext | J | — | {} | 扩展字段（按业务定义） |

## A1.1 项目域

### A1.1.1 projects（项目主表）

| 字段 | 类型 | 必填 | 枚举/约束 | 说明 |
| --- | --- | --- | --- | --- |
| id | S(32) | ✅ | PK | |
| code | S(40) | ✅ | UNIQUE(code, tenant_id, year) | 编号，如 `PRJ-BU1-25-0001` |
| name | S(200) | ✅ | | 项目名称 |
| type | E | ✅ | R&D, INFRA, MKT, COMPL, CONSULT, OTHER | 项目类型 |
| category | S(50) | — | | 子分类（自定义字典） |
| level | E | ✅ | S, A, B, C, D | 项目等级（按规模/战略） |
| secret_level | E | ✅ | PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED | 密级 |
| status | E | ✅ | DRAFT, PENDING, ACTIVE, SUSPENDED, CLOSING, CLOSED, ARCHIVED | 状态机 |
| health_score | I(0-100) | — | | 健康度 |
| rag_status | E | ✅ | GREEN, AMBER, RED, UNKNOWN | 灯 |
| business_unit_id | Ref | ✅ | FK→departments | 业务单元 |
| dept_id | Ref | ✅ | FK→departments | 主办部门 |
| sponsor_id | Ref | ✅ | FK→users | 业务负责人 |
| pm_id | Ref | ✅ | FK→users | 项目经理 |
| program_id | Ref | — | FK→programs | 所属项目集 |
| portfolio_id | Ref | — | FK→portfolios | 所属组合 |
| template_id | Ref | — | FK→project_templates | 立项模板 |
| start_date | D | ✅ | | 计划开始 |
| end_date | D | ✅ | > start_date | 计划结束 |
| actual_start | D | — | | 实际开始 |
| actual_end | D | — | | 实际结束 |
| baseline_id | Ref | — | FK→baselines | 当前基线 |
| currency | S(3) | ✅ | ISO 4217, default CNY | 主币种 |
| total_budget | M | — | >=0 | 主预算 |
| description | Txt | — | | 描述 |
| tags | Arr<S> | — | | 标签 |
| custom_fields | J | — | | 自定义字段 |
| is_archived | B | ✅ | false | 归档 |

**索引**：
- `idx_proj_tenant_status (tenant_id, status)`
- `idx_proj_pm (tenant_id, pm_id)`
- `idx_proj_program (program_id)`
- `idx_proj_dates (start_date, end_date)`
- `uniq_proj_code (tenant_id, code)`

### A1.1.2 programs（项目集）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| name | S(200) | ✅ | |
| owner_id | Ref | ✅ | 项目集经理 |
| strategy | Txt | — | 战略目标 |
| start_date/end_date | D | ✅ | |
| status | E | ✅ | PLANNING, ACTIVE, CLOSED |

### A1.1.3 portfolios（项目组合）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| name | S(200) | ✅ | |
| dimension | E | ✅ | BU, PRODUCT, REGION, CUSTOM | 维度 |
| owner_id | Ref | ✅ | |

### A1.1.4 project_members（项目成员）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| project_id | Ref | ✅ | |
| user_id | Ref | ✅ | |
| role | E | ✅ | PM, DEPUTY_PM, WBS_OWNER, MEMBER, REVIEWER, EXTERNAL |
| allocation | I(0-100) | ✅ | 默认 100，占用率 |
| join_date | D | ✅ | |
| leave_date | D | — | |
| is_active | B | ✅ | true |
| raci | E | — | R, A, C, I |

**唯一**：`uniq_pm (project_id, user_id, role)`。

### A1.1.5 wbs_nodes（WBS 节点）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| project_id | Ref | ✅ | |
| parent_id | Ref | — | 自引用 |
| code | S(40) | ✅ | 节点编号 |
| name | S(200) | ✅ | |
| type | E | ✅ | PHASE, DELIVERABLE, WORK_PACKAGE, TASK |
| level | I | ✅ | 1-5 |
| owner_id | Ref | — | |
| plan_start/plan_end | D | ✅ | |
| actual_start/actual_end | D | — | |
| weight | D(0-1) | ✅ | 父节点进度权重 |
| progress | D(0-1) | ✅ | 完成度 |
| estimate_hours | D | — | |
| actual_hours | D | — | |
| is_milestone | B | ✅ | false |
| sort | I | ✅ | 排序 |

### A1.1.6 milestones（里程碑）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| project_id | Ref | ✅ | |
| wbs_id | Ref | — | 关联 WBS |
| name | S(200) | ✅ | |
| type | E | ✅ | KICKOFF, STAGE_GATE, KEY_DELIVERY, ACCEPTANCE, CLOSING |
| plan_date | D | ✅ | |
| forecast_date | D | — | |
| actual_date | D | — | |
| status | E | ✅ | PENDING, FORECAST, ACHIEVED, MISSED, CANCELLED |
| approver_id | Ref | — | |

### A1.1.7 baselines（基线）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| project_id | Ref | ✅ | |
| version | S(20) | ✅ | 如 V1.0 |
| snapshot | J | ✅ | 完整 WBS 快照 |
| status | E | ✅ | DRAFT, APPROVED, SUPERSEDED |
| approved_by | Ref | — | |
| approved_at | DT | — | |
| is_current | B | ✅ | true=当前基线 |

### A1.1.8 stage_gates（阶段门）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| project_id | Ref | ✅ | |
| name | S(200) | ✅ | |
| from_phase | S(50) | ✅ | |
| to_phase | S(50) | ✅ | |
| criteria | J | ✅ | 准入准出条件 |
| status | E | ✅ | LOCKED, OPEN, PASSED, FAILED |
| reviewer_ids | Arr<Ref> | ✅ | |
| decision | E | — | PASS, CONDITIONAL, FAIL |

## A1.2 工作项域

### A1.2.1 work_items（统一工作项）

| 字段 | 类型 | 必填 | 枚举/约束 | 说明 |
| --- | --- | --- | --- | --- |
| id | S(32) | ✅ | PK | |
| key | S(40) | ✅ | UNIQUE(key, tenant_id) | 如 `PRJ-123` |
| project_id | Ref | ✅ | | |
| type | E | ✅ | EPIC, FEATURE, STORY, TASK, BUG, SPIKE | |
| title | S(500) | ✅ | | |
| description | Txt | — | | 富文本 |
| status | E | ✅ | BACKLOG, TODO, IN_PROGRESS, BLOCKED, IN_REVIEW, DONE, ARCHIVED | |
| priority | E | ✅ | P0, P1, P2, P3 | |
| severity | E | — | S1, S2, S3, S4 | 仅 Bug |
| assignee_id | Ref | — | | 主负责人 |
| reporter_id | Ref | ✅ | | 报告人 |
| watchers | Arr<Ref> | — | | 关注人 |
| collaborators | Arr<Ref> | — | | 协作人 |
| parent_id | Ref | — | | 自引用 |
| sprint_id | Ref | — | | |
| wbs_id | Ref | — | | 关联 WBS |
| estimate_hours | D | — | >=0 | |
| actual_hours | D | — | >=0 | 自动汇总 |
| remaining_hours | D | — | >=0 | 预估-实际+重估 |
| plan_start/due | D | — | | |
| actual_start/done | D | — | | |
| labels | Arr<S> | — | | |
| components | Arr<S> | — | | 模块 |
| story_points | D | — | | 故事点 |
| custom_fields | J | — | | |
| resolution | E | — | FIXED, WONT_FIX, DUPLICATE, CANNOT_REPRODUCE | |
| rag | E | ✅ | GREEN, AMBER, RED | |
| blocked_reason | S(100) | — | | |
| blocked_by_id | Ref | — | | |
| linked_items | Arr<Ref> | — | | 关联工作项 |
| attachments | Arr<Ref> | — | | |
| sla_due | DT | — | | |
| created_via | E | — | WEB, API, IM, EMAIL, TEMPLATE | |

**索引**：
- `idx_wi_project_status (project_id, status)`
- `idx_wi_assignee (assignee_id, status)`
- `idx_wi_sprint (sprint_id)`
- `idx_wi_due (due_date)`
- `idx_wi_type (project_id, type)`

### A1.2.2 sprints（迭代）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| project_id | Ref | ✅ | |
| name | S(100) | ✅ | |
| goal | Txt | — | |
| start_date/end_date | D | ✅ | |
| status | E | ✅ | FUTURE, ACTIVE, COMPLETED |
| capacity_hours | D | — | |
| planned_points | D | — | |
| completed_points | D | — | |
| velocity | D | — | |

### A1.2.3 dependencies（依赖）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| from_item_id | Ref | ✅ | 前置 |
| to_item_id | Ref | ✅ | 后继 |
| type | E | ✅ | FS, SS, FF, SF |
| lag | I | — | 单位：小时（可负） |
| created_by | Ref | ✅ | |

**唯一**：`(from_item_id, to_item_id, type)`。

### A1.2.4 comments（评论）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| target_type | E | ✅ | PROJECT, TASK, DOC, RISK, ISSUE, ... |
| target_id | Ref | ✅ | |
| author_id | Ref | ✅ | |
| content | Txt | ✅ | |
| mentions | Arr<Ref> | — | |
| parent_id | Ref | — | 二级评论 |
| reactions | J | — | {emoji: [userId]} |
| edited_at | DT | — | |
| is_deleted | B | ✅ | false |

### A1.2.5 attachments（附件）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| target_type | E | ✅ | |
| target_id | Ref | ✅ | |
| name | S(255) | ✅ | |
| mime | S(100) | ✅ | |
| size | L | ✅ | bytes |
| storage_url | S(500) | ✅ | OSS path |
| sha256 | S(64) | ✅ | |
| scan_status | E | — | PENDING, CLEAN, INFECTED |
| uploaded_by | Ref | ✅ | |

---
