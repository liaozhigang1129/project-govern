# A1 数据字典 Part2 — 资源域

> 承接 Part1（A1.0 ~ A1.2）。本节覆盖 A1.3 资源域全部表。类型简记同 Part1。

## A1.3 资源域

### A1.3.1 users（用户主表）

| 字段 | 类型 | 必填 | 枚举/约束 | 说明 |
| --- | --- | --- | --- | --- |
| id | S(32) | ✅ | PK | |
| tenant_id | S(32) | ✅ | FK | 多租户隔离 |
| login_name | S(64) | ✅ | UNIQUE(tenant_id, login_name) | 登录名 |
| email | S(128) | ✅ | UNIQUE(tenant_id, email) | 主邮箱 |
| mobile | S(32) | — | | 加密存储 |
| display_name | S(100) | ✅ | | 显示名 |
| avatar_url | S(500) | — | | 头像 |
| employee_no | S(40) | — | UNIQUE(tenant_id, employee_no) | 工号 |
| id_type | E | — | ID_CARD, PASSPORT, OTHER | 证件类型 |
| id_no_hash | S(128) | — | | 证件号哈希（敏感） |
| status | E | ✅ | ACTIVE, SUSPENDED, LOCKED, OFFBOARDED | 状态 |
| type | E | ✅ | EMPLOYEE, OUTSOURCED, PARTNER, EXTERNAL, BOT | 用户类型 |
| dept_id | Ref | ✅ | FK→departments | 主部门 |
| position_id | Ref | — | FK→positions | 岗位 |
| manager_id | Ref | — | FK→users | 直属上级 |
| cost_center | S(40) | — | | 成本中心 |
| location | S(100) | — | | 工作地 |
| timezone | S(50) | ✅ | default "Asia/Shanghai" | IANA 时区 |
| locale | S(10) | ✅ | default "zh-CN" | 语言 |
| hire_date | D | — | | 入职日期 |
| leave_date | D | — | | 离职日期 |
| last_login_at | DT | — | | 最近登录 |
| mfa_enabled | B | ✅ | false | 是否启用 MFA |
| mfa_type | E | — | SMS, TOTP, FIDO2 | MFA 类型 |
| password_changed_at | DT | — | | 密码修改时间 |
| source | E | ✅ | LOCAL, LDAP, OA, SSO | 来源 |
| external_id | S(128) | — | | 外部系统 ID |

**索引**：
- `uniq_users_login (tenant_id, login_name)`
- `uniq_users_email (tenant_id, email)`
- `idx_users_dept (dept_id)`
- `idx_users_manager (manager_id)`

### A1.3.2 departments（组织部门）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| tenant_id | S(32) | ✅ | |
| name | S(200) | ✅ | |
| short_name | S(50) | — | |
| code | S(50) | ✅ | 部门编码 |
| type | E | ✅ | COMPANY, BU, DIVISION, DEPT, TEAM, VIRTUAL |
| parent_id | Ref | — | 自引用 |
| path | S(500) | ✅ | 物化路径：`/root/bu/div/dept/` |
| level | I | ✅ | 层级 |
| sort | I | ✅ | |
| leader_id | Ref | — | 部门负责人 |
| cost_center | S(40) | — | 成本中心 |
| is_active | B | ✅ | true |
| external_id | S(128) | — | |

**唯一**：`uniq_dept_code (tenant_id, code)`；`uniq_dept_path (tenant_id, path)`。

### A1.3.3 positions（岗位）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| tenant_id | S(32) | ✅ | |
| name | S(100) | ✅ | 岗位名 |
| category | E | ✅ | MGMT, TECH, FUNC, SALES, SUPPORT, OTHER |
| level | E | — | P1, P2, P3, P4, P5, P6, P7 | 职级 |
| job_family | S(50) | — | 职位族 |
| standard_cost_rate | M | — | 标准人天费率 |

### A1.3.4 user_positions（用户-岗位多对多）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| user_id | Ref | ✅ | |
| position_id | Ref | ✅ | |
| is_primary | B | ✅ | 主岗 |
| start_date | D | ✅ | |
| end_date | D | — | |

**唯一**：`uniq_up (user_id, position_id, start_date)`。

### A1.3.5 user_roles（用户-角色绑定）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| user_id | Ref | ✅ | |
| role_id | Ref | ✅ | FK→roles |
| scope_type | E | ✅ | GLOBAL, ORG, DEPT, PROGRAM, PROJECT |
| scope_id | S(32) | — | 范围 ID |
| granted_by | Ref | ✅ | |
| granted_at | DT | ✅ | |
| expires_at | DT | — | 临时授权 |

**唯一**：`uniq_ur (user_id, role_id, scope_type, scope_id)`。

### A1.3.6 roles（角色定义）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| tenant_id | S(32) | ✅ | |
| code | S(50) | ✅ | 如 `PMO_DIRECTOR` |
| name | S(100) | ✅ | |
| category | E | ✅ | SYSTEM, PMO, PROJECT, FUNCTION |
| description | Txt | — | |
| permissions | J | ✅ | 权限点 JSON |
| is_built_in | B | ✅ | false |
| status | E | ✅ | ENABLED, DISABLED |

### A1.3.7 skills（技能字典）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| tenant_id | S(32) | ✅ | |
| name | S(100) | ✅ | |
| category | E | ✅ | TECH, BUSINESS, MGMT, LANGUAGE, COMPLIANCE, DOMAIN |
| description | Txt | — | |
| parent_id | Ref | — | 支持技能树 |
| is_active | B | ✅ | true |

### A1.3.8 user_skills（用户技能矩阵）

| 字段 | 类型 | 必填 | 枚举/约束 | 说明 |
| --- | --- | --- | --- | --- |
| id | S(32) | ✅ | | |
| user_id | Ref | ✅ | | |
| skill_id | Ref | ✅ | | |
| level | E | ✅ | NOVICE, INTERMEDIATE, ADVANCED, EXPERT | 自评/他评 |
| assessed_by | Ref | — | | 评估人 |
| assessed_at | DT | — | | |
| certified | B | ✅ | false | 是否持证 |
| last_used_at | D | — | | |
| willingness | E | — | HIGH, MEDIUM, LOW | 意愿度 |
| years | D | — | | 经验年限 |

**唯一**：`uniq_us (user_id, skill_id)`。

### A1.3.9 certifications（证书）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| user_id | Ref | ✅ | |
| name | S(200) | ✅ | |
| issuer | S(200) | — | 颁发机构 |
| issued_date | D | — | |
| expiry_date | D | — | |
| file_url | S(500) | — | |
| status | E | ✅ | VALID, EXPIRING, EXPIRED, REVOKED |

### A1.3.10 resource_pools（资源池）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| tenant_id | S(32) | ✅ | |
| name | S(200) | ✅ | |
| type | E | ✅ | DEPT, SKILL, ROLE, SHARED, ROTATION |
| owner_id | Ref | ✅ | 池负责人 |
| description | Txt | — | |
| is_active | B | ✅ | true |

### A1.3.11 resource_pool_members（资源池成员）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| pool_id | Ref | ✅ | |
| user_id | Ref | ✅ | |
| join_at | DT | ✅ | |
| leave_at | DT | — | |
| is_active | B | ✅ | true |

**唯一**：`uniq_rpm (pool_id, user_id)`。

### A1.3.12 resource_assignments（资源分配/调度）

| 字段 | 类型 | 必填 | 枚举/约束 | 说明 |
| --- | --- | --- | --- | --- |
| id | S(32) | ✅ | | |
| user_id | Ref | ✅ | | |
| project_id | Ref | ✅ | FK→projects | |
| role_in_project | E | ✅ | PM, DEPUTY_PM, DEV, QA, BA, DESIGNER, REVIEWER, OTHER | |
| allocation | I(0-100) | ✅ | default 100 | 占用率 % |
| status | E | ✅ | SOFT_BOOKED, HARD_BOOKED, ACTIVE, RELEASED, CANCELLED | |
| start_date | D | ✅ | | |
| end_date | D | ✅ | >= start_date | |
| actual_start | D | — | | |
| actual_end | D | — | | |
| requested_by | Ref | ✅ | | 申请人 |
| approved_by | Ref | — | | 审批人 |
| approved_at | DT | — | | |
| billable | B | ✅ | true | 是否计费 |
| cost_rate | M | — | | 协商费率（覆盖标准） |
| notes | Txt | — | | |
| source | E | — | MANUAL, AUTO, TEMPLATE | |

**索引**：
- `idx_ra_user (user_id, status)`
- `idx_ra_project (project_id, status)`
- `idx_ra_dates (start_date, end_date)`
- `uniq_ra_active (user_id, project_id, role_in_project, start_date)` 部分唯一

### A1.3.13 calendars（日历）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| tenant_id | S(32) | ✅ | |
| name | S(100) | ✅ | |
| type | E | ✅ | COMPANY, DEPT, TEAM, PERSONAL |
| timezone | S(50) | ✅ | |
| work_hours | J | ✅ | `{mon:{start:"09:00",end:"18:00",break:60}, ...}` |
| is_default | B | ✅ | false |

### A1.3.14 calendar_holidays（节假日��

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| calendar_id | Ref | ✅ | |
| date | D | ✅ | |
| name | S(100) | ✅ | |
| type | E | ✅ | STATUTORY, COMPANY, WEEKEND, MAKEUP_WORK, SPECIAL |
| is_workday | B | ✅ | 调休上班时为 true |
| region | S(50) | — | |

**唯一**：`uniq_holiday (calendar_id, date)`。

### A1.3.15 user_leaves（请假/休假）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| user_id | Ref | ✅ | |
| type | E | ✅ | ANNUAL, SICK, PERSONAL, MATERNITY, PARENTAL, UNPAID, OTHER |
| start_at | DT | ✅ | |
| end_at | DT | ✅ | |
| hours | D | ✅ | 请假小时数 |
| status | E | ✅ | PENDING, APPROVED, REJECTED, CANCELLED |
| approver_id | Ref | — | |
| reason | Txt | — | |
| source | E | — | MANUAL, OA_SYNC, HR_SYNC |
| external_id | S(128) | — | |

**索引**：`idx_leave_user_dates (user_id, start_at, end_at)`。

### A1.3.16 capacity_snapshots（产能快照）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| user_id | Ref | ✅ | |
| snapshot_date | D | ✅ | |
| period | E | ✅ | DAY, WEEK, MONTH |
| available_hours | D | ✅ | |
| allocated_hours | D | ✅ | |
| booked_hours | D | ✅ | |
| actual_hours | D | ✅ | |
| utilization | D | ✅ | 比率（0-2+） |
| snapshot_at | DT | ✅ | |

**唯一**：`uniq_cap (user_id, snapshot_date, period)`。

---
