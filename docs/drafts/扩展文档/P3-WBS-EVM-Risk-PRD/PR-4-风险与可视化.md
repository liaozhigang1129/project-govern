# PR-4: P3 风险与可视化

> **版本**: v0.1 (草稿)
> **作者**: PMO 研发组
> **评审**: @前端 @PM @后端
> **更新**: 2025-06-10
> **状态**: ⏳ 评审中
> **依赖**: PR-1 (角色), PR-2 (数据模型), PR-3 (核心功能)

---

## 1. 风险 CRUD

### 1.1 4 个核心端点

| # | 端点 | 方法 | 角色 | 用途 |
|:---:|---|:---:|---|---|
| 1 | `/risks/by-project/{projectId}` | GET | Read | 全部风险 (按 score 降序, 含已关闭) |
| 2 | `/risks/by-project/{projectId}/active` | GET | Read | 活跃风险 (排除 CLOSED/ACCEPTED) |
| 3 | `/risks/{id}` | GET | Read | 单个风险详情 |
| 4 | `/risks` | POST | Operate | 新建/更新 (id 缺失=新建) |
| 5 | `/risks/{id}` | DELETE | Operate | 软删除 (写 history) |

### 1.2 17 字段规则

| 字段 | 必填 | 校验 | 默认 |
|---|:---:|---|---|
| code | ✅ | 项目内唯一, R-001/R-002... | 自动生成 |
| title | ✅ | ≤ 256 字符 | — |
| description | ❌ | TEXT | "" |
| category | ✅ | 7 值: TECHNICAL/SCHEDULE/COST/QUALITY/EXTERNAL/ORGANIZATIONAL/OTHER | — |
| probability | ✅ | 1-5 | — |
| impact | ✅ | 1-5 | — |
| **score** | — | **后端自动算 = probability × impact** | — |
| **level** | — | **后端自动算** (LOW 1-4 / MEDIUM 5-9 / HIGH 10-15 / CRITICAL 16-25) | — |
| status | ✅ | 5 值: OPEN/MITIGATING/CLOSED/OCCURRED/ACCEPTED | OPEN |
| ownerUserId | ❌ | user_id | null |
| mitigation | ❌ | TEXT (预防措施) | "" |
| contingency | ❌ | TEXT (应急措施) | "" |
| responseStrategy | ❌ | 7 值: AVOID/MITIGATE/TRANSFER/ACCEPT/EXPLOIT/ENHANCE/SHARE | null |
| identifiedDate | ✅ | YYYY-MM-DD | today |
| targetCloseDate | ❌ | YYYY-MM-DD | null |

### 1.3 score / level 自动推导

```java
// RiskService.save
risk.setProbability(req.getProbability());
risk.setImpact(req.getImpact());
int score = req.getProbability() * req.getImpact();   // 1-25
risk.setScore(score);
risk.setLevel(deriveLevel(score));  // LOW/MEDIUM/HIGH/CRITICAL

private String deriveLevel(int score) {
    if (score <= 4)  return "LOW";
    if (score <= 9)  return "MEDIUM";
    if (score <= 15) return "HIGH";
    return "CRITICAL";
}
```

| score 区间 | level | 颜色 | 行动 |
|---|---|---|---|
| 1-4 | LOW | 🟢 绿 | 监控 |
| 5-9 | MEDIUM | 🟡 黄 | 关注 |
| 10-15 | HIGH | 🟠 橙 | 应对 |
| 16-25 | CRITICAL | 🔴 红 | 立即响应 |

### 1.4 软删除 + 写 history

```java
@Transactional
public void softDelete(Long riskId) {
    Risk risk = repository.findById(riskId)
        .orElseThrow(() -> new BusinessException("RISK-001: 风险不存在"));
    
    risk.setDeleted(true);
    risk.setUpdatedAt(Instant.now());
    repository.save(risk);
    
    // 写 history (V2.7 新增 DELETED action)
    RiskHistory h = new RiskHistory();
    h.setRiskId(riskId);
    h.setAction("DELETED");
    h.setOperatorId(securityUtils.currentUserId());
    historyRepository.save(h);
}
```

## 2. 应对行动 (risk_response)

### 2.1 3 个端点

| # | 端点 | 方法 | 角色 | 用途 |
|:---:|---|:---:|---|---|
| 1 | `/risks/{riskId}/responses` | GET | Read | 某风险的全部应对 |
| 2 | `/risks/{riskId}/responses` | POST | Operate | 新建/更新 (id 缺失=新建) |
| 3 | `/risks/responses/{responseId}` | DELETE | Operate | 软删除 |

### 2.2 业务定位

PMBOK 7 推荐: **一个风险可挂多条措施**, 解决"一个风险多个动作"的问题 (例如 "需求变更频繁" 风险可能同时有"加需求评审""冻结需求池""加强 PRD 培训" 三个动作).

### 2.3 8 字段

| 字段 | 必填 | 校验 | 默认 |
|---|:---:|---|---|
| riskId | ✅ | FK | 父风险 |
| action | ✅ | ≤ 256 字符 | — |
| ownerUserId | ❌ | user_id | null |
| dueDate | ❌ | YYYY-MM-DD | null |
| completedAt | ❌ | TIMESTAMP | null (完成时自动填) |
| status | ✅ | 4 值: PLANNED/IN_PROGRESS/DONE/CANCELLED | PLANNED |
| note | ❌ | TEXT | "" |

### 2.4 4 态状态机

```
PLANNED → IN_PROGRESS → DONE  (终态, 自动填 completedAt)
                  ↘ CANCELLED (终态)
```

**联动**: 当 risk_response 状态变更 (DONE), 写 risk_history action=`RESPONSE_DONE`.

## 3. 变更历史 (risk_history)

### 3.1 端点

- `GET /risks/{riskId}/history` — 某风险的全部变更历史 (按 created_at 倒序)

### 3.2 业务定位

**审计追踪**: 记录状态/分数/责任人/评论变更, 软删也写 history, **不物理删**.

### 3.3 8 字段 + 9 种 action

| 字段 | 必填 | 说明 |
|---|:---:|---|
| riskId | ✅ | 父风险 |
| action | ✅ | 9 种 (V2.7 新增 DELETED) |
| fieldName | ❌ | 字段名 (e.g. status, score) |
| oldValue | ❌ | 旧值 (TEXT, JSON 序列化) |
| newValue | ❌ | 新值 (TEXT, JSON 序列化) |
| comment | ❌ | 评论 |
| operatorId | ❌ | 操作人 (从 SecurityUtils 取) |
| createdAt | ✅ | DB 默认 NOW() |

**9 种 action**:

| action | 触发 | fieldName | old/new |
|---|---|---|---|
| `CREATED` | 新建风险 | — | — |
| `STATUS_CHANGED` | 改 status | status | OPEN→MITIGATING 等 |
| `SCORE_CHANGED` | 改 probability/impact | probability, impact | 2,3 → 3,4 |
| `OWNER_CHANGED` | 改 ownerUserId | owner_user_id | 1 → 2 |
| `LEVEL_CHANGED` | score 跨档 | level | MEDIUM→HIGH |
| `COMMENTED` | 加评论 | — | — |
| `RESPONSE_ADDED` | 新建 response | — | — |
| `RESPONSE_DONE` | response 标 DONE | — | — |
| `DELETED` | 软删 (V2.7 新增) | — | — |

### 3.4 V2.7 补丁

`V2.7__risk_history_deleted_action.sql`:
- 扩大 `action` 字段长度 (VARCHAR(32) → VARCHAR(64))
- 新增 'DELETED' 到 CHECK 约束 (如原表有 CHECK)
- 不影响现有数据

## 4. 5×5 风险矩阵

### 4.1 端点

- `GET /risks/matrix/by-project/{projectId}` — 5×5 概率×影响 热力图

### 4.2 矩阵结构

**横轴**: 概率 (1=极低 → 5=极高)
**纵轴**: 影响 (1=轻微 → 5=严重)
**单元格**: 该 (prob, impact) 组合下活跃风险数

```
影响 ↑
  5  [ 0] [ 0] [ 1] [ 3] [ 5]   ← CRITICAL
  4  [ 0] [ 0] [ 2] [ 2] [ 4]   ← HIGH
  3  [ 0] [ 1] [ 2] [ 3] [ 3]   ← MEDIUM (跨档)
  2  [ 1] [ 1] [ 2] [ 1] [ 0]   ← LOW
  1  [ 2] [ 3] [ 1] [ 0] [ 0]   ← LOW
     p=1  p=2  p=3  p=4  p=5
              概率 →
```

**颜色**: 单元格按 score 着色 (绿/黄/橙/红), 数字 = 该 (prob, impact) 下风险数.

### 4.3 接口返回结构

```typescript
{
  projectId: number,
  cells: Array<{
    probability: 1|2|3|4|5,
    impact: 1|2|3|4|5,
    score: number,        // = prob × impact
    level: 'LOW'|'MEDIUM'|'HIGH'|'CRITICAL',
    count: number,        // 该 cell 的风险数
    riskIds: number[],    // 详情用
  }>,
  summary: {
    total: number,        // 活跃风险总数
    critical: number,     // CRITICAL 数
    high: number,         // HIGH 数
    medium: number,       // MEDIUM 数
    low: number,          // LOW 数
  }
}
```

### 4.4 组件 (RiskMatrixView.vue, 112 行)

- **5×5 网格**: `<table>` 或 CSS Grid
- **悬停**: 显示该 cell 风险数 + 跳转到 list
- **点击 cell**: 过滤跳到 `/risks/{projectId}?prob=X&impact=Y`
- **空 cell**: 浅灰, 数字 0
- **图例**: 4 色 4 level

## 5. 项目风险健康度

### 5.1 端点

- `GET /risks/health/by-project/{projectId}` — 项目风险健康度 KPI (给 PMO 仪表盘用)

### 5.2 KPI 字段

```typescript
{
  projectId: number,
  totalRisks: number,         // 总风险数 (含关闭)
  activeRisks: number,        // 活跃风险数
  byLevel: {
    critical: number,
    high: number,
    medium: number,
    low: number,
  },
  byStatus: {
    open: number,
    mitigating: number,
    closed: number,
    occurred: number,
    accepted: number,
  },
  // 健康度
  health: 'GOOD'|'WARN'|'BAD',
  // 建议
  suggestion: string,         // '立即处理 N 个 CRITICAL 风险' 等
}
```

### 5.3 健康度 3 档

```java
public enum Health { GOOD, WARN, BAD }

public Health health() {
    long critical = byLevel.critical;
    long high = byLevel.high;
    if (critical > 0)         return BAD;     // 有 CRITICAL 立即告警
    if (high >= 3)            return WARN;    // HIGH ≥ 3 关注
    return GOOD;
}
```

| 健康 | 条件 | 头部 tag |
|---|---|---|
| 🔴 BAD | critical > 0 | 红色 |
| 🟡 WARN | high ≥ 3 | 黄色 |
| 🟢 GOOD | 其他 | 绿色 |

## 6. 任务甘特图 (WBS 任务级)

### 6.1 端点

- `GET /wbs/gantt/by-project/{projectId}` — 任务级甘特图数据

### 6.2 关键设计: 复用 GanttView 组件

**问题**: 项目级 GanttView.vue **53 KB, 1463 行**, 含远程光标/拖拽/SVG 联动, 改动风险大.

**方案**: **只做适配器** (WbsGanttView.vue, 129 行)
- 不重写 GanttView
- 把 `WbsGanttRow[]` 适配成 `GanttBar[]` 喂给现有 GanttView
- `milestone=true` → 在该任务行内画里程碑菱形
- `critical=true` → bar 边框加红
- 任务点击 → emit `task-click` → 父切回树视图并选中

### 6.3 WbsGanttResponse 结构

```typescript
{
  projectId: number,
  rangeFrom: string,           // YYYY-MM-DD
  rangeTo: string,             // YYYY-MM-DD
  taskCount: number,
  rows: Array<{
    taskId: number,
    wbsCode: string,           // "1.1.2"
    name: string,
    status: 'NOT_STARTED'|...,
    planStart: string, planEnd: string,
    actualStart: string|null, actualEnd: string|null,
    progressPct: number,
    milestone: boolean,
    weight: number,
  }>,
}
```

### 6.4 UI 行为

- **左轴**: 任务行 (按 wbsCode 升序)
- **横轴**: 日期 (rangeFrom → rangeTo, 自动算缩放)
- **bar**: 计划区间 (灰) + 实际区间 (蓝) 重叠
- **里程碑**: 菱形 (◆) 在 planEnd 当 planDate
- **关键路径**: 红色边框
- **任务点击**: 切回 WbsView 树视图 + 选中该任务

## 7. 网络图 (ECharts GraphChart)

### 7.1 端点

- `GET /wbs/network/by-project/{projectId}` — 依赖网络图 + 关键路径 (一次返回)

### 7.2 5 维节点视觉编码 (WbsNetworkView.vue, 372 行)

| 维度 | 字段 | 视觉表现 |
|---|---|---|
| 1. 工期 | planDurationDays | 节点大小 24-54 px (越大越长) |
| 2. 类型 | milestone | 形状: 菱形 (◆) / 圆 (●) |
| 3. 状态 | status + progressPct | 填充色 6 档 (灰/浅蓝/中蓝/蓝/绿/红) |
| 4. 关键路径 | critical | 红色边框 + 阴影 |
| 5. 类别 | category | 图例 3 类 (普通/里程碑/关键) |

### 7.3 边视觉编码

- **普通依赖**: 灰线 (#c0c4cc), 1px
- **关键路径边**: 红线 (#f56c6c), 2.5px
- **箭头**: 末端箭头 (symbol: ['none', 'arrow'])

### 7.4 节点 tooltip

```
1.4 开发
状态: 进行中 (65%)
工期: 10 天 · 工时: 80h
负责人: 王五
计划: 2025-01-15 → 2025-01-25
🔴 关键路径
```

### 7.5 边 tooltip

```
紧前关系
1.2 设计
↓
1.4 开发
🔴 关键路径
```

### 7.6 节点点击

emit `task-click` → 父组件切回树视图 + 选中

### 7.7 4 个 KPI 卡片 (顶部)

| KPI | 含义 |
|---|---|
| 总任务 | 项目 WBS 规模 |
| 🔴 关键路径任务 (占比) | 关键路径覆盖度, 占比 < 20% 健康 |
| 📅 项目工期 (CPM) | 理论最短天数 (关键路径工期之和) |
| 🔗 依赖边 | 任务耦合度, 越多越易卡死 |

## 8. CPM 关键路径

### 8.1 业务定位

**CPM (Critical Path Method) 关键路径法**: 项目工期 = **最长的任务链**, 链上任务称为**关键任务**, 任何延期 = 项目延期.

### 8.2 算法 (4 步)

```
1. 拓扑排序 (处理 predecessorIds 链, 检测环)
2. 正向遍历: 算 ES (最早开始) / EF (最早完成)
   - ES[0] = 0
   - EF[i] = ES[i] + duration[i]
   - ES[j] = max(EF[i] for i in predecessor[j])
3. 反向遍历: 算 LS (最晚开始) / LF (最晚完成)
   - LF[n] = project_deadline
   - LS[i] = LF[i] - duration[i]
   - LF[i] = min(LS[j] for j where i in predecessor[j])
4. 总浮动 slack[i] = LS[i] - ES[i]
   - slack = 0 → 关键任务
   - 边两端都在关键任务上 → 关键边
```

### 8.3 接口 `WbsNetworkResponse`

```typescript
{
  projectId: number,
  taskCount: number,
  nodes: Array<{
    taskId: number,
    wbsCode: string,
    name: string,
    status: string,
    progressPct: number,
    planStart: string, planEnd: string,
    planDurationDays: number,
    planHours: number,
    ownerName: string|null,
    critical: boolean,        // ← CPM 算出
    milestone: boolean,
  }>,
  edges: Array<{
    fromTaskId: number,
    toTaskId: number,
    isCriticalEdge: boolean,  // ← CPM 算出
  }>,
  criticalTaskIds: number[],  // 关键任务 id 列表
  hasCycle: boolean,          // 有环时 nodes/edges 仍返回, 但 criticalTaskIds=[]
}
```

### 8.4 关键任务清单 (折叠表)

```vue
<el-collapse>
  <el-collapse-item title="📋 关键路径任务清单">
    <el-table :data="data.nodes.filter(n => n.critical)" border>
      <el-table-column prop="wbsCode" label="WBS"/>
      <el-table-column prop="name" label="任务名"/>
      <el-table-column prop="status" label="状态"/>
      <el-table-column label="工期">{{ row.planDurationDays }} 天</el-table-column>
      <el-table-column prop="ownerName" label="负责人"/>
      <el-table-column label="计划">{{ row.planStart }} → {{ row.planEnd }}</el-table-column>
    </el-table>
  </el-collapse-item>
</el-collapse>
```

### 8.5 关键路径占项目工期比例

- **< 20%**: 健康 (缓冲充足)
- **20-40%**: 正常
- **> 40%**: 危险 (缓冲太少, 一点延期就崩)

## 9. 验收 + 风险

### 9.1 验收标准 (本 PR-4 维度)

| # | 验收项 | 验证方式 |
|:---:|---|---|
| 1 | 风险 11 端点通过 ControllerTest | RiskControllerTest (7 用例) |
| 2 | 17 字段校验 + score/level 自动推导 | RiskServiceTest |
| 3 | 软删写 history (DELETED action) | 集成测 |
| 4 | 应对行动 4 态状态机 | 状态转换测试 |
| 5 | 5×5 矩阵正确着色 | UI 手动 + 单测 (25 cell) |
| 6 | 网络图 CPM 算法正确 | WbsNetworkServiceTest (含环测试) |
| 7 | 关键路径标红 | 视觉检查 |
| 8 | 风险健康度 3 档 (critical/high) | 单测 |

### 9.2 风险与待定

| # | 风险 | 缓解 |
|:---:|---|---|
| 1 | 5×5 矩阵风险 ID 列表大, 返回 payload 重 | 仅返回 id 列表, 详情 click 再查 |
| 2 | 应对行动多时, history 关联 N 条 | history 索引 (risk_id, created_at DESC) |
| 3 | 网络图力导向布局任务多时慢 | 限制任务 ≤ 100, 超过分组/分页 |
| 4 | CPM 算法有环时 criticalTaskIds=[] | 提示用户"请检查 predecessorIds" |
| 5 | V2.7 DELETED action 升级 | 已 Flyway 自动跑, 不需手动 |

---

## 10. 关联文档

- 前置: [PR-1-概述与角色.md](./PR-1-概述与角色.md) / [PR-2-数据模型.md](./PR-2-数据模型.md) / [PR-3-核心功能-任务与资源.md](./PR-3-核心功能-任务与资源.md)
- 后续: [PR-5-API与非功能.md](./PR-5-API与非功能.md)
- SQL: `V2.6__risk.sql` / `V2.7__risk_history_deleted_action.sql`
