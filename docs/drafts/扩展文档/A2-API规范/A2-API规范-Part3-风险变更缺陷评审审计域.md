# A2 OpenAPI 3.0 规范 Part3 — 风险/问题/变更/缺陷/评审/审计

> 本节覆盖 A2.5 ~ A2.6。承接 Part1/Part2。

---

## A2.5 风险/问题/变更/缺陷/评审域

### A2.5.1 风险（Risk）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/projects/{id}/risks` | 风险列表 |
| POST | `/projects/{id}/risks` | 新建风险 |
| GET | `/risks/{id}` | 详情 |
| PATCH | `/risks/{id}` | 更新 |
| DELETE | `/risks/{id}` | 归档 |
| POST | `/risks/{id}/responses` | 新增应对措施 |
| PATCH | `/risks/{id}/responses/{respId}` | 更新措施 |
| POST | `/risks/{id}/responses/{respId}/complete` | 完成措施 |
| POST | `/risks/{id}/escalate` | 升级 |
| POST | `/risks/{id}/close` | 关闭（含原因） |
| POST | `/risks/{id}/reassess` | 复评（重算 P/I） |
| POST | `/risks/{id}/materialize` | 风险事件化（→Issue） |
| GET | `/risks/{id}/history` | 风险历史 |
| GET | `/risks/matrix?projectId=...` | 风险热力图（P×I 矩阵） |
| GET | `/risks/heatmap?scope=portfolio&period=...` | 组合级热力 |

#### Schema 摘要

```yaml
Risk:
  type: object
  required: [projectId, title, category, source, probability, impact, ownerId, strategy]
  properties:
    id: { type: string }
    code: { type: string }
    projectId: { type: string }
    title: { type: string, maxLength: 200 }
    description: { type: string }
    category: { type: string, enum: [TECH, RESOURCE, SCHEDULE, COST, SCOPE, QUALITY, COMPLIANCE, EXTERNAL, OTHER] }
    source: { type: string, enum: [INTERNAL, EXTERNAL] }
    probability: { type: integer, minimum: 1, maximum: 5 }
    impact: { type: integer, minimum: 1, maximum: 5 }
    score: { type: integer, readOnly: true, description: "= P × I" }
    level: { type: string, readOnly: true, enum: [LOW, MEDIUM, HIGH, EXTREME] }
    triggerCondition: { type: string }
    ownerId: { type: string }
    strategy: { type: string, enum: [AVOID, MITIGATE, TRANSFER, ACCEPT, EXPLOIT, SHARE] }
    status: { type: string, enum: [IDENTIFIED, ANALYZING, RESPONDING, CLOSED, MATERIALIZED] }
    nextReviewAt: { type: string, format: date, nullable: true }
    tags: { type: array, items: { type: string } }
    linkedItems:
      type: array
      items: { $ref: '#/components/schemas/LinkedItemRef' }
```

#### 关键示例

**新建风险（自动计算 score & level）**
```yaml
POST /projects/p-001/risks
Request:
  title: 核心研发人员 A 离职风险
  category: RESOURCE
  source: INTERNAL
  probability: 3
  impact: 5
  ownerId: u-pm01
  strategy: MITIGATE
  triggerCondition: A 提交辞职信或连续请假 ≥ 5 天
  nextReviewAt: 2025-05-15
Response 201:
  Risk { id: r-001, score: 15, level: EXTREME, status: IDENTIFIED }
```

**复评**
```yaml
POST /risks/r-001/reassess
Request:
  probability: 4
  impact: 5
  trigger: EVENT
  comment: A 已提离职
Response 200:
  Risk { score: 20, level: EXTREME, history: [...] }
```

**风险矩阵（热力）**
```yaml
GET /risks/matrix?projectId=p-001
Response 200:
  {
    "matrix": [
      [{count:0},{count:0},{count:0},{count:1},{count:2}],
      [{count:0},{count:1},{count:3},{count:0},{count:0}],
      ...
    ],
    "summary": { "total": 12, "extreme": 3, "high": 4 }
  }
```

---

### A2.5.2 问题（Issue）

| 方法 | 路径 |
| --- | --- |
| GET | `/projects/{id}/issues` |
| POST | `/projects/{id}/issues` |
| GET | `/issues/{id}` |
| PATCH | `/issues/{id}` |
| POST | `/issues/{id}/assign` |
| POST | `/issues/{id}/resolve` |
| POST | `/issues/{id}/verify` |
| POST | `/issues/{id}/close` |
| POST | `/issues/{id}/reopen` |
| POST | `/issues/{id}/convert-to-risk` |
| POST | `/issues/{id}/convert-to-change` |

```yaml
Issue:
  type: object
  properties:
    id: { type: string }
    code: { type: string }
    projectId: { type: string }
    title: { type: string }
    type: { type: string, enum: [BLOCKER, MAJOR, MINOR, INFO] }
    severity: { type: string, enum: [S1, S2, S3, S4] }
    priority: { type: string, enum: [P0, P1, P2, P3] }
    ownerId: { type: string }
    slaDue: { type: string, format: date-time, nullable: true }
    rootCause: { type: string, enum: [PEOPLE, PROCESS, TECH, EXTERNAL, OTHER] }
    status: { type: string, enum: [OPEN, IN_PROGRESS, RESOLVED, VERIFIED, CLOSED, REOPENED] }
```

---

### A2.5.3 变更（Change Request）

| 方法 | 路径 |
| --- | --- |
| GET | `/projects/{id}/changes` |
| POST | `/projects/{id}/changes` |
| GET | `/changes/{id}` |
| PATCH | `/changes/{id}` |
| POST | `/changes/{id}/submit` |
| POST | `/changes/{id}/ccb-decide` |
| POST | `/changes/{id}/implement` |
| POST | `/changes/{id}/complete` |
| POST | `/changes/{id}/withdraw` |
| POST | `/changes/{id}/impact-analysis` | 预演影响
| GET | `/changes/{id}/impact-analysis` | 取影响快照
| POST | `/changes/{id}/approve-fast` | 紧急通道

```yaml
ChangeRequest:
  type: object
  required: [projectId, title, type, impactLevel, reason, proposedSolution]
  properties:
    id: { type: string }
    code: { type: string }
    projectId: { type: string }
    title: { type: string }
    type: { type: string, enum: [SCOPE, SCHEDULE, COST, RESOURCE, QUALITY, BASELINE, CONTRACT, OTHER] }
    impactLevel: { type: string, enum: [MINOR, MAJOR, CRITICAL] }
    reason: { type: string }
    impactScope: { type: string }
    impactScheduleDays: { type: integer }
    impactCost: { type: number }
    impactResource: { type: string }
    impactQuality: { type: string }
    impactRisk: { type: string }
    proposedSolution: { type: string }
    alternatives: { type: string }
    status: { type: string, enum: [DRAFT, SUBMITTED, IN_REVIEW, APPROVED, REJECTED, IMPLEMENTING, COMPLETED, CANCELLED] }
    decision: { type: string, enum: [APPROVED, REJECTED, DEFERRED] }
    isEmergency: { type: boolean }
    ccbMeetingAt: { type: string, format: date-time, nullable: true }
    items:
      type: array
      items:
        type: object
        properties:
          itemType: { type: string, enum: [WBS, TASK, MILESTONE, BUDGET, RESOURCE, DELIVERABLE, RISK] }
          itemId: { type: string }
          changeType: { type: string, enum: [ADD, MODIFY, REMOVE] }
          before: { type: object }
          after: { type: object }
```

**预演影响（不落库）**
```yaml
POST /changes/{id}/impact-analysis
Response 200:
  {
    "schedule": { "newEndDate": "2025-08-15", "delayDays": 15 },
    "cost": { "delta": 380000, "fromBudgetId": "b-001", "willExceed": false },
    "resource": [
      { "userId": "u-301", "fromAllocation": 50, "toAllocation": 80, "conflict": false }
    ],
    "risk": [
      { "type": "QUALITY", "probability": 2, "impact": 3, "newRisks": ["r-new01"] }
    ]
  }
```

**CCB 决策**
```yaml
POST /changes/cr-001/ccb-decide
Request:
  decision: APPROVED
  comment: 有条件通过，需在 5 月 30 日前完成验证
  conditions:
    - 提交补充安全测试报告
    - 更新用户培训材料
  effectiveDate: 2025-05-15
Response 200:
  ChangeRequest { status: APPROVED, decision: APPROVED, decisionAt: ... }
```

---

### A2.5.4 缺陷（Defect）

| 方法 | 路径 |
| --- | --- |
| GET | `/projects/{id}/defects` |
| POST | `/projects/{id}/defects` |
| GET | `/defects/{id}` |
| PATCH | `/defects/{id}` |
| POST | `/defects/{id}/assign` |
| POST | `/defects/{id}/fix` |
| POST | `/defects/{id}/verify` |
| POST | `/defects/{id}/close` |
| POST | `/defects/{id}/reopen` |
| POST | `/defects/{id}/wontfix` |
| GET | `/defects/{id}/sla` | SLA 状态
| GET | `/defects/metrics?projectId=...&from=...&to=...` | 缺陷度量

```yaml
Defect:
  type: object
  properties:
    id: { type: string }
    code: { type: string }
    projectId: { type: string }
    title: { type: string, maxLength: 500 }
    description: { type: string }
    stepsToReproduce: { type: string }
    expected: { type: string }
    actual: { type: string }
    severity: { type: string, enum: [S1, S2, S3, S4] }
    priority: { type: string, enum: [P0, P1, P2, P3] }
    type: { type: string, enum: [FUNCTIONAL, PERFORMANCE, SECURITY, UI, COMPATIBILITY, DATA, OTHER] }
    environment: { type: string, enum: [DEV, TEST, STAGING, PROD] }
    module: { type: string }
    version: { type: string }
    fixedVersion: { type: string, nullable: true }
    status: { type: string, enum: [NEW, ASSIGNED, IN_PROGRESS, FIXED, VERIFIED, CLOSED, REOPENED, WONT_FIX] }
    resolution: { type: string, enum: [FIXED, WONT_FIX, DUPLICATE, CANNOT_REPRODUCE, BY_DESIGN] }
    slaResponseDue: { type: string, format: date-time }
    slaFixDue: { type: string, format: date-time }
    source: { type: string, enum: [MANUAL, IM, EMAIL, ALM_SYNC, AUTO_TEST] }
    externalId: { type: string, nullable: true }
    almSystem: { type: string, nullable: true }
```

**与 ALM 双向同步（预留）**
```yaml
POST /defects/sync/from-alm
Request:
  system: JIRA
  projectKey: PRJ
  since: 2025-04-01T00:00:00Z
Response 200:
  { "synced": 23, "created": 5, "updated": 18, "failed": 0 }
```

---

### A2.5.5 评审（Review）

| 方法 | 路径 |
| --- | --- |
| GET | `/projects/{id}/reviews` |
| POST | `/projects/{id}/reviews` |
| GET | `/reviews/{id}` |
| PATCH | `/reviews/{id}` |
| POST | `/reviews/{id}/start` |
| POST | `/reviews/{id}/score` | 提交打分
| POST | `/reviews/{id}/decide` | 决议
| POST | `/reviews/{id}/cancel` |
| GET | `/reviews/{id}/materials` |
| POST | `/reviews/{id}/action-items` | 转行动项

```yaml
Review:
  type: object
  properties:
    id: { type: string }
    projectId: { type: string }
    type: { type: string, enum: [CHARTER, STAGE_GATE, DESIGN, CODE, ACCEPTANCE, CLOSING, OTHER] }
    name: { type: string }
    plannedAt: { type: string, format: date-time }
    actualAt: { type: string, format: date-time, nullable: true }
    chairId: { type: string }
    reviewerIds: { type: array, items: { type: string } }
    materials: { type: array, items: { $ref: '#/components/schemas/AttachmentRef' } }
    criteria: { type: object, description: "多维度评分项" }
    status: { type: string, enum: [PLANNED, IN_PROGRESS, COMPLETED, CANCELLED] }
    decision: { type: string, enum: [PASS, CONDITIONAL, FAIL, DEFERRED] }
    fromPhase: { type: string, nullable: true }
    toPhase: { type: string, nullable: true }
```

**打分**
```yaml
POST /reviews/rv-001/score
Request:
  scores:
    - { dimension: "范围清晰度", score: 8, comment: "总体清晰" }
    - { dimension: "进度合理性", score: 6, comment: "关键路径偏紧" }
    - { dimension: "风险识别", score: 9 }
  decision: APPROVE
  concerns: "需补充 S3 风险应对"
Response 200:
  Review { status: IN_PROGRESS }
```

---

### A2.5.6 审计（Audit）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/audit-logs` | 查询（仅审计角色） |
| GET | `/audit-logs/{id}` | 详情 |
| GET | `/audit-logs/export` | 导出（带水印） |
| POST | `/audit-logs/verify-chain` | 哈希链校验 |

```yaml
AuditLog:
  type: object
  properties:
    id: { type: string }
    tenantId: { type: string }
    userId: { type: string }
    userName: { type: string }
    action: { type: string, enum: [CREATE, READ, UPDATE, DELETE, LOGIN, LOGOUT, EXPORT, PRINT, APPROVE, REJECT, ESCALATE, ASSIGN, ...] }
    targetType: { type: string, enum: [PROJECT, TASK, RISK, ISSUE, BUDGET, ...] }
    targetId: { type: string }
    before: { type: object }
    after: { type: object }
    diff: { type: object }
    ip: { type: string }
    userAgent: { type: string }
    device: { type: string, enum: [WEB, MOBILE, API, IM, BOT] }
    requestId: { type: string }
    occurredAt: { type: string, format: date-time }
    sensitive: { type: boolean }
    reason: { type: string, nullable: true }
```

**查询**
```yaml
GET /audit-logs?targetType=PROJECT&targetId=p-001&action=UPDATE&from=2025-04-01&to=2025-04-30&size=50
Response 200:
  {
    "items": [
      {
        "id": "al-001", "userName": "张三", "action": "UPDATE",
        "before": { "status": "ACTIVE" }, "after": { "status": "SUSPENDING" },
        "diff": { "status": { "from": "ACTIVE", "to": "SUSPENDING" } },
        "ip": "10.0.0.5", "occurredAt": "2025-04-15T03:22:11Z"
      }
    ],
    "total": 138
  }
```

---
