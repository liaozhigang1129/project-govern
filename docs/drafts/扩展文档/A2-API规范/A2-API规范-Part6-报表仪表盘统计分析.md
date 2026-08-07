# A2 OpenAPI 3.0 规范 Part6 — 报表、仪表盘、统计分析

> 本 Part 覆盖 A2.9 报表分析域。

---

## A2.9 报表、仪表盘、统计分析域

### A2.9.1 仪表盘（Dashboard）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/dashboards` | 我的仪表盘列表 |
| POST | `/dashboards` | 新建仪表盘 |
| GET | `/dashboards/{id}` | 详情 |
| PATCH | `/dashboards/{id}` | 更新布局/配置 |
| DELETE | `/dashboards/{id}` | 删除 |
| POST | `/dashboards/{id}/clone` | 复制 |
| POST | `/dashboards/{id}/share` | 分享（链接/角色） |
| GET | `/dashboards/role/{role}` | 角色默认仪表盘 |
| GET | `/dashboards/{id}/data` | 仪表盘聚合数据（一次拉取所有 widget） |

```yaml
Dashboard:
  type: object
  properties:
    id: { type: string }
    name: { type: string }
    scope: { type: string, enum: [PERSONAL, ROLE, PROJECT, PROGRAM, PORTFOLIO, TENANT] }
    scopeId: { type: string, nullable: true }
    ownerId: { type: string }
    layout: { type: object, description: "{ cols: 12, rows: N, widgets: [...] }" }
    filters: { type: object }
    refreshIntervalSec: { type: integer, default: 60 }
    isDefault: { type: boolean }
    isShared: { type: boolean }
    shareUrl: { type: string, nullable: true }
    updatedAt: { type: string, format: date-time }

Widget:
  type: object
  properties:
    id: { type: string }
    dashboardId: { type: string }
    type: { type: string, enum: [KPI_CARD, CHART, TABLE, GANTT, HEATMAP, LIST, IFRAME] }
    chartType: { type: string, enum: [LINE, BAR, PIE, SCATTER, FUNNEL, SANKEY, GAUGE, STACKED] }
    title: { type: string }
    dataset: { type: string, description: "数据集/查询 ID" }
    query: { type: object, description: "自定义查询（高级）" }
    config: { type: object, description: "图表配置" }
    position: { type: object, properties: { x: {type:integer}, y: {type:integer}, w: {type:integer}, h: {type:integer} } }
```

#### 关键示例

**聚合拉取（单次返回所有 widget 数据）**
```yaml
GET /dashboards/db-001/data?refresh=true
Response 200:
  {
    "dashboardId": "db-001",
    "refreshedAt": "2025-04-15T10:00:00Z",
    "widgets": [
      { "widgetId": "w-1", "type": "KPI_CARD", "data": { "value": 12, "delta": -2, "unit": "个" } },
      { "widgetId": "w-2", "type": "CHART", "data": { "series": [...], "xAxis": [...], "yAxis": [...] } }
    ]
  }
```

---

### A2.9.2 数据集 / 指标语义层

| 方法 | 路径 |
| --- | --- |
| GET | `/datasets` |
| POST | `/datasets` | 新建数据集
| GET | `/datasets/{id}` | 详情 + 字段
| PATCH | `/datasets/{id}` |
| DELETE | `/datasets/{id}` |
| POST | `/datasets/{id}/query` | 查询（带过滤/分组/聚合）
| GET | `/datasets/{id}/preview` | 预览（前 100 行）
| GET | `/metrics` | 指标字典
| GET | `/dimensions` | 维度字典

```yaml
Dataset:
  type: object
  properties:
    id: { type: string }
    name: { type: string }
    source: { type: string, enum: [WORK_ITEM, PROJECT, RISK, BUDGET, TIME, USER, CUSTOM] }
    fields: { type: array, items: { $ref: '#/components/schemas/DatasetField' } }
    isBuiltIn: { type: boolean }

DatasetField:
  type: object
  properties:
    name: { type: string }
    label: { type: string }
    type: { type: string, enum: [STRING, NUMBER, DATE, BOOLEAN, REF, JSON] }
    aggregatable: { type: boolean }
    filterable: { type: boolean }
    groupable: { type: boolean }
    expression: { type: string, nullable: true, description: "派生字段表达式" }
```

#### 预置指标（与 SRS §10 对齐）

| 指标 | 含义 | 计算 |
| --- | --- | --- |
| ProjectHealthScore | 项目健康度 | 进度/成本/范围/风险/质量 加权 |
| RagStatus | 灯 | 自动规则 + 人工覆盖 |
| ScheduleVariance | 进度偏差 | (EV-PV)/PV |
| CostVariance | 成本偏差 | (EV-AC)/AC |
| Spi | 进度绩效 | EV/PV |
| Cpi | 成本绩效 | EV/AC |
| ResourceUtilization | 资源利用率 | 已分配工时/产能 |
| BudgetBurnRate | 预算燃烧 | 实际/计划 |
| DefectDensity | 缺陷密度 | 缺陷数/规模 |
| EscapeRate | 缺陷逃逸率 | 生产缺陷/(生产+测试) |
| TaskCompletionRate | 任务完成率 | Done/(Done+InProgress+Todo) |
| RiskClosureRate | 风险闭环率 | 关闭/(关闭+识别中) |
| OnTimeDelivery | 按时交付率 | 准时里程碑/总里程碑 |
```

---

### A2.9.3 报表

| 方法 | 路径 |
| --- | --- |
| GET | `/reports` |
| POST | `/reports` | 新建报表
| GET | `/reports/{id}` | 配置
| PATCH | `/reports/{id}` |
| DELETE | `/reports/{id}` |
| POST | `/reports/{id}/run` | 执行
| GET | `/reports/{id}/runs/{runId}` | 执行结果
| GET | `/reports/{id}/export?format=pdf|xlsx|csv` | 导出
| POST | `/reports/{id}/subscribe` | 订阅
| GET | `/report-templates` | 预置模板

```yaml
Report:
  type: object
  properties:
    id: { type: string }
    name: { type: string }
    type: { type: string, enum: [STATUS, EVM, RESOURCE, RISK, QUALITY, BUDGET, PORTFOLIO, CUSTOM] }
    datasetId: { type: string }
    config: { type: object, description: "过滤/分组/聚合/排序/格式" }
    templateId: { type: string, nullable: true }
    ownerId: { type: string }
    isPublic: { type: boolean }
    schedule: { type: string, nullable: true, description: "Cron 表达式" }
    subscribers: { type: array, items: { type: string } }
    lastRunAt: { type: string, format: date-time, nullable: true }
```

**预置报表模板清单**（与 SRS §10.2 对齐）：

| 报表名 | 维度 | 关键指标 |
| --- | --- | --- |
| PMO 周报 | 项目组合 | RAG、进度偏差、Top 5 风险、待办事项 |
| 项目状态报告 | 项目 | 健康度、里程碑、预算偏差、Sprint 燃尽 |
| EVM 月报 | 项目 | PV/EV/AC、SPI/CPI、TCPI、趋势图 |
| 资源月报 | 部门 + 月份 | 利用率热力、冲突数、未来空缺 |
| 风险季度报告 | 组合 | P×I 热力、Top 10、闭环率 |
| 质量季度报告 | 项目 | 缺陷密度、逃逸率、SLA 达成 |
| 预算执行报告 | 项目 + 月份 | 预算 vs 实际、EAC、VAC |
| 投资组合报告 | Portfolio | 投资分布、战略对齐、收益跟踪 |
| 工时合规报告 | 部门 | 填报率、超时、漏报 |
| 审计与合规报告 | 租户 | 登录、敏感访问、变更追溯 |

---

### A2.9.4 关键示例

**项目健康度聚合**
```yaml
GET /datasets/PROJECT/query
  ?metrics=ProjectHealthScore,RagStatus,ScheduleVariance,CostVariance
  &groupBy=BusinessUnit,RagStatus
  &filter[status]=ACTIVE
  &period=2025-Q2
Response 200:
  {
    "columns": ["businessUnit", "rag", "count", "avgHealth", "avgSv", "avgCv"],
    "rows": [
      { "businessUnit": "BU-30", "rag": "GREEN", "count": 5, "avgHealth": 87, "avgSv": 0.04, "avgCv": 0.06 },
      { "businessUnit": "BU-30", "rag": "AMBER", "count": 2, "avgHealth": 65, "avgSv": -0.12, "avgCv": -0.08 }
    ]
  }
```

**EVM 趋势**
```yaml
POST /reports/evm-monthly/run
Request:
  projectIds: ["p-001", "p-002", "p-003"]
  from: 2025-01-01
  to: 2025-04-30
  granularity: MONTH
Response 200:
  ReportRun {
    id: rr-001,
    status: COMPLETED,
    dataUrl: "/report-runs/rr-001/data",
    duration: 3.2
  }
```

**导出（异步）**
```yaml
GET /reports/evm-monthly/export?runId=rr-001&format=xlsx
Response 202:
  { "exportId": "exp-001", "status": "PROCESSING", "pollUrl": "/exports/exp-001" }

GET /exports/exp-001
Response 200:
  { "status": "READY", "fileUrl": "https://oss/.../exp-001.xlsx", "expiresAt": "..." }
```

**订阅**
```yaml
POST /reports/r-001/subscribe
Request:
  schedule: "0 9 * * MON"   # 每周一 9 点
  format: PDF
  channels: [EMAIL, IM]
  recipients: ["u-100", "u-200"]
  includeWatermark: true
Response 201:
  ReportSubscription { id: rs-001, schedule: "0 9 * * MON" }
```

---

### A2.9.5 业务规则

- 数据集查询必须强制 `rowLimit ≤ 100,000`；
- 自定义指标公式白名单（`+ - * / % SUM AVG COUNT MIN MAX IF CASE WHEN`）；
- 报表执行超时：默认 60s，复杂报表可至 300s；
- 导出文件带用户水印 + 加密 URL，TTL 默认 24h；
- 仪表盘分享链接含 token，权限继承原始仪表盘；
- 订阅发送失败重试 3 次（指数退避），失败入"通知失败队列"。

---

**Part6 完成。下一步 Part7：Webhook 事件契约。**
