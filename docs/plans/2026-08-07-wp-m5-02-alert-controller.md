---
status: active
created: 2026-08-07
updated: 2026-08-07
summary: WP-M5-02 预警控制器 + 触发器实现计划
---

# Plan · WP-M5-02 预警控制器 + 触发器

> 对应 WBS 工作包:[`WP-M5-02`](../WBS.md#wp-m5-02-预警控制器--触发器)
> 对应里程碑:**M5**(预警数据层 + 控制器)
> 当前状态:**active**(2026-08-07 启动)

---

## 1. 目标与范围

### 1.1 一句话

在 `alert` 数据层(已就绪)之上补齐 `AlertController` + 6 类规则触发器,前端 `AlertDashboard` 能看到实时告警列表与按规则类型的分布。

### 1.2 范围内

- `AlertController` 端点:`GET /api/alerts` / `GET /api/alerts/{id}` / `POST /api/alerts/{id}/ack` / `POST /api/alerts/{id}/resolve` / `GET /api/alerts/stats`
- 6 类规则触发器(见 [specs/legacy/PRD-cost-control.md §3](../specs/legacy/PRD-cost-control.md)):
  1. `cost_overrun`:成本超支(月累计 > 预算 90%)
  2. `schedule_delay`:进度落后(实际完成 % < 计划完成 % - 10)
  3. `quality_issue`:质量事件(缺陷率 > 阈值)
  4. `risk_escalation`:风险升级(评分跨级)
  5. `resource_overload`:资源过载(单用户工时 > 50h/week)
  6. `compliance_violation`:合规违规(审批超时 / 字典缺失)
- 触发调度:`AlertScheduler` 每 5 分钟跑批(`@Scheduled(fixedDelay = 300_000)`)
- 告警通道:复用 M6 NotificationDispatcher(邮件 + IM),独立通道开关
- 前端:`AlertDashboard.vue` + `AlertList.vue` + `AlertDetailDrawer.vue`

### 1.3 出范围

- 规则可视化编辑器(留 v5 治理)
- 告警压缩 / 抑制(留 v5)
- 智能根因分析(留 v5 AI)

---

## 2. 实现步骤

### T-01 AlertController 基础 CRUD

- `AlertController` 5 个端点,分页查询 + 详情
- `@RequireRoles("PMO_ADMIN")` 仅 PMO_ADMIN / ALERT_ADMIN 可见
- `@AuditLog` 写操作(ack / resolve)
- 单测:`AlertControllerTest` 6 case(列表 / 详情 / ack / resolve / stats / 401)
- 验证:`mvn -B test`

### T-02 规则引擎抽象

- 接口 `AlertRule { String getCode(); boolean matches(AlertContext ctx); AlertSeverity severity(); }`
- 抽象类 `AbstractSqlAlertRule`,6 类规则各自实现
- `AlertRuleRegistry` 注册 6 类规则(Spring `@Component` 自动注入)
- 单测:`AlertRuleRegistryTest` 验证 6 规则都注册成功
- 验证:`mvn -B test`

### T-03 6 类规则实现

每个规则独立 commit:

| 规则 | 触发 SQL / 逻辑 | 严重度 |
|---|---|---|
| `cost_overrun` | `SUM(cost_item WHERE project_id AND period) > budget * 0.9` | HIGH |
| `schedule_delay` | `milestone_completion_pct < plan_pct - 10` | MEDIUM |
| `quality_issue` | `COUNT(defect WHERE project_id AND severity>='MAJOR') > 5` | HIGH |
| `risk_escalation` | `risk.level 跨级上升(从 LOW/MEDIUM → HIGH/CRITICAL)` | CRITICAL |
| `resource_overload` | `SUM(timesheet WHERE user_id AND week) > 50` | MEDIUM |
| `compliance_violation` | `审批超时 24h / 字典启用但无值` | LOW |

- 每个规则:`*AlertRuleTest` 至少 2 case(触发 / 不触发)
- 验证:`mvn -B test`

### T-04 AlertScheduler 调度

- `AlertScheduler.scan()` 每 5 分钟跑:
  1. 拉所有项目
  2. 对每个项目跑 6 类规则
  3. 触发写 `alert` 表(`severity` / `rule_code` / `project_id` / `context_json`)
  4. 通过 `NotificationDispatcher` 发邮件 / IM
- `@Scheduled` + `@EnableScheduling`
- 幂等:同 `(project_id, rule_code, period)` 5 分钟内不重复触发
- 单测:`AlertSchedulerTest` 验证幂等
- 验证:`mvn -B test`

### T-05 前端

- `frontend/src/views/AlertDashboard.vue`:统计卡片(总告警 / 待 ack / 高危数)
- `frontend/src/views/AlertList.vue`:列表,按规则类型 / 严重度 / 时间筛选
- `frontend/src/components/AlertDetailDrawer.vue`:详情抽屉,显示 context_json
- `frontend/src/api/alert.ts`:5 个 API 客户端
- 路由 + 菜单权限
- 验证:`pnpm build` + `pnpm exec vue-tsc --noEmit`

### T-06 E2E 冒烟

- `scripts/business-smoke.sh` 新增告警冒烟:
  - 造一个成本超支场景 → 等 5 分钟调度 → 验证 alert 表有 1 行
  - 验证邮件到达 mailpit
- 验证:`bash scripts/business-smoke.sh`

---

## 3. 验收标准

### 3.1 必过

| 项 | 标准 |
|---|---|
| 后端单测 | `mvn -B test` 全绿,新增 `AlertControllerTest` + 6 个 `*AlertRuleTest` + `AlertSchedulerTest` ≥ 14 case |
| CI 5 job | 全绿(backend-test / build / frontend / smoke / docs-lint) |
| 规则覆盖 | 6 类规则全部实现,每类至少 1 触发 case + 1 不触发 case |
| 调度稳定 | AlertScheduler 连续跑 1h 无崩溃,日志无 ERROR |

### 3.2 应过

| 项 | 标准 |
|---|---|
| 业务冒烟 | business-smoke 告警冒烟全绿 |
| 前端构建 | 无 warning |
| 告警到达 | 邮件 mailpit 收到测试告警 |

### 3.3 不做

- 规则可视化编辑器
- 告警压缩 / 抑制
- AI 根因分析

---

## 4. 风险与缓解

| ID | 风险 | 缓解 |
|---|---|---|
| R-006 | 5 分钟调度在数据量大时跑不完 | 加分布式锁(Redis SETNX),跑不完则跳过本次,下次继续 |
| R-007 | 告警风暴(同一规则 100 条) | 加去重窗口 + 单项目单规则每小时最多 1 条 |
| R-008 | 邮件 / IM 通道失败导致告警丢失 | 告警先入库,通道失败仅 warn log,后台补偿 job 重试 |

---

## 5. 进度节点(预估)

| 节点 | 日期 |
|---|---|
| T-01 AlertController | 2026-08-12 |
| T-02 规则引擎抽象 | 2026-08-14 |
| T-03 6 类规则实现 | 2026-08-20 |
| T-04 AlertScheduler | 2026-08-22 |
| T-05 前端 | 2026-08-26 |
| T-06 E2E 冒烟 | 2026-08-28 |
| **M5 门禁** | **2026-08-28**(见 STATUS.md) |

---

## 6. 完成后处置

- WBS.md:WP-M5-02 `Plan:` 填本文件名
- STATUS.md:M5 进度 `60% → 100%`,风险 R-006/007/008 关闭,门禁移到 M4 财务闭环完成
- CHANGELOG.md:追加 `[M5.0]` 段
- ADR:若规则引擎产生方向性变化(比如改严重度算法),追加 ADR
