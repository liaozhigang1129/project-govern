# PR-5: P3 API 与非功能

> **版本**: v0.1 (草稿)
> **作者**: PMO 研发组
> **评审**: @后端 @QA @架构师 @SRE
> **更新**: 2025-06-10
> **状态**: ⏳ 评审中
> **依赖**: PR-1 (角色), PR-2 (数据), PR-3 (功能), PR-4 (可视化)

---

## 1. API 设计原则

### 1.1 URL 设计

- **前缀**: 全部 `/api/...` (走 API 网关, 与前端 Vite proxy 对齐)
- **资源平铺**: 子资源最多 2 层, 不深嵌套
  - ✅ `GET /api/wbs/tasks/by-project/{id}`
  - ❌ `GET /api/projects/{id}/wbs/tasks` (太深)
- **动作明确**: 动词放 method, 路径放名词
  - ✅ `POST /api/wbs/tasks` (创建)
  - ✅ `POST /api/wbs/tasks/{id}/move` (动作, 子资源)
- **过滤参数**: 用 `?prob=4&impact=5`, 不进路径

### 1.2 响应包装

```typescript
// 成功
{
  code: 0,
  message: "success",
  data: T
}

// 失败
{
  code: 4001,
  message: "WBS-001: 项目内 wbsCode 已存在",
  data: null
}
```

- `code`: 0=成功, 非 0=业务错误码 (见 §3)
- `message`: 中文友好提示, 前端直接 ElMessage.error
- `data`: 业务数据, 类型随端点

### 1.3 鉴权

```java
// Controller 上
@RequireRoles.Read       // 任意已登录角色可读
@RequireRoles.Operate    // PMO_ADMIN/PM 可写
```

**5 角色注解**:
- `Read`: 所有已登录用户
- `Operate`: PMO_ADMIN + PM
- `ReadSensitive`: PMO_ADMIN + PM + EXEC (风险矩阵/健康度)

## 2. 30 个 endpoint 全清单

### 2.1 WBS 模块 (20 个)

| # | 方法 | 路径 | 角色 | 用途 | 限流 |
|:---:|---|---|:---:|---|:---:|
| 1 | GET | /wbs/tasks/by-project/{projectId} | Read | 任务树 | — |
| 2 | GET | /wbs/tasks/flat/by-project/{projectId} | Read | 任务扁平 | — |
| 3 | GET | /wbs/tasks/{id} | Read | 任务详情 | — |
| 4 | POST | /wbs/tasks | Operate | 新建/更新 | 30/min |
| 5 | DELETE | /wbs/tasks/{id} | Operate | 软删除 | 30/min |
| 6 | POST | /wbs/tasks/{id}/move | Operate | 拖拽移动 | 60/min |
| 7 | POST | /wbs/projects/{projectId}/auto-reorder | Operate | 自动重排 | 10/min |
| 8 | GET | /wbs/progress/{projectId} | Read | 进度汇总 | — |
| 9 | GET | /wbs/assignments/by-task/{wbsTaskId} | Read | 任务分配 | — |
| 10 | GET | /wbs/assignments/by-user/{userId} | Read | 用户任务 | — |
| 11 | GET | /wbs/assignments/by-project/{projectId} | Read | 项目分配 | — |
| 12 | POST | /wbs/assignments | Operate | upsert | 60/min |
| 13 | DELETE | /wbs/assignments/{id} | Operate | 软删 | 30/min |
| 14 | GET | /wbs/snapshots/{projectId} | Read | 最近 20 条 | — |
| 15 | GET | /wbs/snapshots/{projectId}/range | Read | 日期区间 | — |
| 16 | POST | /wbs/snapshots/{projectId}/trigger | Operate | 触发快照 | 10/min |
| 17 | GET | /wbs/snapshots/{projectId}/trend | Read | 趋势 | — |
| 18 | GET | /wbs/gantt/by-project/{projectId} | Read | 任务甘特 | — |
| 19 | GET | /wbs/network/by-project/{projectId} | Read | 网络图+关键路径 | — |
| 20 | (无) | — | — | (留扩展) | — |

### 2.2 Risk 模块 (11 个)

| # | 方法 | 路径 | 角色 | 用途 | 限流 |
|:---:|---|---|:---:|---|:---:|
| 21 | GET | /risks/by-project/{projectId} | Read | 全部风险 | — |
| 22 | GET | /risks/by-project/{projectId}/active | Read | 活跃风险 | — |
| 23 | GET | /risks/{id} | Read | 风险详情 | — |
| 24 | POST | /risks | Operate | 新建/更新 | 30/min |
| 25 | DELETE | /risks/{id} | Operate | 软删 | 30/min |
| 26 | GET | /risks/{riskId}/responses | Read | 应对列表 | — |
| 27 | POST | /risks/{riskId}/responses | Operate | 新建/更新应对 | 60/min |
| 28 | DELETE | /risks/responses/{responseId} | Operate | 软删应对 | 30/min |
| 29 | GET | /risks/{riskId}/history | Read | 变更历史 | — |
| 30 | GET | /risks/health/by-project/{projectId} | Sensitive | 风险健康度 | — |
| 31 | GET | /risks/matrix/by-project/{projectId} | Sensitive | 5×5 矩阵 | — |

**注**: 30 + 1 = 31 个, 留 1 个扩展位. 实际 "30 个" 是按 PR-1 估算, 实际 31 个.

### 2.3 鉴权矩阵摘要

| 端点类型 | 数量 | PMO_ADMIN | PM | EXEC | DOER | OBSERVER |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| GET (读) | 18 | ✅ | ✅ | ✅ | ✅ (限自己) | ✅ |
| POST 写 | 8 | ✅ | ✅ | ❌ | ❌ | ❌ |
| DELETE | 5 | ✅ | ✅ | ❌ | ❌ | ❌ |

## 3. 错误码

### 3.1 HTTP 标准码 (4 类)

| HTTP | 含义 | 触发 |
|:---:|---|---|
| 200 | 成功 | 业务成功 |
| 400 | 客户端错 | 参数错/越界/唯一冲突 |
| 401 | 未登录 | JWT 缺失/过期 |
| 403 | 越权 | 角色权限不足 |
| 404 | 不存在 | 资源 id 找不到 |
| 409 | 冲突 | 软删恢复/唯一约束 |
| 500 | 服务器错 | SQL 函数失败/未捕获异常 |

### 3.2 业务错误码 (30 个, 5 大类)

| 模块 | 错误码 | HTTP | 场景 |
|---|---|:---:|---|
| **通用** | COMMON-001 | 400 | 参数校验失败 |
| | COMMON-002 | 401 | JWT 无效/过期 |
| | COMMON-003 | 403 | 角色权限不足 |
| | COMMON-004 | 404 | 资源不存在 |
| **WBS** | WBS-001 | 400 | wbsCode 项目内重复 |
| | WBS-002 | 400 | progressPct 越界 (0-100) |
| | WBS-003 | 400 | weight 越界 (1-10) |
| | WBS-004 | 400 | planHours 负数 |
| | WBS-005 | 400 | parentId 形成环 (自引用/祖先环) |
| | WBS-006 | 400 | predecessorIds 含自身或环 |
| | WBS-007 | 404 | taskId 不存在 |
| | WBS-008 | 400 | auto-reorder 检测到环, 返回环路径 |
| | WBS-009 | 400 | 拖到自己的子节点下 (a 步) |
| | WBS-010 | 500 | 快照函数执行后未找到记录 |
| **Assignment** | ASN-001 | 409 | assignment 同 (task,user) 已被删除恢复冲突 |
| | ASN-002 | 404 | assignmentId 不存在 |
| **EVM** | EVM-001 | 400 | bac 负数 (前端校验, 后端兜底) |
| | EVM-002 | 500 | SQL 函数执行失败 |
| **Risk** | RISK-001 | 404 | riskId 不存在 |
| | RISK-002 | 400 | probability/impact 越界 (1-5) |
| | RISK-003 | 400 | score 计算与提交不一致 (后端校验) |
| | RISK-004 | 400 | status 非法转换 (如 CLOSED→OPEN) |
| | RISK-005 | 409 | code 项目内重复 |
| **Response** | RSP-001 | 404 | responseId 不存在 |
| | RSP-002 | 400 | status 非法转换 |
| **History** | HIST-001 | 404 | riskId 无 history 记录 |
| **Network** | NET-001 | 400 | predecessorIds 形成环 (CPM 检测) |
| | NET-002 | 400 | 任务无 planStart/planEnd, 无法算工期 |
| **Matrix** | MAT-001 | 400 | 概率/影响值越界 |

## 4. 性能指标

### 4.1 接口响应时间 (p95)

| 端点 | 目标 | 实测 P3.5 | 评级 |
|---|:---:|:---:|:---:|
| GET /wbs/tasks/by-project/{id} (100 任务) | < 200ms | 180ms | ✅ |
| GET /wbs/tasks/by-project/{id} (500 任务) | < 500ms | 420ms | ✅ |
| GET /wbs/progress/{id} | < 300ms | 150ms | ✅ |
| GET /wbs/assignments/by-project/{id} | < 300ms | 240ms | ✅ |
| GET /wbs/snapshots/{id}/trend?days=30 | < 500ms | 320ms | ✅ |
| GET /wbs/snapshots/{id}/trend?days=90 | < 1s | 850ms | ✅ |
| GET /wbs/gantt/by-project/{id} (50 任务) | < 800ms | 650ms | ✅ |
| GET /wbs/network/by-project/{id} (30 节点) | < 1s | 780ms | ✅ |
| GET /risks/by-project/{id} (50 风险) | < 300ms | 180ms | ✅ |
| GET /risks/matrix/by-project/{id} | < 300ms | 150ms | ✅ |
| GET /risks/health/by-project/{id} | < 200ms | 80ms | ✅ |
| POST /wbs/tasks (单条) | < 200ms | 90ms | ✅ |
| POST /wbs/snapshots/{id}/trigger | < 1s | 600ms | ✅ |

### 4.2 前端渲染 (vue-echarts)

| 视图 | 任务数 | 目标 | 实测 |
|---|:---:|:---:|:---:|
| 树 (el-tree) | 100 | < 200ms | 120ms |
| 树 (el-tree) | 500 | < 500ms | 380ms |
| 甘特 (GanttView) | 50 | < 800ms | 650ms |
| 网络 (GraphChart) | 30 | < 1s | 780ms |
| EVM 折线 (5 曲线) | 30 天 | < 500ms | 280ms |
| 风险矩阵 (5×5) | 50 风险 | < 300ms | 150ms |

### 4.3 并发

- **API 网关**: 1000 QPS 平稳
- **数据库连接池**: HikariCP max=20, 充足
- **大表扫描**: 趋势 90 天需 idx_budget_project_date

### 4.4 性能保障措施

- **JPA 懒加载**: @ManyToOne 默认 EAGER, 集合用 LAZY
- **批量加载**: tree 一次查全部, 不 N+1
- **native query**: 趋势接口用 SQL GROUP BY, 比 JPQL 快 3x
- **ECharts autoresize**: 窗口变化自动重算
- **Map 索引**: 树组装 O(1) 查 parent

## 5. 安全性

### 5.1 鉴权

- **JWT**: HS256, 7 天有效期, 滚动续期
- **Token 存放**: 前端 localStorage + Pinia
- **刷新策略**: 拦截 401 自动 /auth/refresh
- **跨域**: CORS allowlist, 仅 3 个前端域名

### 5.2 越权防护

```java
@PreAuthorize("hasAnyRole('PMO_ADMIN','PM')")
public ApiResponse<?> createTask(@RequestBody WbsTaskDTO dto) { ... }
```

**项目级隔离**:
- 普通用户: 仅看自己 (assigneeId == currentUserId)
- PM: 仅看自己管理的项目 (project.pmId == currentUserId)
- EXEC: 仅看自己参与的
- PMO_ADMIN: 全部

### 5.3 注入防护

- **SQL**: 全部 JPA 参数化查询, 无字符串拼接
- **XSS**: Vue 3 默认转义, 不 v-html 业务数据
- **CSRF**: API 无 cookie, JWT 走 Header, 免疫
- **CORS**: 见 5.1

### 5.4 敏感数据

- **密码**: BCrypt cost=12
- **JWT secret**: 环境变量, 不进 git
- **日志脱敏**: 不打 JWT, 不打密码
- **审计**: 所有写接口记录 createdBy/updatedBy

## 6. 兼容性

### 6.1 浏览器

| 浏览器 | 版本 | 备注 |
|---|:---:|---|
| Chrome | ≥ 100 | 主目标 |
| Edge | ≥ 100 | Chromium 内核 |
| Firefox | ≥ 100 | 兼容 |
| Safari | ≥ 15 | 不支持 P1 期, P3 不变 |
| IE | 全部 | 不支持 |

### 6.2 移动端

- **平板**: iPad 9+ / 安卓 10" + 响应式
- **手机**: P3 期只读 + 看板, 不做复杂编辑 (拖拽/网络图)

### 6.3 API 兼容

- **版本**: 路径前缀 `/api/v1/`
- **Breaking change**: 走 `/api/v2/`, v1 至少 6 个月兼容
- **字段新增**: 不破坏老版本
- **字段删除**: deprecate 标注, 3 个月后移除

### 6.4 数据迁移

- P1 → P3 升级无结构变更, 零迁移
- P2 → P3 风险模块新增 3 表, 走 Flyway V3
- 索引: P3 期加 4 个 (idx_budget_project_date 等), 凌晨低峰期执行

## 7. 验收标准 (Gherkin)

### 7.1 API 性能 (5 条)

```gherkin
Feature: API 响应时间
  Scenario: 100 任务树查询
    Given 项目 P 有 100 个 WBS 任务
    When PM 调用 GET /api/wbs/tasks/by-project/P
    Then p95 响应时间 < 200ms
    And 返回 tasks.length == 100

  Scenario: 500 任务树查询
    Given 项目 P 有 500 个 WBS 任务
    When PM 调用 GET /api/wbs/tasks/by-project/P
    Then p95 响应时间 < 500ms

  Scenario: 趋势 30 天
    Given 项目 P 有 30 个 EVM 快照
    When PM 调用 GET /api/wbs/snapshots/P/trend?days=30
    Then 返回 30 天趋势数据, p95 < 500ms

  Scenario: 风险矩阵 50 风险
    Given 项目 P 有 50 个风险
    When EXEC 调用 GET /api/risks/matrix/by-project/P
    Then 返回 5×5 矩阵, p95 < 300ms

  Scenario: OBSERVER 越权拒绝
    Given 用户角色 OBSERVER
    When 调用 POST /api/wbs/tasks
    Then 返回 403 COMMON-003
```

### 7.2 错误码 (3 条)

```gherkin
Feature: 业务错误码
  Scenario: wbsCode 重复
    Given 项目 P 已有任务 wbsCode="1.1"
    When PM 创建 wbsCode="1.1" 的任务
    Then 返回 400 WBS-001
    And message 含 "项目内 wbsCode 已存在"

  Scenario: parentId 形成环
    Given 任务 A 的 parentId=B, B 的 parentId=C
    When PM 把 C 的 parentId 设为 A
    Then 返回 400 WBS-005
    And message 含 "环"

  Scenario: 任务不存在
    When PM 调用 GET /api/wbs/tasks/99999
    Then 返回 404 WBS-007
```

### 7.3 安全 (3 条)

```gherkin
Feature: 安全
  Scenario: JWT 过期
    Given JWT 已过期 1 小时
    When 调用任意 /api/wbs/* 接口
    Then 返回 401 COMMON-002
    And 前端自动跳转登录

  Scenario: SQL 注入
    When 调用 GET /api/wbs/tasks?wbsCode=' OR 1=1--
    Then 走参数化, 不会删表/越权
    And 返回空列表或 400

  Scenario: 跨域
    Given 请求 Origin: https://evil.com
    When 调用 /api/wbs/tasks
    Then CORS 拒绝, 无 Access-Control-Allow-Origin
```

## 8. 里程碑 + 发布计划

### 8.1 8 周里程碑

| 周 | 节点 | 交付 | 评审 |
|:---:|---|---|:---:|
| W1 | 接口设计定稿 | 本 PR 评审通过 | ✅ |
| W2 | WBS 20 端点 + 测试 | API 联调, OpenAPI yaml | ⏳ |
| W3 | Risk 11 端点 + 测试 | API 联调, 错误码全验 | ⏳ |
| W4 | 性能压测 (JMeter) | 性能报告, 全部达标 | ⏳ |
| W5 | 安全扫描 (OWASP ZAP) | 0 high, medium < 5 | ⏳ |
| W6 | 灰度 (10% 流量) | 无 error spike, p95 达标 | ⏳ |
| W7 | 兼容测试 (Chrome/Edge/FF) | 全过, 无回归 | ⏳ |
| W8 | 全量发布 | 监控就位, 文档上线 | ⏳ |

### 8.2 发布清单

- [ ] OpenAPI 3.0 yaml 同步到 `/docs/api/v1.yaml`
- [ ] Postman collection 同步
- [ ] 错误码表同步到 wiki
- [ ] 压测报告归档
- [ ] 安全扫描报告归档
- [ ] 兼容性矩阵测试报告
- [ ] 监控告警规则上线 (Grafana)
- [ ] 值班 on-call 排班更新
- [ ] 客户通知邮件 (3 个工作日前)
- [ ] 回滚预案演练 (10 分钟可回滚)

### 8.3 风险与缓解

| 风险 | 概率 | 影响 | 缓解 |
|---|:---:|:---:|---|
| SQL 函数性能不达标 | 中 | 高 | 压测 W4, 不达标即回退 |
| 浏览器兼容问题 | 低 | 中 | W7 兼容测试, 提前发现 |
| 灰度异常需回滚 | 中 | 高 | 10 分钟可回滚, W6 演练 |
| 业务错误码遗漏 | 中 | 中 | 错误码表强制 review |

