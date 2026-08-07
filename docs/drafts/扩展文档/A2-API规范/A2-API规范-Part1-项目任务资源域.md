# A2 OpenAPI 3.0 规范 Part1 — 总览 + 项目/任务/资源域

> 文档名：`zhiyu-api-v1.yaml`（规范导出）。本 Part 覆盖：A2.0 全局约定、错误模型、分页、鉴权、版本、限流、查询语言；A2.1 项目域；A2.2 工作项域；A2.3 资源域（人员/部门/资源调度）。

## A2.0 全局约定

### A2.0.1 基础信息

```yaml
openapi: 3.0.3
info:
  title: PMO-PMS Public API
  version: 1.0.0
  description: |
    PMO-PMS 开放 API。覆盖项目、任务、资源、成本、风险、文档、流程、报表全领域。
    所有接口需要 Bearer Token，遵循 OAuth 2.0。
  contact: { name: PMO-PMS API Support, email: api@zhiyu-pms.example.com }
  license: { name: Proprietary }
servers:
  - { url: https://api.zhiyu-pms.example.com/v1, description: Production }
  - { url: https://api-staging.zhiyu-pms.example.com/v1, description: Staging }
externalDocs:
  url: https://docs.zhiyu-pms.example.com/api
```

### A2.0.2 通用请求头

| Header | 必填 | 说明 |
| --- | --- | --- |
| Authorization | ✅ | `Bearer {access_token}` |
| X-Tenant-Id | △ | 多租户场景必填，缺省取 token 中 `tenant` 声明 |
| X-Request-Id | △ | 客户端生成 UUID，便于链路追踪 |
| X-Idempotency-Key | △ | 幂等键（写接口，24h 内同 key 复用结果） |
| Accept-Language | — | `zh-CN` / `en-US` |
| If-Match | △ | 乐观锁并发控制，值为目标资源 ETag |
| Prefer | △ | `return=minimal` / `return=representation` |

### A2.0.3 鉴权方案

```yaml
components:
  securitySchemes:
    OAuth2:
      type: oauth2
      flows:
        clientCredentials:
          tokenUrl: /oauth2/token
          scopes:
            pmo.read: 只读
            pmo.write: 读写
            pmo.admin: 管理
        authorizationCode:
          authorizationUrl: /oauth2/authorize
          tokenUrl: /oauth2/token
          scopes: { pmo.read: Read, pmo.write: Write }
    ApiKey:
      type: apiKey
      in: header
      name: X-API-Key
```

### A2.0.4 错误模型（RFC 7807）

```yaml
components:
  schemas:
    Error:
      type: object
      required: [code, message, traceId]
      properties:
        code: { type: string, example: PMS_PROJECT_NOT_FOUND }
        message: { type: string }
        details:
          type: array
          items: { type: object, additionalProperties: true }
        traceId: { type: string, format: uuid }
        timestamp: { type: string, format: date-time }
        path: { type: string }
    ValidationError:
      allOf:
        - $ref: '#/components/schemas/Error'
        - type: object
          properties:
            details:
              type: array
              items:
                type: object
                properties:
                  field: { type: string }
                  rule: { type: string }
                  message: { type: string }
```

| HTTP | 错误码示例 | 含义 |
| --- | --- | --- |
| 400 | PMS_VALIDATION_FAILED | 参数校验失败 |
| 401 | PMS_UNAUTHENTICATED | 未认证 |
| 403 | PMS_FORBIDDEN | 越权 |
| 404 | PMS_PROJECT_NOT_FOUND | 资源不存在 |
| 409 | PMS_VERSION_CONFLICT | 乐观锁冲突 |
| 409 | PMS_DUPLICATE_KEY | 唯一键冲突 |
| 422 | PMS_BUSINESS_RULE_VIOLATED | 业务规则违反 |
| 429 | PMS_RATE_LIMITED | 限流 |
| 5xx | PMS_INTERNAL_ERROR | 服务异常 |

### A2.0.5 分页

```yaml
PageQuery:
  type: object
  properties:
    page: { type: integer, minimum: 1, default: 1 }
    size: { type: integer, minimum: 1, maximum: 200, default: 20 }
    sort: { type: string, example: "-created_at,name" }
    cursor: { type: string, description: 游标分页模式 }

Page:
  type: object
  properties:
    items: { type: array, items: {} }
    total: { type: integer }
    page: { type: integer }
    size: { type: integer }
    hasMore: { type: boolean }
    nextCursor: { type: string, nullable: true }
```

### A2.0.6 过滤 & 字段投影

- 过滤：`?filter[status]=ACTIVE&filter[pmId]=u-001`
- 全文搜索：`?q=keyword`
- 字段投影：`?fields=id,name,status`
- 关联展开：`?expand=members,baseline`

### A2.0.7 限流

| 层级 | 默认 | 超限响应 |
| --- | --- | --- |
| 用户 | 60 req/min | 429 + Retry-After |
| 租户 | 10,000 req/min | 429 |
| 应用 | 120 req/s | 429 |

---

## A2.1 项目域

### A2.1.1 Project（核心 schema）

```yaml
Project:
  type: object
  properties:
    id: { type: string }
    code: { type: string }
    name: { type: string }
    type: { type: string, enum: [RND, INFRA, MKT, COMPL, CONSULT, OTHER] }
    category: { type: string, nullable: true }
    level: { type: string, enum: [S, A, B, C, D] }
    secretLevel: { type: string, enum: [PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED] }
    status: { type: string, enum: [DRAFT, PENDING, ACTIVE, SUSPENDING, CLOSING, CLOSED, ARCHIVED] }
    rag: { type: string, enum: [GREEN, AMBER, RED, UNKNOWN] }
    healthScore: { type: integer, minimum: 0, maximum: 100 }
    sponsorId: { type: string }
    pmId: { type: string }
    businessUnitId: { type: string }
    deptId: { type: string }
    programId: { type: string, nullable: true }
    portfolioId: { type: string, nullable: true }
    startDate: { type: string, format: date }
    endDate: { type: string, format: date }
    actualStart: { type: string, format: date, nullable: true }
    actualEnd: { type: string, format: date, nullable: true }
    currency: { type: string, example: CNY }
    totalBudget: { type: number, format: decimal }
    tags: { type: array, items: { type: string } }
    customFields: { type: object, additionalProperties: true }
    createdAt: { type: string, format: date-time }
    updatedAt: { type: string, format: date-time }
    version: { type: integer, description: 乐观锁 }
```

### A2.1.2 端点

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/projects` | 列表（分页/过滤/搜索） |
| POST | `/projects` | 立项（草稿） |
| GET | `/projects/{id}` | 详情 |
| PATCH | `/projects/{id}` | 更新（If-Match 必填） |
| DELETE | `/projects/{id}` | 归档（软删） |
| POST | `/projects/{id}/submit` | 提交立项审批 |
| POST | `/projects/{id}/activate` | 激活（审批通过） |
| POST | `/projects/{id}/suspend` | 挂起 |
| POST | `/projects/{id}/resume` | 恢复 |
| POST | `/projects/{id}/close` | 结项 |
| GET | `/projects/{id}/members` | 成员列表 |
| POST | `/projects/{id}/members` | 添加成员 |
| PATCH | `/projects/{id}/members/{userId}` | 更新成员角色/占用率 |
| DELETE | `/projects/{id}/members/{userId}` | 移除成员 |
| GET | `/projects/{id}/wbs` | WBS 树 |
| POST | `/projects/{id}/wbs` | 创建 WBS 节点 |
| PATCH | `/wbs/{wbsId}` | 更新 WBS 节点 |
| DELETE | `/wbs/{wbsId}` | 删除节点 |
| POST | `/projects/{id}/wbs/reorder` | 拖拽重排 |
| GET | `/projects/{id}/milestones` | 里程碑列表 |
| POST | `/projects/{id}/milestones` | 新建里程碑 |
| PATCH | `/milestones/{msId}/achieve` | 标记达成 |
| GET | `/projects/{id}/baselines` | 基线列表 |
| POST | `/projects/{id}/baselines` | 创建基线（待审） |
| POST | `/baselines/{id}/approve` | 审批通过 |
| POST | `/baselines/{id}/restore` | 回滚到该基线 |
| GET | `/baselines/{id}/diff?againstId=...` | 基线差异 |
| GET | `/projects/{id}/gates` | 阶段门 |
| POST | `/gates/{id}/decide` | 阶段门决策 |

### A2.1.3 关键操作示例

**创建立项（草稿）**
```yaml
POST /projects
Request:
  name: 智能客服系统 V2.0
  type: RND
  level: A
  secretLevel: INTERNAL
  sponsorId: u-100
  pmId: u-201
  businessUnitId: d-30
  deptId: d-301
  startDate: 2025-01-15
  endDate: 2025-09-30
  currency: CNY
  totalBudget: 5000000
  tags: [AI, Customer-Service]
  customFields:
    roiExpected: 12000000
Response 201:
  Project { id: p-001, code: "PRJ-BU30-25-0001", status: DRAFT, version: 1 }
```

**基线对比**
```yaml
GET /baselines/b-001/diff?againstId=b-002
Response 200:
  {
    "addedWbs": ["3.2.1"], "removedWbs": [],
    "modifiedWbs": [
      { "id": "3.1", "changes": { "endDate": { "from": "2025-06-30", "to": "2025-07-15" } } }
    ],
    "summary": { "delayDays": 15, "budgetDelta": 0 }
  }
```

---

## A2.2 工作项域

### A2.2.1 端点

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/work-items` | 列表（支持 JQL/类 PQL） |
| POST | `/work-items` | 新建 |
| GET | `/work-items/{id}` | 详情 |
| PATCH | `/work-items/{id}` | 更新 |
| DELETE | `/work-items/{id}` | 归档 |
| POST | `/work-items/{id}/transitions` | 状态流转 |
| POST | `/work-items/{id}/assign` | 指派 |
| POST | `/work-items/{id}/watch` | 关注 |
| POST | `/work-items/{id}/link` | 关联工作项 |
| POST | `/work-items/bulk` | 批量操作 |
| GET | `/work-items/{id}/comments` | 评论 |
| POST | `/work-items/{id}/comments` | 评论 |
| GET | `/work-items/{id}/attachments` | 附件 |
| POST | `/work-items/{id}/attachments` | 上传附件（multipart） |
| GET | `/sprints` | 迭代列表 |
| POST | `/sprints` | 新建迭代 |
| PATCH | `/sprints/{id}` | 更新 |
| POST | `/sprints/{id}/start` | 启动 |
| POST | `/sprints/{id}/complete` | 完成 |
| GET | `/sprints/{id}/burndown` | 燃尽图数据 |
| POST | `/dependencies` | 新建依赖 |
| DELETE | `/dependencies/{id}` | 解除依赖 |
| GET | `/work-items/search` | 高级搜索 |
| POST | `/work-items/{id}/clone` | 克隆 |

### A2.2.2 状态流转

```yaml
POST /work-items/{id}/transitions
Request:
  from: TODO
  to: IN_PROGRESS
  comment: 开始开发
  fields:
    assigneeId: u-301
  notifyWatchers: true
Response 200:
  WorkItem { status: IN_PROGRESS, updatedAt: ... }
```

### A2.2.3 JQL/PQL 查询语法（摘要）

```
project = "p-001" AND status NOT IN (DONE) AND assignee = currentUser() AND priority IN (P0, P1)
```

支持的操作符：`=, !=, IN, NOT IN, <, >, <=, >=, ~ (LIKE), IS EMPTY, IS NOT EMPTY, WAS, CHANGED`。

---

## A2.3 资源域

### A2.3.1 端点

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/users` | 用户列表（脱敏） |
| GET | `/users/{id}` | 详情（按权限） |
| POST | `/users` | 新建 |
| PATCH | `/users/{id}` | 更新 |
| POST | `/users/{id}/disable` | 停用 |
| POST | `/users/{id}/transfer` | 转岗/移交 |
| GET | `/users/{id}/assignments` | 资源分配历史 |
| GET | `/users/{id}/capacity?from=...&to=...` | 产能查询 |
| GET | `/departments` | 部门树 |
| POST | `/departments` | 新建 |
| PATCH | `/departments/{id}` | 更新 |
| POST | `/departments/{id}/move` | 调整组织 |
| GET | `/skills` | 技能字典 |
| GET | `/users/{id}/skills` | 用户的技能 |
| POST | `/users/{id}/skills` | 添加技能 |
| GET | `/resource-pools` | 资源池 |
| POST | `/resource-pools` | 新建 |
| GET | `/resource-pools/{id}/members` | 池成员 |
| POST | `/resource-pools/{id}/members` | 加入池 |
| GET | `/resource-assignments` | 资源分配 |
| POST | `/resource-assignments` | 申请 |
| PATCH | `/resource-assignments/{id}` | 调整 |
| POST | `/resource-assignments/{id}/approve` | 审批 |
| POST | `/resource-assignments/{id}/release` | 释放 |
| GET | `/resource-assignments/conflicts` | 冲突检测 |
| POST | `/resource-assignments/suggest` | 推荐人选（技能+负载） |
| GET | `/calendars` | 日历列表 |
| GET | `/calendars/{id}/holidays` | 节假日 |
| POST | `/leaves` | 申请请假 |
| POST | `/leaves/{id}/approve` | 审批 |

### A2.3.2 资源申请（典型流程）

```yaml
POST /resource-assignments
Request:
  userId: u-301
  projectId: p-001
  roleInProject: DEV
  allocation: 50
  startDate: 2025-02-01
  endDate: 2025-06-30
  billable: true
  costRate: 1500
Response 201:
  ResourceAssignment { id: ra-001, status: SOFT_BOOKED }

POST /resource-assignments/ra-001/approve
Request: { approverId: u-rm-01, comment: "OK" }
Response 200:
  ResourceAssignment { status: HARD_BOOKED, approvedBy: u-rm-01, approvedAt: ... }
```

### A2.3.3 资源冲突检测

```yaml
GET /resource-assignments/conflicts?userId=u-301&from=2025-02-01&to=2025-08-31
Response 200:
  {
    "userId": "u-301",
    "conflicts": [
      {
        "type": "OVERALLOCATION",
        "date": "2025-04-15",
        "totalAllocation": 150,
        "projects": [
          { "projectId": "p-001", "allocation": 100 },
          { "projectId": "p-002", "allocation": 50 }
        ]
      }
    ]
  }
```

---

> Part1 完成。Part2 将覆盖：**成本/工时/风险/变更/缺陷/评审/文档/流程/报表/Webhook 事件**。
