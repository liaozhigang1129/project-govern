# A2 OpenAPI 3.0 规范 Part5 — 流程引擎与通知

> 本 Part 覆盖 A2.8 工作流引擎、通知、公告、订阅。

---

## A2.8 流程、通知、公告、订阅域

### A2.8.1 工作流引擎

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| **流程定义** | | |
| GET | `/workflow-definitions` | 流程定义列表 |
| POST | `/workflow-definitions` | 新建定义 |
| GET | `/workflow-definitions/{id}` | 详情 |
| PATCH | `/workflow-definitions/{id}` | 更新（仅 DRAFT） |
| POST | `/workflow-definitions/{id}/publish` | 发布 |
| POST | `/workflow-definitions/{id}/deprecate` | 弃用 |
| POST | `/workflow-definitions/{id}/clone` | 复制 |
| GET | `/workflow-definitions/{id}/versions` | 版本历史 |
| GET | `/workflow-definitions/{id}/metrics` | 流程度量（平均耗时、超时率） |
| **流程实例** | | |
| GET | `/workflow-instances` | 实例列表 |
| POST | `/workflow-instances` | 发起流程 |
| GET | `/workflow-instances/{id}` | 详情 |
| POST | `/workflow-instances/{id}/suspend` | 挂起 |
| POST | `/workflow-instances/{id}/resume` | 恢复 |
| POST | `/workflow-instances/{id}/withdraw` | 撤回（发起人） |
| POST | `/workflow-instances/{id}/terminate` | 强制终止（管理员） |
| GET | `/workflow-instances/{id}/history` | 流转历史 |
| GET | `/workflow-instances/{id}/diagram` | 流程图状态 |
| **流程任务（待办）** | | |
| GET | `/workflow-tasks` | 我的待办（支持 filter） |
| POST | `/workflow-tasks/{id}/approve` | 同意 |
| POST | `/workflow-tasks/{id}/reject` | 拒绝 |
| POST | `/workflow-tasks/{id}/return` | 打回 |
| POST | `/workflow-tasks/{id}/add-sign` | 加签 |
| POST | `/workflow-tasks/{id}/transfer` | 转签 |
| POST | `/workflow-tasks/{id}/delegate` | 委托 |
| POST | `/workflow-tasks/{id}/claim` | 认领（候选任务） |
| GET | `/workflow-tasks/{id}/form` | 获取表单 |
| POST | `/workflow-tasks/{id}/form` | 提交表单数据 |

#### 关键 Schema

```yaml
WorkflowDefinition:
  type: object
  properties:
    id: { type: string }
    code: { type: string }
    name: { type: string }
    category: { type: string, enum: [CHARTER, PLAN, BASELINE, CHANGE, RESOURCE, RISK, DOC, REVIEW, EXPENSE, PROCUREMENT, CONTRACT, TIMESHEET, CUSTOM] }
    bpmnXml: { type: string, description: "BPMN 2.0 XML" }
    formSchema: { type: object }
    nodeConfigs: { type: object }
    version: { type: integer }
    status: { type: string, enum: [DRAFT, PUBLISHED, DEPRECATED] }
    timeoutHours: { type: integer, nullable: true }
    isBuiltIn: { type: boolean }

WorkflowInstance:
  type: object
  properties:
    id: { type: string }
    definitionId: { type: string }
    definitionVersion: { type: integer }
    businessKey: { type: string }
    businessType: { type: string }
    businessId: { type: string }
    title: { type: string }
    initiatorId: { type: string }
    status: { type: string, enum: [RUNNING, SUSPENDED, COMPLETED, TERMINATED, WITHDRAWN] }
    currentNodes: { type: array, items: { type: string } }
    variables: { type: object }
    startedAt: { type: string, format: date-time }
    endedAt: { type: string, format: date-time, nullable: true }
    result: { type: string, enum: [APPROVED, REJECTED, CANCELLED] }

WorkflowTask:
  type: object
  properties:
    id: { type: string }
    instanceId: { type: string }
    nodeId: { type: string }
    nodeName: { type: string }
    assigneeId: { type: string, nullable: true }
    candidateIds: { type: array, items: { type: string } }
    ccIds: { type: array, items: { type: string } }
    status: { type: string, enum: [PENDING, IN_PROGRESS, COMPLETED, SKIPPED, TIMEOUT] }
    decision: { type: string, enum: [APPROVE, REJECT, RETURN, ADD_SIGN, TRANSFER, DELEGATE] }
    comment: { type: string, nullable: true }
    formData: { type: object }
    startedAt: { type: string, format: date-time }
    completedAt: { type: string, format: date-time, nullable: true }
    dueAt: { type: string, format: date-time, nullable: true }
    escalated: { type: boolean }
    subTasks: { type: array, items: { $ref: '#/components/schemas/WorkflowTask' } }
```

#### 关键示例

**发起流程**
```yaml
POST /workflow-instances
Request:
  definitionCode: PROJECT_CHARTER
  businessType: PROJECT
  businessId: p-001
  title: 智能客服系统 V2.0 - 立项审批
  variables:
    sponsorId: u-100
    totalBudget: 5000000
  formData:
    projectName: 智能客服系统 V2.0
    targetRoi: 2.4
  attachments: [att-001, att-002]
Response 201:
  WorkflowInstance { id: wi-001, status: RUNNING, currentNodes: ["start", "pm_review"] }
```

**审批**
```yaml
POST /workflow-tasks/wt-001/approve
Request:
  comment: "范围清晰，预算合理"
  formData:
    riskAssessment: "已识别 3 项关键风险"
  notifyWatchers: true
Response 200:
  WorkflowTask { status: COMPLETED, decision: APPROVE, completedAt: ... }
```

**加签**
```yaml
POST /workflow-tasks/wt-001/add-sign
Request:
  addSignUserIds: ["u-201", "u-202"]
  mode: SEQUENTIAL    # 或 PARALLEL
  reason: "需财务复核"
Response 200:
  WorkflowTask { subTasks: [{ assigneeId: "u-201" }, { assigneeId: "u-202" }] }
```

**流程度量**
```yaml
GET /workflow-definitions/wf-001/metrics?from=2025-01-01&to=2025-04-30
Response 200:
  {
    "instanceCount": 156, "completedCount": 142, "timeoutCount": 8,
    "avgDurationHours": 18.4, "p95DurationHours": 72.0,
    "timeoutRate": 0.057, "bottleneckNode": "ccb_decision"
  }
```

---

### A2.8.2 通知（Notification）

| 方法 | 路径 |
| --- | --- |
| GET | `/notifications` | 我的通知列表
| GET | `/notifications/unread-count` | 未读数（轮询用）
| POST | `/notifications/{id}/read` | 标记已读
| POST | `/notifications/bulk-read` | 批量已读
| POST | `/notifications/bulk-archive` | 批量归档
| POST | `/notifications/{id}/archive` | 归档
| DELETE | `/notifications/{id}` | 删除
| GET | `/notifications/preferences` | 通知偏好
| PATCH | `/notifications/preferences` | 更新偏好

```yaml
Notification:
  type: object
  properties:
    id: { type: string }
    userId: { type: string }
    type: { type: string, enum: [SYSTEM, MENTION, ASSIGN, APPROVAL, CHANGE, RISK, ALERT, ANNOUNCEMENT, SUBSCRIPTION] }
    category: { type: string, enum: [TASK, RISK, BUDGET, DOC, REVIEW, WORKFLOW, OTHER] }
    title: { type: string, maxLength: 500 }
    content: { type: string }
    linkUrl: { type: string }
    targetType: { type: string, nullable: true }
    targetId: { type: string, nullable: true }
    priority: { type: string, enum: [LOW, NORMAL, HIGH, URGENT] }
    channels: { type: array, items: { type: string, enum: [INAPP, EMAIL, IM, SMS] } }
    deliveryStatus: { type: object, additionalProperties: true }
    isRead: { type: boolean }
    readAt: { type: string, format: date-time, nullable: true }
    isArchived: { type: boolean }
    createdAt: { type: string, format: date-time }

NotificationPreferences:
  type: object
  properties:
    userId: { type: string }
    channels: { type: object, description: "类型→通道映射" }
    quietHours: { type: object, properties: { startTime: { type: string }, endTime: { type: string }, timezone: { type: string } } }
    digestMode: { type: string, enum: [INSTANT, HOURLY, DAILY, WEEKLY, OFF] }
```

---

### A2.8.3 公告（Announcement）

| 方法 | 路径 |
| --- | --- |
| GET | `/announcements` |
| POST | `/announcements` |
| GET | `/announcements/{id}` |
| PATCH | `/announcements/{id}` |
| POST | `/announcements/{id}/publish` |
| POST | `/announcements/{id}/withdraw` |
| POST | `/announcements/{id}/confirm` | 用户确认
| GET | `/announcements/{id}/receipts` | 回执汇总
| GET | `/my-announcements` | 我的公告

```yaml
Announcement:
  type: object
  properties:
    id: { type: string }
    scope: { type: string, enum: [TENANT, ORG, PROGRAM, PROJECT] }
    scopeId: { type: string, nullable: true }
    title: { type: string, maxLength: 500 }
    content: { type: string }
    type: { type: string, enum: [NOTICE, POLICY, EVENT, OUTAGE, TRAINING, OTHER] }
    priority: { type: string, enum: [LOW, NORMAL, HIGH, URGENT] }
    requireConfirm: { type: boolean }
    authorId: { type: string }
    publishedAt: { type: string, format: date-time, nullable: true }
    expiresAt: { type: string, format: date-time, nullable: true }
    status: { type: string, enum: [DRAFT, PUBLISHED, EXPIRED, WITHDRAWN] }
    channels: { type: array, items: { type: string } }
```

---

### A2.8.4 订阅（Subscription）

| 方法 | 路径 |
| --- | --- |
| GET | `/subscriptions` |
| POST | `/subscriptions` |
| DELETE | `/subscriptions/{id}` |
| PATCH | `/subscriptions/{id}` | 启停

```yaml
Subscription:
  type: object
  properties:
    id: { type: string }
    userId: { type: string }
    targetType: { type: string, enum: [PROJECT, TASK, RISK, DOC, REVIEW] }
    targetId: { type: string }
    eventTypes: { type: array, items: { type: string } }
    channels: { type: array, items: { type: string, enum: [INAPP, EMAIL, IM, SMS] } }
    enabled: { type: boolean }
```

---

### A2.8.5 业务规则

- **会签**：所有候选人必须审批，节点状态 = 全部完成；
- **或签**：任一候选人完成即结束；
- **加签 SEQUENTIAL**：按顺序流转；PARALLEL：并行；
- **超时升级**：节点 `dueAt` 过期未处理 → 触发 `escalation_rule`（重指派/抄送上级）；
- **撤回规则**：仅 RUNNING 状态、发起人本人在流程未到达终态时可撤回；
- **强制终止**：仅 SysAdmin/PMO 可用，需填写 `reason`；
- **未读数实时性**：站内通知 5s 内到达，IM 1min 内，邮件 5min 内；
- **勿扰时段**：在 quiet_hours 内不发送非紧急通道（IM/Email 累积到下一个工作时段）；
- **强回执**：`requireConfirm=true` 公告必须 ack 才计"已传达"。

---

**Part5 完成。下一步 Part6：报表与仪表盘。**
