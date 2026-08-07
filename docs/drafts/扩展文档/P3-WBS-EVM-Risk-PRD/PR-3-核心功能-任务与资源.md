# PR-3: P3 核心功能 — 任务与资源

> **版本**: v0.1 (草稿)
> **作者**: PMO 研发组
> **评审**: @后端 @前端 @架构师
> **更新**: 2025-06-10
> **状态**: ⏳ 评审中
> **依赖**: PR-1 (角色), PR-2 (数据模型)

---

## 1. WBS 任务 CRUD

### 1.1 5 个端点

| # | 端点 | 方法 | 角色 | 用途 |
|:---:|---|:---:|---|---|
| 1 | `/wbs/tasks/by-project/{projectId}` | GET | Read | 任务树 (扁平+嵌套 children) |
| 2 | `/wbs/tasks/flat/by-project/{projectId}` | GET | Read | 扁平列表 (搜索/下拉用) |
| 3 | `/wbs/tasks/{id}` | GET | Read | 单个任务详情 |
| 4 | `/wbs/tasks` | POST | Operate | 新建/更新 (id 缺失=新建) |
| 5 | `/wbs/tasks/{id}` | DELETE | Operate | 软删除 (子任务不级联) |

### 1.2 新建/更新 22 字段规则

| 字段 | 必填 | 校验 | 默认 |
|---|:---:|---|---|
| wbsCode | ✅ | 项目内唯一, ≤ 32 字符, 形如 1.1.2 | — |
| name | ✅ | ≤ 256 字符 | — |
| taskType | ✅ | IN (SUMMARY, EXECUTION, MILESTONE, DELIVERABLE) | EXECUTION |
| status | ✅ | IN (NOT_STARTED, IN_PROGRESS, COMPLETED, BLOCKED, CANCELLED) | NOT_STARTED |
| weight | ✅ | 1-10 | 1 |
| progressPct | ❌ | 0-100 | 0 |
| ownerUserId | ❌ | 数字 (user_id) | null |
| planStartDate / planEndDate | ❌ | YYYY-MM-DD | null |
| actualStartDate / actualEndDate | ❌ | YYYY-MM-DD | null |
| planHours / actualHours | ❌ | ≥ 0, 0.5 步 | 0 |
| predecessorIds | ❌ | 逗号分隔 id, 解析成 Long[] | [] |
| critical / milestone | ❌ | boolean | false |
| deliverable / remark | ❌ | ≤ 2000 字符 | "" |

### 1.3 软删除语义

- 仅设 `deleted=true`, **不物理删**
- 子任务**不级联删** (CASCADE 只在父 task.id 真删时触发)
- 删后该 task 在 GET 列表中**不再出现**
- 删除前**前端必须 ElMessageBox 二次确认**
- 删除操作写 audit_log (P1.6 已实现)

## 2. 树组装与加权进度

### 2.1 算法: 扁平 → 嵌套

```java
// 1. id → node 索引 (LinkedHashMap 保序)
Map<Long, WbsTaskNode> byId = new LinkedHashMap<>();
for (WbsTask t : flat) {
    byId.put(t.getId(), WbsTaskNode.leaf(t, 0, List.of(t.getWbsCode())));
}

// 2. 单次遍历, 挂到 parent.children
List<WbsTaskNode> roots = new ArrayList<>();
for (WbsTask t : flat) {
    WbsTaskNode node = byId.get(t.getId());
    if (t.getParentId() == null) {
        roots.add(node);
    } else {
        WbsTaskNode parent = byId.get(t.getParentId());
        if (parent != null) {
            // 关键: 构造带 depth+1 / 完整 path 的新 node 替换
            WbsTaskNode childWithDepth = new WbsTaskNode(
                /* 22 字段原样 + */
                parent.depth() + 1,
                concatPath(parent.path(), node.wbsCode()),
                node.children()
            );
            byId.put(t.getId(), childWithDepth);
            parent.children().add(childWithDepth);
        } else {
            // 父不存在 (软删), 提升为根
            roots.add(node);
        }
    }
}
```

**复杂度**: O(n) 单次遍历, 适合 500+ 任务的大项目.

### 2.2 加权进度 (父任务汇总)

**公式**: `progress_parent = Σ (weight_i × progress_i) / Σ weight_i`

**示例**:
```
1. 设计阶段 (weight 自身 = 1)
  ├─ 1.1 概要设计  (weight=3, progress=80%) → 240
  └─ 1.2 详细设计  (weight=7, progress=20%) → 140
合计: weight_sum=10, weighted_sum=380
父进度 = 380 / 10 = 38%
```

### 2.3 接口 `/wbs/progress/{projectId}`

返回 `WbsProgressSummary`:
- `totalTasks`: 任务总数
- `completedTasks`: 已完成数
- `overallProgressPct`: 加权汇总进度 (0-100)
- `totalPlanHours` / `totalActualHours`: 工时燃尽
- `burnRate`: 实际/计划 (1.0 = 持平, >1 = 超)

## 3. 拖拽与重排 (a 步 + b 步)

### 3.1 端点: `POST /wbs/tasks/{id}/move`

**入参** (WbsTaskMoveRequest):

| 字段 | 类型 | 必填 | 说明 |
|---|---|:---:|---|
| `newParentId` | Long | ❌ | 目标父 id, null = 顶层 |
| `beforeSiblingId` | Long | ❌ | 拖到该 sibling 之前, null = 末尾 |

**dispatch 规则**:

```
if (beforeSiblingId == null) {
    // a 步: 换 parent, 子树级联重编号
    return moveTask(taskId, newParentId);
} else {
    // b 步: 同 parent 内 reorder
    return reorderTask(taskId, newParentId, beforeSiblingId);
}
```

### 3.2 a 步: 换 parent + 子树级联重编号

**业务场景**: 拖任务 A 从父 X 移到父 Y 下

**算法** (伪代码):

```
1. 取出 A + A 子树 (递归查 children)
2. 更新 A.parent_id = Y
3. 找 Y 下当前最大 wbs_code (形如 Y.code.x), 生成新 A.code = Y.code.(max+1)
4. 遍历 A 子树, 全部 prefix 替换
   - 旧 1.2.3.4 → 新 1.5.1.4 (1.2.3.4 在 1.2.3 下, 移到 1.5.1 下)
5. 旧父 X 下剩余任务的 wbs_code 自动补齐 (Kahn 拓扑)
```

**复杂度**: O(n) 一次性, n=子树规模. 子树 ≤ 500 节点性能 OK.

**回滚**: 失败时事务回滚, 不留半截状态.

### 3.3 b 步: 同 parent 内 reorder

**业务场景**: 把任务 1.1.3 拖到 1.1.1 之前

**算法**:

```
1. 取出 task 当前 parent (oldParentId)
2. 从 oldParent 下移除 task, 插到 newParent 下 beforeSibling 之前
3. 重排 oldParent / newParent 下所有 sibling 的 wbs_code (按 sort 字段)
   - 1.1.1 / 1.1.2 / 1.1.3 → 1.1.1 / 1.1.2 / 1.1.3 (按 DOM 顺序)
```

**注意**: b 步**不跨 parent**, 跨 parent 必须走 a 步.

### 3.4 自动重排: `POST /wbs/projects/{id}/auto-reorder`

**业务场景**: 用户在依赖图视图手动改完 predecessorIds 后, 让系统按 Kahn 拓扑自动排

**算法** (Kahn 拓扑):

```
1. 收集所有任务, 按 predecessorIds 建图
2. 检测环: DFS 染色, 有环则抛 400
3. 入度为 0 的入队, BFS
4. 按出队顺序给 wbs_code 编号 (1, 2, 3, ...)
5. 同 parent 内保持拓扑序, 跨 parent 独立排
```

**失败**: 有环 → `400 HAS_CYCLE`, 返回环路径 `A → B → A`.

## 4. 资源分配矩阵

### 4.1 5 个端点

| # | 端点 | 方法 | 角色 | 用途 |
|:---:|---|:---:|---|---|
| 1 | `/wbs/assignments/by-task/{wbsTaskId}` | GET | Read | 某任务的人员 |
| 2 | `/wbs/assignments/by-user/{userId}` | GET | Read | 某人的任务 (资源模块) |
| 3 | `/wbs/assignments/by-project/{projectId}` | GET | Read | 项目下所有 (task,user) 分配 |
| 4 | `/wbs/assignments` | POST | Operate | 新增/更新 (同 (task,user) upsert) |
| 5 | `/wbs/assignments/{id}` | DELETE | Operate | 软删除 |

### 4.2 5 角色 + 配色

| role | 中文 | 颜色 | 适用场景 |
|---|---|---|---|
| LEAD | 负责 | #f56c6c 红 | 责任最大, 1 任务 1 人 |
| DOER | 执行 | #409eff 蓝 | 主要干活, 可多人 |
| REVIEWER | 评审 | #67c23a 绿 | 审核 PR/设计 |
| QA | 测试 | #e6a23c 黄 | 质量保证 |
| OBSERVER | 观察 | #909399 灰 | 知情不参与 |

### 4.3 矩阵 UI 行为

```
        张三  李四  王五  任务合计
1.1 需求 负责  执行         16h
       (8h) (8h)              
1.2 设计       负责  评审   24h
             (16h) (8h)     
任务合计      16h   32h  8h   80h
```

- **行**: 任务 (扁平, 按 wbs_code 升序)
- **列**: 人员 (去重, 升序)
- **单元**: 角色 tag + 工时, 空白可点 + 新增
- **行尾 +**: 弹"输入 userId" → 弹新增 (避免给全员展示空列)
- **3 重工时汇总**: 行/列/总

### 4.4 矩阵单元格交互

- **点空单元 (灰底 +)**: 弹"新增", 默认 DOER + 0h
- **点已分配 (彩色 tag + 工时)**: 弹"编辑", 可改角色/工时/起止
- **编辑弹窗删除按钮**: 软删
- **保存**: upsert by (wbs_task_id, user_id)

### 4.5 分配 = upsert

```java
// 入参 WbsAssignmentRequest
@PostMapping("/assignments")
public ApiResponse<WbsAssignmentResponse> upsert(@Valid @RequestBody WbsAssignmentRequest req) {
    WbsAssignment saved = wbsService.upsertAssignment(req);
    return ApiResponse.ok(WbsAssignmentResponse.from(saved));
}

// service 伪代码
WbsAssignment exist = repository.findByTaskIdAndUserId(req.wbsTaskId, req.userId);
if (exist == null) {
    exist = new WbsAssignment();
    exist.setWbsTaskId(req.wbsTaskId);
    exist.setUserId(req.userId);
}
exist.setRole(req.role);
exist.setPlannedHours(req.plannedHours);
exist.setActualHours(req.actualHours ?? BigDecimal.ZERO);
return repository.save(exist);
```

## 5. EVM 计算 (6 公式)

### 5.1 4 输入 + 7 派生

| 公式 | 表达式 | 业务含义 | 健康阈值 |
|---|---|---|---|
| CV | `EV - AC` | 成本偏差 | ≥ 0 |
| SV | `EV - PV` | 进度偏差 | ≥ 0 |
| CPI | `EV / AC` | 成本绩效 | ≥ 1.0 |
| SPI | `EV / PV` | 进度绩效 | ≥ 1.0 |
| EAC | `BAC / CPI` | 完工估算 | ≤ BAC × 1.1 |
| ETC | `EAC - AC` | 完工尚需 | — |
| VAC | `BAC - EAC` | 完工偏差 | ≥ 0 |

### 5.2 Java 工具类: `EvmCalculator`

**位置**: `com.company.pmo.module.wbs.evm.EvmCalculator`

```java
public final class EvmCalculator {
    // 主入口
    public static EvmResult compute(BigDecimal bac, BigDecimal pv, BigDecimal ev, BigDecimal ac);
    // 单公式
    public static BigDecimal cv(BigDecimal ev, BigDecimal ac);
    public static BigDecimal sv(BigDecimal ev, BigDecimal pv);
    public static BigDecimal cpi(BigDecimal ev, BigDecimal ac);
    public static BigDecimal spi(BigDecimal ev, BigDecimal pv);
    public static BigDecimal eac(BigDecimal bac, BigDecimal cpi);
    public static BigDecimal etc(BigDecimal eac, BigDecimal ac);
    public static BigDecimal vac(BigDecimal bac, BigDecimal eac);
}
```

**特性**:
- **纯静态**: 无 Spring 依赖, 单测秒过
- **0 除法安全**: AC=0 → CPI=1.0, PV=0 → SPI=1.0, CPI=0 → EAC=BAC
- **数值约定**: 金额 scale=2, 指数 scale=3, 舍入 HALF_UP
- **null 校验**: 任一为 null → IllegalArgumentException
- **负数校验**: 任一 < 0 → IllegalArgumentException

### 5.3 健康度 3 档

```java
public enum Health { GOOD, WARN, BAD }

public Health health() {
    double c = cpi.doubleValue();
    double s = spi.doubleValue();
    if (c >= 0.95 && s >= 0.95) return GOOD;   // 健康
    if (c < 0.85  || s < 0.85)  return BAD;    // 告警 (任一 < 0.85)
    return WARN;                                // 关注 (中间地带)
}
```

| CPI × SPI | 颜色 | 含义 | 行动 |
|---|---|---|---|
| 都 ≥ 0.95 | 🟢 GOOD | 健康 | 保持 |
| 任一 < 0.85 | 🔴 BAD | 告警 | PMO 介入 |
| 其他 | 🟡 WARN | 关注 | PM 自己盯 |

### 5.4 测试覆盖 (27 用例)

| @Nested | 数量 | 覆盖 |
|---|---|---|
| Normal | 8 | CV/SV/CPI/SPI/EAC/ETC/VAC/compute |
| ZeroDivision | 4 | AC=0 / PV=0 / CPI=0 / 全 0 |
| RealWorld | 4 | 刚启动 / CPI>1 节省 / 完美执行 / 大幅超支 |
| Health | 6 | GOOD 中心 / 0.95 边界 / WARN / BAD / 0.85 边界 |
| Precision | 2 | 金额 2 位 / 指数 3 位 |
| Validation | 3 | BAC 负数 / EV null / AC null |
| 工具类 | 1 | 反射 new → AssertionError |
| **合计** | **27** | **6 公式 × 5 边界** |

`mvn test -Dtest=EvmCalculatorTest` → **27/27 PASS, BUILD SUCCESS**.

## 6. EVM 快照引擎

### 6.1 触发方式 (3 路)

| 触发源 | 实现 | 频率 |
|---|---|---|
| 手动按钮 | `POST /wbs/snapshots/{projectId}/trigger` | PM 周一开会前 |
| 定时 Job | `EvmSnapshotJob` (Spring `@Scheduled`) | 每日 0 点 |
| 里程碑完成 | 监听 milestone 状态变更事件 | 事件驱动 |

### 6.2 SQL 函数: `pmo.fn_snapshot_evm`

**位置**: `V2.5__wbs.sql` (PG)

```sql
-- 函数签名
CREATE OR REPLACE FUNCTION pmo.fn_snapshot_evm(
    p_project_id BIGINT,
    p_source VARCHAR,        -- 'MANUAL' / 'AUTO' / 'MILESTONE'
    p_operator_id BIGINT
) RETURNS VOID AS $$
DECLARE
    v_bac NUMERIC(14,2);
    v_pv  NUMERIC(14,2);
    v_ev  NUMERIC(14,2);
    v_ac  NUMERIC(14,2);
    v_cpi NUMERIC(6,3);
    v_spi NUMERIC(6,3);
    v_eac NUMERIC(14,2);
    v_etc NUMERIC(14,2);
    v_vac NUMERIC(14,2);
    v_version INT;
BEGIN
    -- 1. 算 BAC (预算总和, 从 budget_line)
    SELECT COALESCE(SUM(amount), 0) INTO v_bac FROM budget_line WHERE project_id = p_project_id;
    -- 2. 算 PV (按计划进度到今天)
    -- 3. 算 EV (按实际进度 × BAC)
    -- 4. 算 AC (从 timesheet 汇总)
    -- 5. 算 CPI/SPI/EAC/ETC/VAC (同 EvmCalculator)
    -- 6. 算 version (同日 +1)
    SELECT COALESCE(MAX(version), 0) + 1 INTO v_version
    FROM budget_snapshot
    WHERE project_id = p_project_id AND snapshot_date = CURRENT_DATE;
    -- 7. INSERT
    INSERT INTO budget_snapshot (project_id, snapshot_date, version, bac, pv, ev, ac, cpi, spi, eac, etc, vac, reason, created_by)
    VALUES (p_project_id, CURRENT_DATE, v_version, v_bac, v_pv, v_ev, v_ac, v_cpi, v_spi, v_eac, v_etc, v_vac,
            'source=' || p_source, p_operator_id);
END;
$$ LANGUAGE plpgsql;
```

### 6.3 Java 调用 (WbsService.snapshotNow)

```java
@Transactional
public BudgetSnapshotResponse snapshotNow(Long projectId, String source, String reason) {
    validateProject(projectId);
    if (source == null || source.isBlank()) source = "MANUAL";
    // 用 native query 调用 SQL 函数
    em.createNativeQuery("SELECT pmo.fn_snapshot_evm(?, ?, ?)")
            .setParameter(1, projectId)
            .setParameter(2, source)
            .setParameter(3, securityUtils.currentUserId())  // 接 PD-3-bug-2
            .getSingleResult();
    // 拉最新一条
    BudgetSnapshot latest = snapshotRepository.findLatestByProject(projectId).stream()
            .findFirst()
            .orElseThrow(() -> new BusinessException("Snapshot 函数执行后未找到记录"));
    return BudgetSnapshotResponse.from(latest);
}
```

### 6.4 4 个查询端点

| 端点 | 用途 |
|---|---|
| GET /wbs/snapshots/{projectId} | 最近 20 条 |
| GET /wbs/snapshots/{projectId}/range?from=&to= | 日期区间 |
| GET /wbs/snapshots/{projectId}/trend?days=30 | 趋势 (每天 1 条) |

## 7. UI 行为

### 7.1 WbsView 三视图切换 (顶部)

```vue
<el-radio-group v-model="viewMode">
  <el-radio-button value="tree">🌲 树视图</el-radio-button>
  <el-radio-button value="gantt">📊 甘特图</el-radio-button>
  <el-radio-button value="network">🕸 网络图</el-radio-button>
</el-radio-group>
```

### 7.2 树视图 (WbsTreeView.vue, 418 行)

- **Element Plus el-tree** 渲染嵌套 children
- **自定义节点**: 编码/名称/状态 tag/进度条/权重
- **5 状态** + **4 任务类型** 颜色映射
- **右键菜单**: 新增子任务 / 编辑 / 删除 / 触发 EVM 快照
- **左: 树 + 右: 详情面板** (el-descriptions 11 字段)

### 7.3 任务编辑弹窗 (WbsTaskEditDialog.vue, 344 行)

- **22 字段** 一站式录入
- **5 必填校验**: wbsCode / name / taskType / status / weight
- **进度 0-100** CHECK
- **3 列并排**: 权重/进度/负责人
- **紧前任务**: 逗号分隔 id 输入 (快速录入)
- **面包屑**: 弹窗头部显示所在层级

### 7.4 资源分配矩阵 (WbsAssignmentMatrix.vue, 472 行)

- **任务 × 人员** 二维表
- **5 角色** 彩色 tag
- **3 重工时汇总**: 行/列/总
- **点空单元** → 弹新增 / **点 + 列** → 输 userId 弹新增
- **任务列** sticky 横向滚动冻结

### 7.5 EVM 趋势卡片 (EvmTrendCard.vue, 315 行)

- **9 KPI 一行**: BAC/PV/EV/AC/CV/SV/CPI/SPI/EAC
- **健康度 tag**: 🟢/🟡/🔴
- **双轴折线图**: 左 ¥ (PV/EV/AC) + 右 0.5-1.5 (CPI/SPI + 1.0 基准线)
- **3 档时间窗**: 7/30/90 天
- **手动触发快照按钮**

## 8. 错误码

| 错误码 | HTTP | 触发场景 |
|---|:---:|---|
| `WBS-001` | 400 | wbsCode 项目内重复 |
| `WBS-002` | 400 | progressPct 越界 (0-100) |
| `WBS-003` | 400 | weight 越界 (1-10) |
| `WBS-004` | 400 | planHours 负数 |
| `WBS-005` | 400 | parentId 形成环 (自引用/祖先环) |
| `WBS-006` | 400 | predecessorIds 含自身或环 |
| `WBS-007` | 404 | taskId 不存在 |
| `WBS-008` | 400 | auto-reorder 检测到环, 返回环路径 |
| `WBS-009` | 400 | 拖到自己的子节点下 (a 步) |
| `WBS-010` | 500 | 快照函数执行后未找到记录 |
| `ASN-001` | 409 | assignment 同 (task,user) 已被删除恢复冲突 |
| `ASN-002` | 404 | assignmentId 不存在 |
| `EVM-001` | 400 | bac 负数 (前端校验, 后端兜底) |
| `EVM-002` | 500 | SQL 函数执行失败 |

## 9. 验收 + 风险

### 9.1 验收标准 (本 PR-3 维度)

| # | 验收项 | 验证方式 |
|:---:|---|---|
| 1 | WBS CRUD 5 端点通过 ControllerTest | WbsControllerTest (15 用例) |
| 2 | 树组装 O(n) 性能 | 500 任务 < 100ms |
| 3 | 拖拽 a+b 步事务一致 | WbsServiceTest 移动用例 |
| 4 | 资源矩阵 upsert 正确 | WbsServiceTest 分配用例 |
| 5 | EVM 27 用例全过 | EvmCalculatorTest 27/27 |
| 6 | EVM 快照 append-only | 集成测, 尝试 UPDATE/DELETE 应失败 |
| 7 | 22 字段表单校验 | WbsTaskEditDialog 手动测 |

### 9.2 风险与待定

| # | 风险 | 缓解 |
|:---:|---|---|
| 1 | 拖拽 a 步子树级联编号, 子树大时慢 | 限制 ≤ 500 节点, 超过分批异步 |
| 2 | EVM 快照 SQL 函数 H2 测不到 | 集成测用 Testcontainers PG |
| 3 | 资源矩阵同 (task,user) 软删恢复可能冲突 | 数据库 UNIQUE 约束兜底, 业务层先 find |
| 4 | EVM 趋势 90 天数据量大 | 趋势接口 `findTrendSince` 已用 native query 优化 |
| 5 | 前端 22 字段表单压成 4-5 步 (wizard) | v2 再说, 当前一屏展平 |

---

## 10. 关联文档

- 前置: [PR-1-概述与角色.md](./PR-1-概述与角色.md) / [PR-2-数据模型.md](./PR-2-数据模型.md)
- 后续: [PR-4-风险与可视化.md](./PR-4-风险与可视化.md)
- SQL: `V2.5__wbs.sql` (PG) / `V2.5__wbs.sql` (MySQL)
