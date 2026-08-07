---
status: active
created: 2026-08-07
updated: 2026-08-07
summary: 关键算法与状态机(立项 3 级审批 + 加权进度 + 4 级费率解析 + KPI)
---

# 关键算法与状态机(Algorithms)

> 单一事实来源:立项 3 级审批状态机、加权进度算法、Dashboard KPI 计算、4 级费率解析。
> 对应来源:[`legacy/pmo-pms-mvp-design.md` §6](legacy/pmo-pms-mvp-design.md) + [`legacy/pmo-pms-cost-engine.md` §3](legacy/pmo-pms-cost-engine.md)

---

## 1. 立项 3 级审批状态机

`InitiationService.decide(...)` 内部:

```java
private static final List<String> APPROVAL_FLOW = List.of("DEPT_LEAD", "PMO_ADMIN", "EXEC");

switch (d.decision()) {
  case "REJECTED" -> {
    i.setStatus(REJECTED);
    i.setCurrentStep(null);
    i.setClosedAt(now);
  }
  case "SUPPLEMENT" -> {
    i.setStatus(SUPPLEMENT);  // 留在当前步骤,等申请人补料
  }
  case "APPROVED" -> {
    if (idx + 1 >= APPROVAL_FLOW.size()) {
      i.setStatus(EXEC_APPROVED);
      i.setClosedAt(now);
      createProjectFromInitiation(i);   // ← 关键副作用
    } else {
      i.setCurrentStep(nextStep);
      i.setStatus(stepStatusMap.get(nextStep));
    }
  }
}
```

**关键点**:
- `currentStep` 是 **String**(DEPT_LEAD/PMO_ADMIN/EXEC),不是 stepId,便于跨库迁移
- 状态 / 步骤**双重字段**:`status_id` 是字典(给前端展示),`current_step` 是步骤(给后端流转)
- 3 级全过 → `createProjectFromInitiation` 自动建项目,`projectId` 回写到 initiation
- SUPPLEMENT 不前进,等申请人重新 `submit` 触发再次流转

---

## 2. 加权进度算法

`MilestoneRepository.computeWeightedProgressPct(projectId)` — **JPQL 一次往返**:

```jpql
SELECT COALESCE(
  ROUND(
    100.0 * SUM(CASE WHEN s.code = 'COMPLETED' THEN m.weight ELSE 0 END) /
    NULLIF(SUM(m.weight), 0)
  ), 0)
FROM Milestone m JOIN m.status s
WHERE m.projectId = :projectId AND m.deleted = false
```

**为什么不用 Java 算**:
1. **LAZY 问题**:在 Java 端遍历 `milestones` 会触发 status 的 lazy load,Service 间调用 + 事务关闭后会 500
2. **N+1**:每个 milestone 都要查 status
3. **不一致**:Service 自调用 `this.computeXxx()` 绕过 Spring 代理,`@Transactional` 不生效

**边界**:
- 空集 / 0 权重 → `NULLIF` 兜底 → `COALESCE` 返回 0
- 所有 weight 都是 0 → 同上
- `ROUND(..., 0)` 整数百分比,前端不再处理小数

---

## 3. Dashboard 4 项 KPI

| KPI | 计算 |
|---|---|
| `activeCount` | `status.code = "ACTIVE"` 的项目数 |
| `newInitiationsThisMonth` | `initiation.createdAt` 在本年月的数量 |
| `closedThisMonth` | `status.code = "CLOSED"` 且 `actualEndDate` 在本月的数量 |
| `overdueProjects` | `status.code = "ACTIVE"` 且 `planEndDate < today` 的数量 |

**性能**:MVP 量级(几十到几百项目)直接 in-memory stream 过滤;**项目数 > 5K 时**应改成 native SQL + GROUP BY(已留 TODO)。

---

## 4. Dashboard 容错

`Dashboard.vue` 改用 `Promise.allSettled` — **4 个 API 任何一个失败,其他 3 个的图仍然画**。这是从 `efa911b` 修的,之前 `Promise.all` 任一失败会全空。

---

## 5. 4 级费率解析(成本引擎 F1 灵魂)

`CostRateResolver.resolveRate(timesheetEntry)` — 4 级优先级匹配:

```
1. (project_id, user_id, role_code, period)  ← 项目级角色特定
2. (project_id, user_id, period)             ← 项目级人员特定
3. (project_id, role_code, period)           ← 项目级角色通用
4. (department_id, role_code, period)        ← 部门级角色通用
```

**算法**:
1. 用 4 个 key 分别查 `role_rate` 表
2. **按优先级返回第一个非空**
3. 全 null → 抛 `BusinessException(404, "No rate found for ...")`

详见 [`cost-engine.md`](cost-engine.md) §4 级费率解析算法。

---

## 6. 甘特图自动范围锚定

`GanttService.gantt()` 计算 `autoFrom/autoTo`:

1. **项目时间窗**:`planStart/End` + `actualStart/End` 取最早 / 最晚
2. **里程碑时间窗**(P2.C 修复):`milestone.planDate/actualDate` 也纳入
3. **今天锚定**(P2.B 兜底):若算出的范围离 today > 3 个月,用 `today ± 3 月`

详见 [`analysis/commit-splits/`](../../analysis/commit-splits/) 内 `P2.B-workload-views-fix.md` / `P2.C-gantt-axis-fix.md`(已迁移至 `docs/reviews/`)。

---

## 7. 里程碑 7 阶段字典(V3.1+)

```
INTAKE → ANALYSIS → PROPOSAL → APPROVAL → KICKOFF → EXECUTION → CLOSING
```

每个阶段含 PENDING / IN_PROGRESS / DONE 三个子状态;项目加权进度公式见 §2。
