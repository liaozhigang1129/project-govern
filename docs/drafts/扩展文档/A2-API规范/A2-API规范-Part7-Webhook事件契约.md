# A2 OpenAPI 3.0 规范 Part7 — Webhook 事件契约

> 本 Part 覆盖 A2.10 出站事件（Outbox + Webhook）。

---

## A2.10 Webhook 与事件契约

### A2.10.1 端点

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| **集成连接** | | |
| GET | `/integrations` | 已注册集成 |
| POST | `/integrations` | 新建（脱敏存储密钥） |
| GET | `/integrations/{id}` | 详情 |
| PATCH | `/integrations/{id}` | 更新 |
| DELETE | `/integrations/{id}` | 删除 |
| POST | `/integrations/{id}/test` | 连通性测试 |
| POST | `/integrations/{id}/sync` | 手动触发同步 |
| **Webhook 端点** | | |
| GET | `/integrations/{id}/webhook-endpoints` | 端点列表 |
| POST | `/integrations/{id}/webhook-endpoints` | 注册端点 |
| PATCH | `/webhook-endpoints/{id}` | 更新 |
| DELETE | `/webhook-endpoints/{id}` | 删除 |
| POST | `/webhook-endpoints/{id}/test` | 发送测试事件 |
| **Webhook 投递** | | |
| GET | `/webhook-deliveries` | 投递记录（按状态/端点过滤） |
| GET | `/webhook-deliveries/{id}` | 详情 |
| POST | `/webhook-deliveries/{id}/retry` | 手动重试 |
| **事件订阅** | | |
| GET | `/event-types` | 支持的事件类型清单（见 §A2.10.3） |

### A2.10.2 关键 Schema

```yaml
Integration:
  type: object
  properties:
    id: { type: string }
    name: { type: string }
    system: { type: string, enum: [OA, IM, EMAIL, ALM, DEVOPS, ERP, HR, CRM, BI, E_SIGN, OSS, CALENDAR, LDAP, IDAAS, CUSTOM] }
    authType: { type: string, enum: [OAUTH2, API_KEY, BASIC, CERT, WEBHOOK, LDAP, SAML] }
    config: { type: object, description: "配置（密钥已脱敏）" }
    status: { type: string, enum: [ACTIVE, DISABLED, ERROR] }
    lastSyncAt: { type: string, format: date-time, nullable: true }
    lastError: { type: string, nullable: true }
    syncDirection: { type: string, enum: [IN, OUT, BI] }
    rateLimit: { type: integer, nullable: true }

WebhookEndpoint:
  type: object
  properties:
    id: { type: string }
    integrationId: { type: string }
    url: { type: string, format: uri }
    secret: { type: string, description: "HMAC 签名密钥（仅创建时返回）" }
    eventTypes: { type: array, items: { type: string } }
    enabled: { type: boolean }
    headers: { type: object, additionalProperties: { type: string } }

WebhookDelivery:
  type: object
  properties:
    id: { type: string }
    endpointId: { type: string }
    eventId: { type: string }
    eventType: { type: string }
    payload: { type: object }
    status: { type: string, enum: [PENDING, SUCCESS, FAILED, RETRY, EXPIRED] }
    httpStatus: { type: integer, nullable: true }
    responseBody: { type: string, nullable: true }
    attempts: { type: integer }
    lastAttemptAt: { type: string, format: date-time, nullable: true }
    nextRetryAt: { type: string, format: date-time, nullable: true }
    createdAt: { type: string, format: date-time }
```

### A2.10.3 事件类型清单（v1.0）

> 命名规范：`{domain}.{entity}.{action}`，全小写、点分。

#### 项目域

| 事件类型 | 触发时机 | Payload 关键字段 |
| --- | --- | --- |
| `project.created` | 立项草稿创建 | project |
| `project.submitted` | 提交立项审批 | project, workflowInstanceId |
| `project.activated` | 审批通过激活 | project |
| `project.suspended` | 挂起 | project, reason |
| `project.resumed` | 恢复 | project |
| `project.closed` | 结项 | project, closingReportUrl |
| `project.health_changed` | 健康度/RAG 变化 | projectId, oldRag, newRag, oldScore, newScore |
| `project.baseline.published` | 基线发布 | projectId, baselineId, version |
| `project.gate.decided` | 阶段门决策 | projectId, gateId, decision |

#### 工作项域

| 事件类型 | 触发时机 |
| --- | --- |
| `workitem.created` | 创建 |
| `workitem.updated` | 字段更新（含 diff） |
| `workitem.status_changed` | 状态流转 |
| `workitem.assigned` | 指派/转移 |
| `workitem.commented` | 新增评论 |
| `workitem.mentioned` | @用户 |
| `workitem.blocked` | 阻塞 |
| `workitem.unblocked` | 解除阻塞 |
| `workitem.sla_breached` | SLA 违约 |
| `sprint.started` / `sprint.completed` | 迭代起止 |

#### 资源域

| 事件类型 | 触发时机 |
| --- | --- |
| `resource.assignment.requested` | 资源申请提交 |
| `resource.assignment.approved` | 审批通过 |
| `resource.assignment.rejected` | 拒绝 |
| `resource.assignment.released` | 释放 |
| `resource.conflict.detected` | 资源冲突检测 |

#### 成本域

| 事件类型 | 触发时机 |
| --- | --- |
| `budget.created` / `budget.updated` / `budget.approved` / `budget.rejected` / `budget.closed` | 预算生命周期 |
| `budget.exceeded` | 超过软/硬阈值 |
| `budget.alert` | 达到预警阈值（80%） |
| `timeentry.submitted` / `timeentry.approved` / `timeentry.rejected` / `timeentry.locked` | 工时 |
| `expense.submitted` / `expense.approved` / `expense.paid` | 报销 |
| `procurement.approved` / `procurement.milestone.paid` | 采购 |

#### 风险/问题/变更/缺陷

| 事件类型 | 触发时机 |
| --- | --- |
| `risk.opened` / `risk.escalated` / `risk.closed` / `risk.materialized` | 风险 |
| `issue.opened` / `issue.assigned` / `issue.resolved` / `issue.closed` / `issue.sla_breached` | 问题 |
| `change.submitted` / `change.approved` / `change.rejected` / `change.implemented` / `change.completed` | 变更 |
| `defect.created` / `defect.assigned` / `defect.fixed` / `defect.verified` / `defect.closed` / `defect.sla_breached` | 缺陷 |

#### 文档/流程/系统

| 事件类型 | 触发时机 |
| --- | --- |
| `document.created` / `document.version_uploaded` / `document.published` / `document.archived` | 文档 |
| `review.scheduled` / `review.completed` / `review.action_items_created` | 评审 |
| `workflow.instance.started` / `workflow.task.created` / `workflow.task.completed` / `workflow.instance.completed` | 流程 |
| `user.created` / `user.disabled` / `user.transferred` | 用户 |
| `role.assigned` / `role.revoked` | 角色 |

### A2.10.4 标准 Payload 结构（信封）

```json
{
  "eventId": "evt-uuid-v7",
  "eventType": "workitem.status_changed",
  "occurredAt": "2025-04-15T08:23:11.123Z",
  "tenantId": "t-001",
  "producer": "zhiyu-pms",
  "version": 1,
  "data": {
    "workItemId": "wi-100",
    "key": "PRJ-123",
    "from": "TODO",
    "to": "IN_PROGRESS",
    "actorId": "u-301",
    "projectId": "p-001"
  }
}
```

### A2.10.5 Payload 示例

**workitem.status_changed**
```json
{
  "eventId": "01HW8X...",
  "eventType": "workitem.status_changed",
  "occurredAt": "2025-04-15T08:23:11Z",
  "tenantId": "t-001",
  "data": {
    "workItemId": "wi-100",
    "key": "PRJ-123",
    "from": "TODO",
    "to": "IN_PROGRESS",
    "actorId": "u-301",
    "projectId": "p-001",
    "comment": "开始开发"
  }
}
```

**budget.exceeded**
```json
{
  "eventId": "01HW8Y...",
  "eventType": "budget.exceeded",
  "occurredAt": "2025-04-15T10:00:00Z",
  "data": {
    "projectId": "p-001",
    "budgetId": "b-001",
    "category": "LABOR",
    "planned": 3000000,
    "committed": 1500000,
    "actual": 1700000,
    "exceededBy": 200000,
    "controlStrategy": "HARD",
    "ownerId": "u-pm01"
  }
}
```

**risk.escalated**
```json
{
  "eventId": "01HW8Z...",
  "eventType": "risk.escalated",
  "occurredAt": "2025-04-15T11:00:00Z",
  "data": {
    "riskId": "r-001",
    "projectId": "p-001",
    "level": "EXTREME",
    "score": 20,
    "reason": "P 提升至 4",
    "escalatedTo": ["u-100"]
  }
}
```

### A2.10.6 签名与安全

#### 签名头

```
X-PMS-Signature: t=<unix_ts>,v1=<hmac_sha256>
X-PMS-Event-Id: <eventId>
X-PMS-Event-Type: <eventType>
X-PMS-Delivery-Attempt: <int>
```

#### 签名生成

```
signed_payload = `${unix_ts}.${request_body}`
signature = hex(hmac_sha256(signed_payload, endpoint.secret))
```

#### 客户端校验伪代码

```python
def verify(req):
    ts, sig = parse_header(req.headers["X-PMS-Signature"])
    expected = hmac_sha256(f"{ts}.{req.body}", endpoint.secret)
    if not hmac.compare_digest(sig, expected):
        raise 401
    if abs(now() - ts) > 300:  # 5 分钟容差
        raise 408
```

#### 重放保护

- `eventId` 单调递增（UUIDv7），消费方按 `eventId` 去重；
- 超过 24h 投递的消息不再重试，��态置 `EXPIRED`。

### A2.10.7 投递策略

| 参数 | 默认值 | 备注 |
| --- | --- | --- |
| 超时 | 10s | 可配置 5-30s |
| 重试 | 指数退避 1m, 5m, 30m, 2h, 12h, 24h | 最多 6 次 |
| 成功判定 | 2xx | 3xx 视为失败 |
| 限流 | 100 req/s/端点 | 超出排队 |
| 持久化 | Outbox + 投递表 | at-least-once |
| 顺序 | 同一 `aggregateId` 保序 | 跨聚合不保序 |

### A2.10.8 订阅与过滤

```yaml
POST /integrations/int-001/webhook-endpoints
Request:
  url: https://oa.example.com/api/pms/webhook
  eventTypes:
    - "project.*"
    - "workitem.status_changed"
    - "budget.exceeded"
  filter:
    projectId: ["p-001", "p-002"]   # 仅推送指定项目
    secretLevel: ["INTERNAL"]        # 过滤密级
  headers:
    X-Custom-Token: "abc"
Response 201:
  WebhookEndpoint { id: we-001, secret: "whsec_***" }
```

#### 订阅过滤（通配 + 显式）

- `project.*` 匹配 `project.created`、`project.activated` …
- `*.created` 匹配所有 created 事件
- 显式列表：精确匹配
- `filter`：JSON 路径表达式 + 包含/排除规则

### A2.10.9 业务规则

- Webhook 端点必须 HTTPS；
- secret 仅创建时返回一次，需妥善保存；
- 投递失败 6 次后转 `EXPIRED`，进入"死信队列"，可手动重试或禁用端点；
- 同一事件对同一端点的并发投递由 `eventId + endpointId` 去重；
- 大体量事件（带 diff 的 workitem.updated）payload 上限 1MB；
- 投递日志保留 90 天，含原始 payload、响应、耗时；
- 关键事件（如 `budget.exceeded`）支持租户级强制订阅（不能取消）。

---

**Part7 完成。下一步 Part8：系统管理与认证授权。**
