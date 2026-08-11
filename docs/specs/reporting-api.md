---
status: active
created: 2026-08-07
updated: 2026-08-07
summary: 报表域 API 契约(仪表盘 / 数据集 / 报表 / 导出 / 订阅)— 关键路径与业务规则摘要
source: docs/drafts/扩展文档/A2-API规范/A2-API规范-Part6-报表仪表盘统计分析.md (superseded)
---

# 报表域 API 契约(Reporting API Contract)

> 本 Spec 给出报表域的**关键 API 路径 + 核心业务规则**,供实现时对照。
> 完整 DTO / OpenAPI 详见 source 中的 Part6 草案(251 行);
> 实际 OpenAPI 文件待实现时导出为 [`docs/specs/openapi/reporting.yaml`](openapi/)。

---

## 1. 仪表盘(Dashboard)

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/dashboards` | 我的仪表盘列表 |
| POST | `/api/dashboards` | 新建仪表盘 |
| GET | `/api/dashboards/{id}` | 详情 |
| PATCH | `/api/dashboards/{id}` | 更新布局/配置 |
| DELETE | `/api/dashboards/{id}` | 删除 |
| POST | `/api/dashboards/{id}/clone` | 复制 |
| POST | `/api/dashboards/{id}/share` | 分享(链接/角色) |
| GET | `/api/dashboards/role/{roleCode}` | 角色默认仪表盘 |
| GET | `/api/dashboards/{id}/data` | **聚合拉取**(一次返回所有 widget) |

**核心模型**:`Dashboard { id, name, scope[PERSONAL/ROLE/PROJECT/PROGRAM/PORTFOLIO/TENANT], scopeId, ownerId, layout, filters, refreshIntervalSec, isDefault, isShared, shareUrl, updatedAt }`

**核心模型**:`Widget { id, dashboardId, type[KPI_CARD/CHART/TABLE/GANTT/HEATMAP/LIST/IFRAME], chartType[LINE/BAR/PIE/SCATTER/FUNNEL/SANKEY/GAUGE/STACKED], title, dataset, query, config, position{x,y,w,h} }`

---

## 2. 数据集 / 指标语义层

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/datasets` | 数据集列表 |
| POST | `/api/datasets` | 新建数据集 |
| GET | `/api/datasets/{id}` | 详情 + 字段 |
| PATCH | `/api/datasets/{id}` | 更新 |
| DELETE | `/api/datasets/{id}` | 删除 |
| POST | `/api/datasets/{id}/query` | 查询(过滤/分组/聚合) |
| GET | `/api/datasets/{id}/preview` | 预览(前 100 行) |
| GET | `/api/metrics` | 指标字典 |
| GET | `/api/dimensions` | 维度字典 |

**核心模型**:`Dataset { id, name, source[WORK_ITEM/PROJECT/RISK/BUDGET/TIME/USER/CUSTOM], fields, isBuiltIn }`

**预置指标**(11 项,与 reporting.md §2 对齐):

| 指标 | 含义 |
| --- | --- |
| ProjectHealthScore | 项目健康度(进度/成本/范围/风险/质量 加权) |
| RagStatus | RAG 灯(自动规则 + 人工覆盖) |
| ScheduleVariance | 进度偏差 (EV-PV)/PV |
| CostVariance | 成本偏差 (EV-AC)/AC |
| Spi / Cpi | 进度 / 成本绩效指数 |
| ResourceUtilization | 资源利用率 = 已分配工时/产能 |
| BudgetBurnRate | 预算燃烧 = 实际/计划 |
| DefectDensity | 缺陷密度 |
| EscapeRate | 缺陷逃逸率 |
| TaskCompletionRate | 任务完成率 |
| RiskClosureRate | 风险闭环率 |
| OnTimeDelivery | 按时交付率 |

---

## 3. 报表(Report)

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/reports` | 我的报表 |
| POST | `/api/reports` | 新建报表 |
| GET | `/api/reports/{id}` | 配置 |
| PATCH | `/api/reports/{id}` | 更新 |
| DELETE | `/api/reports/{id}` | 删除 |
| POST | `/api/reports/{id}/run` | 执行 |
| GET | `/api/reports/{id}/runs/{runId}` | 执行结果 |
| GET | `/api/reports/{id}/export` | 导出(PDF/XLSX/CSV) |
| POST | `/api/reports/{id}/subscribe` | 订阅 |
| GET | `/api/report-templates` | 预置模板 |

**核心模型**:`Report { id, name, type[STATUS/EVM/RESOURCE/RISK/QUALITY/BUDGET/PORTFOLIO/CUSTOM], datasetId, config, templateId, ownerId, isPublic, schedule, subscribers, lastRunAt }`

**预置模板(10 类)**:
PMO 周报 / 项目状态报告 / EVM 月报 / 资源月报 / 风险季度报告 /
质量季度报告 / 预算执行报告 / 投资组合报告 / 工时合规报告 / 审计与合规报告

---

## 4. 导出(Export)

| 路径 | 行为 |
| --- | --- |
| `GET /api/reports/{id}/export?format=pdf\|xlsx\|csv\|png` | 同步或异步导出 |
| `GET /api/exports/{exportId}` | 查询导出任务状态 |

**导出 SLA**:
- **同步**:`≤ 1000 行 && ≤ 5MB` → 直接 stream 返回
- **异步**:`> 1000 行 || > 5MB` → 返回 `exportId`,轮询/SSE 完成
- 异步任务 SLA < 60s
- 产物保留 7 天(对接 MinIO/OSS)

---

## 5. 订阅(Subscribe)

`POST /api/reports/{id}/subscribe`

请求体:`{ schedule, format, channels[EMAIL/IM], recipients[], includeWatermark }`

- schedule:cron 表达式
- 发送失败重试 3 次(指数退避),失败入"通知失败队列"
- 导出文件带用户水印 + 加密 URL,TTL 默认 24h

---

## 6. 业务规则(强制)

| 规则 | 说明 |
| --- | --- |
| **数据集查询行数** | `rowLimit ≤ 100,000`(超过返回 413) |
| **自定义公式白名单** | 仅 `+ - * / % SUM AVG COUNT MIN MAX IF CASE WHEN` |
| **报表执行超时** | 默认 60s,复杂报表可至 300s |
| **导出加密** | 加密 URL + 用户水印,TTL 24h |
| **仪表盘分享** | 链接含 token,权限继承原始仪表盘 |
| **订阅失败重试** | 3 次指数退避(1s/5s/25s) |

---

## 7. 与现有契约的关系

| 现有 | 关系 |
| --- | --- |
| [`api-contract.md`](api-contract.md) §响应约定 | 复用 `ApiResponse<T>` + 错误码体系 |
| [`api-contract.md`](api-contract.md) §鉴权 | 复用 `Authorization: Bearer <jwt>`;报表写操作需 `PMO_ADMIN/PM/部门负责人` |
| [`api-contract.md`](api-contract.md) §分页 | 数据集查询复用 page/size |

---

## 8. 待澄清(需 WP-M7-01 评审时确认)

- 自助 BI 是否引入第三方引擎(目前定:不引入,用 ECharts + 后端聚合 API)
- 嵌入式 BI 是否支持(目前定:MVP 不支持)
- 数据集是否支持跨数据源 JOIN(目前定:不支持,单 dataset 单 source)
- 预置报表模板的"预置程度"——是否可被用户编辑覆盖
