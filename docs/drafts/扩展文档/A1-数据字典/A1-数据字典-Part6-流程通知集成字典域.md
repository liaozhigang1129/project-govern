# A1 数据字典 Part6 — 流程引擎、通知、集成、字典

> 本节覆盖 A1.7 流程/通知/集成/字典域全部表。

## A1.7 流程、通知、集成、字典域

### A1.7.1 workflow_definitions（流程定义）

| 字段 | 类型 | 必填 | 枚举/约束 | 说明 |
| --- | --- | --- | --- | --- |
| id | S(32) | ✅ | PK | |
| tenant_id | S(32) | ✅ | | |
| code | S(50) | ✅ | UNIQUE(tenant_id, code) | 流程编码 |
| name | S(200) | ✅ | | |
| category | E | ✅ | CHARTER, PLAN, BASELINE, CHANGE, RESOURCE, RISK, DOC, REVIEW, EXPENSE, PROCUREMENT, CONTRACT, TIMESHEET, CUSTOM | |
| bpmn_xml | Txt | ✅ | | BPMN 2.0 流程定义 |
| form_schema | J | ✅ | | 表单 JSON |
| node_configs | J | ✅ | | 节点配置（审批人/规则/SLA） |
| trigger | J | — | | 触发条件 |
| version | I | ✅ | 1 | |
| status | E | ✅ | DRAFT, PUBLISHED, DEPRECATED | |
| published_at | DT | — | | |
| published_by | Ref | — | | |
| is_built_in | B | ✅ | false | |
| description | Txt | — | | |
| timeout_hours | I | — | | 默认超时 |
| escalation_rule | J | — | | 升级规则 |

**索引**：`uniq_wf_code (tenant_id, code, version)`。

### A1.7.2 workflow_instances（流程实例）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| definition_id | Ref | ✅ | |
| definition_version | I | ✅ | 锁定版本 |
| business_key | S(100) | ✅ | UNIQUE(definition_id, business_key) |
| business_type | E | ✅ | PROJECT, TASK, BUDGET, ... |
| business_id | S(32) | ✅ | |
| title | S(500) | ✅ | |
| initiator_id | Ref | ✅ | |
| status | E | ✅ | RUNNING, SUSPENDED, COMPLETED, TERMINATED, WITHDRAWN |
| current_nodes | Arr<S> | ✅ | 当前活动节点 |
| variables | J | ✅ | 流程变量 |
| started_at | DT | ✅ | |
| ended_at | DT | — | |
| result | E | — | APPROVED, REJECTED, CANCELLED |
| parent_instance_id | Ref | — | 子流程 |
| reason | Txt | — | 终止/驳回原因 |

**索引**：
- `idx_wfi_business (business_type, business_id)`
- `idx_wfi_initiator (initiator_id, status)`
- `idx_wfi_status (status, started_at)`

### A1.7.3 workflow_tasks（流程任务/待办）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| instance_id | Ref | ✅ | |
| node_id | S(50) | ✅ | 节点 ID |
| node_name | S(200) | ✅ | |
| assignee_id | Ref | — | 主审批人 |
| candidate_ids | Arr<Ref> | ✅ | 候选人 |
| cc_ids | Arr<Ref> | — | 抄送 |
| status | E | ✅ | PENDING, IN_PROGRESS, COMPLETED, SKIPPED, TIMEOUT |
| decision | E | — | APPROVE, REJECT, RETURN, ADD_SIGN, TRANSFER, DELEGATE |
| comment | Txt | — | |
| form_data | J | — | 表单填写数据 |
| started_at | DT | ✅ | |
| completed_at | DT | — | |
| due_at | DT | — | |
| escalated | B | ✅ | false |
| escalated_at | DT | — | |
| sub_tasks | Arr<Ref> | — | 加签子任务 |

**索引**：
- `idx_wft_assignee (assignee_id, status)`
- `idx_wft_due (due_at, status)`
- `idx_wft_instance (instance_id, status)`

### A1.7.4 workflow_histories（流程流转历史）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| instance_id | Ref | ✅ | |
| from_node | S(50) | — | |
| to_node | S(50) | ✅ | |
| actor_id | Ref | ✅ | |
| action | E | ✅ | SUBMIT, APPROVE, REJECT, RETURN, ADD_SIGN, TRANSFER, DELEGATE, WITHDRAW, AUTO, TIMEOUT |
| comment | Txt | — | |
| before_vars | J | — | |
| after_vars | J | — | |
| occurred_at | DT | ✅ | |

### A1.7.5 notifications（站内通知）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| tenant_id | S(32) | ✅ | |
| user_id | Ref | ✅ | 接收人 |
| type | E | ✅ | SYSTEM, MENTION, ASSIGN, APPROVAL, CHANGE, RISK, ALERT, ANNOUNCEMENT, SUBSCRIPTION |
| category | E | ✅ | TASK, RISK, BUDGET, DOC, REVIEW, WORKFLOW, OTHER |
| title | S(500) | ✅ | |
| content | Txt | ✅ | |
| link_url | S(500) | — | 跳转 |
| target_type | E | — | |
| target_id | S(32) | — | |
| priority | E | ✅ | LOW, NORMAL, HIGH, URGENT |
| channels | Arr<E> | — | [INAPP, EMAIL, IM, SMS] |
| delivery_status | J | ✅ | 各通道投递状态 |
| is_read | B | ✅ | false |
| read_at | DT | — | |
| is_archived | B | ✅ | false |
| expires_at | DT | — | |
| created_at | DT | ✅ | |

**索引**：
- `idx_notif_user_unread (user_id, is_read, created_at DESC)`
- `idx_notif_target (target_type, target_id)`

### A1.7.6 subscriptions（订阅规则）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| user_id | Ref | ✅ | |
| target_type | E | ✅ | PROJECT, TASK, RISK, DOC, REVIEW |
| target_id | S(32) | ✅ | |
| event_types | Arr<E> | ✅ | 监听事件类型 |
| channels | Arr<E> | ✅ | 投递通道 |
| enabled | B | ✅ | true |
| created_at | DT | ✅ | |

**唯一**：`uniq_sub (user_id, target_type, target_id, event_types)`。

### A1.7.7 announcements（公告）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| tenant_id | S(32) | ✅ | |
| scope | E | ✅ | TENANT, ORG, PROGRAM, PROJECT |
| scope_id | S(32) | — | |
| title | S(500) | ✅ | |
| content | Txt | ✅ | |
| type | E | ✅ | NOTICE, POLICY, EVENT, OUTAGE, TRAINING, OTHER |
| priority | E | ✅ | |
| require_confirm | B | ✅ | false |
| author_id | Ref | ✅ | |
| published_at | DT | — | |
| expires_at | DT | — | |
| status | E | ✅ | DRAFT, PUBLISHED, EXPIRED, WITHDRAWN |
| channels | Arr<E> | ✅ | |
| attachments | Arr<Ref> | — | |

### A1.7.8 announcement_receipts（公告回执）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| announcement_id | Ref | ✅ | |
| user_id | Ref | ✅ | |
| delivered_at | DT | ✅ | |
| read_at | DT | — | |
| confirmed_at | DT | — | |

**唯一**：`uniq_ar (announcement_id, user_id)`。

### A1.7.9 integrations（集成连接配置）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| tenant_id | S(32) | ✅ | |
| name | S(200) | ✅ | |
| system | E | ✅ | OA, IM, EMAIL, ALM, DEVOPS, ERP, HR, CRM, BI, E_SIGN, OSS, CALENDAR, LDAP, IDaaS, CUSTOM |
| auth_type | E | ✅ | OAUTH2, API_KEY, BASIC, CERT, WEBHOOK, LDAP, SAML |
| config | J | ✅ | 各系统配置（脱敏存储） |
| status | E | ✅ | ACTIVE, DISABLED, ERROR |
| last_sync_at | DT | — | |
| last_error | Txt | — | |
| sync_direction | E | — | IN, OUT, BI |
| rate_limit | I | — | |
| owner_id | Ref | ✅ | |

### A1.7.10 webhook_endpoints（Webhook 端点）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| tenant_id | S(32) | ✅ | |
| integration_id | Ref | ✅ | |
| url | S(500) | ✅ | |
| secret | S(128) | ✅ | HMAC 签名密钥 |
| event_types | Arr<S> | ✅ | 订阅事件 |
| enabled | B | ✅ | true |
| created_by | Ref | ✅ | |

### A1.7.11 webhook_deliveries（Webhook 投递记录）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(64) | ✅ | |
| endpoint_id | Ref | ✅ | |
| event_id | S(64) | ✅ | |
| event_type | S(80) | ✅ | |
| payload | J | ✅ | |
| status | E | ✅ | PENDING, SUCCESS, FAILED, RETRY, EXPIRED |
| http_status | I | — | |
| response_body | Txt | — | |
| attempts | I | ✅ | 0 |
| last_attempt_at | DT | — | |
| next_retry_at | DT | — | |
| created_at | DT | ✅ | |

**索引**：`idx_wd_endpoint_status (endpoint_id, status, next_retry_at)`。

### A1.7.12 event_outbox（事件发件箱 / CDC）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(64) | ✅ | UUIDv7 |
| event_type | S(80) | ✅ | |
| aggregate_type | E | ✅ | |
| aggregate_id | S(32) | ✅ | |
| tenant_id | S(32) | ✅ | |
| payload | J | ✅ | |
| headers | J | ✅ | |
| status | E | ✅ | PENDING, PUBLISHED, FAILED |
| published_at | DT | — | |
| partition_key | S(64) | ✅ | |
| created_at | DT | ✅ | |

**索引**：`idx_outbox_status (status, created_at)`。

### A1.7.13 dictionaries（数据字典）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| tenant_id | S(32) | ✅ | |
| code | S(80) | ✅ | UNIQUE(tenant_id, code) |
| name | S(200) | ✅ | |
| category | S(50) | ✅ | |
| items | J | ✅ | 字典项 JSON |
| is_built_in | B | ✅ | false |
| is_active | B | ✅ | true |
| sort | I | ✅ | |
| description | Txt | — | |

### A1.7.14 project_templates（项目模板）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| tenant_id | S(32) | ✅ | |
| name | S(200) | ✅ | |
| category | E | ✅ | RND, INFRA, MKT, ... |
| industry | S(50) | — | |
| description | Txt | — | |
| wbs_template | J | ✅ | WBS 结构 |
| milestone_template | J | ✅ | |
| risk_template | J | ✅ | |
| budget_template | J | ✅ | |
| document_template_ids | Arr<Ref> | ✅ | |
| role_template | J | ✅ | 默认角色 |
| workflow_codes | Arr<S> | ✅ | 流程 |
| estimated_duration_days | I | — | |
| is_active | B | ✅ | true |
| is_built_in | B | ✅ | false |
| version | S(20) | ✅ | |

### A1.7.15 custom_field_defs（自定义字段定义）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| tenant_id | S(32) | ✅ | |
| target_type | E | ✅ | PROJECT, TASK, RISK, ISSUE, BUDGET |
| field_key | S(50) | ✅ | |
| field_name | S(100) | ✅ | |
| field_type | E | ✅ | TEXT, NUMBER, DATE, SELECT, MULTI_SELECT, BOOLEAN, USER, REF |
| options | J | — | 字典项 |
| required | B | ✅ | false |
| default_value | S(200) | — | |
| validation | J | — | 正则/范围 |
| visible_to | J | — | 角色可见性 |
| sort | I | ✅ | |
| is_active | B | ✅ | true |

**唯一**：`uniq_cfd (target_type, field_key)`。

### A1.7.16 custom_field_values（自定义字段值）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| field_id | Ref | ✅ | |
| target_id | S(32) | ✅ | |
| value | J | ✅ | 任意 JSON |
| updated_at | DT | ✅ | |

**唯一**：`uniq_cfv (field_id, target_id)`。

---

## 附录：枚举值汇总（与 SRS §1.4 + 各模块对齐）

| 枚举 | 取值 |
| --- | --- |
| ProjectStatus | DRAFT, PENDING, ACTIVE, SUSPENDED, CLOSING, CLOSED, ARCHIVED |
| RagStatus | GREEN, AMBER, RED, UNKNOWN |
| ProjectLevel | S, A, B, C, D |
| SecretLevel | PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED |
| WorkItemType | EPIC, FEATURE, STORY, TASK, BUG, SPIKE |
| WorkItemStatus | BACKLOG, TODO, IN_PROGRESS, BLOCKED, IN_REVIEW, DONE, ARCHIVED |
| Priority | P0, P1, P2, P3 |
| Severity | S1, S2, S3, S4 |
| RiskLevel | LOW, MEDIUM, HIGH, EXTREME |
| RiskStrategy | AVOID, MITIGATE, TRANSFER, ACCEPT, EXPLOIT, SHARE |
| BudgetControl | HARD, SOFT |
| ChangeImpact | MINOR, MAJOR, CRITICAL |
| ControlStrategy | HARD, SOFT |
| DepType | FS, SS, FF, SF |

---

至此，**A1 数据字典 6 个 Part 全部完成**，共覆盖 7 大域、**48 张核心表**。下一步将进入 **A2 OpenAPI 3.0 规范**。
