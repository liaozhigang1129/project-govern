# PR-2: P3 数据模型

> **版本**: v0.1 (草稿)
> **作者**: PMO 研发组
> **评审**: @DBA @后端 @架构师
> **更新**: 2025-06-10
> **状态**: ⏳ 评审中
> **依赖**: PR-1 (角色术语), V2.5__wbs.sql / V2.6__risk.sql / V2.7__risk_history_deleted_action.sql

---

## 1. ER 总体图

### 1.1 mermaid 关系图

```mermaid
erDiagram
    project ||--o{ wbs_task : "1:N"
    wbs_task ||--o{ wbs_task : "parent 自引用"
    wbs_task ||--o{ wbs_assignment : "1:N"
    app_user ||--o{ wbs_assignment : "1:N"
    wbs_task ||--o{ budget_snapshot : "间接(按 projectId)"
    project ||--o{ risk : "1:N"
    risk ||--o{ risk_response : "1:N"
    risk ||--o{ risk_history : "1:N"
    wbs_task ||--o{ risk : "软关联 (可选)"
    milestone ||--o{ wbs_task : "软关联 milestone_id"

    wbs_task {
        bigint id PK
        bigint project_id FK
        bigint parent_id FK_self
        varchar wbs_code "项目内唯一"
        varchar task_type "SUMMARY/EXECUTION/MILESTONE/DELIVERABLE"
        varchar status "5 态"
        bigint[] predecessor_ids "PG 数组, MySQL 用 JSON"
    }
    wbs_assignment {
        bigint id PK
        bigint wbs_task_id FK
        bigint user_id FK
        varchar role "5 角色"
        numeric planned_hours
    }
    budget_snapshot {
        bigint id PK
        bigint project_id FK
        date snapshot_date
        numeric bac_pv_ev_ac "14,2"
        numeric cpi_spi "6,3"
    }
    risk {
        bigint id PK
        bigint project_id FK
        varchar code "R-001"
        int probability "1-5"
        int impact "1-5"
        int score "1-25"
        varchar level "LOW/MEDIUM/HIGH/CRITICAL"
    }
    risk_response {
        bigint id PK
        bigint risk_id FK
        varchar status "4 态"
    }
    risk_history {
        bigint id PK
        bigint risk_id FK
        varchar action "8 种"
    }
```

### 1.2 实体清单 (8 张表)

| # | 表名 | 业务实体 | 行数估 | 关键字段 |
|:---:|---|---|:---:|---|
| 1 | `wbs_task` | WBS 任务节点 | 1-N/项目 | wbs_code, parent_id, predecessor_ids |
| 2 | `wbs_assignment` | 任务-人员分配 | 5-20/任务 | wbs_task_id, user_id, role, planned_hours |
| 3 | `budget_snapshot` | EVM 快照 (append-only) | 1/天/项目 | snapshot_date, bac, pv, ev, ac, cpi, spi |
| 4 | `risk` | 项目风险登记 | 5-30/项目 | code, probability, impact, score, level |
| 5 | `risk_response` | 风险应对行动 | 1-5/风险 | risk_id, action, owner_user_id, status |
| 6 | `risk_history` | 风险变更历史 (append-only) | 5-50/风险 | risk_id, action, field_name, old/new_value |
| 7 | (规划) `budget_line` | 预算科目 | 5-20/项目 | P3 不深用, 留扩展 |
| 8 | (规划) `hourly_rate` | 人员费率 | 1-N/项目 | P3 不深用, 留扩展 |

## 2. 主表: wbs_task

### 2.1 业务定位

WBS 任务节点, 树形结构, **100% 原则** (叶子节点 100% 归到父节点), 引用 PMBOK 第 6/7 版 + DCMA 14-point + ANSI/EIA-748 EVM 标准.

### 2.2 字段表 (26 字段)

| # | 字段 | 类型 | 必填 | 默认 | 说明 |
|:---:|---|---|:---:|---|---|
| 1 | `id` | BIGSERIAL PK | ✅ | auto | 主键 |
| 2 | `project_id` | BIGINT FK→project | ✅ | — | 所属项目, ON DELETE CASCADE |
| 3 | `parent_id` | BIGINT FK→wbs_task.id | ❌ | NULL | 父任务, 顶层=NULL, ON DELETE CASCADE |
| 4 | `wbs_code` | VARCHAR(32) | ✅ | — | 项目内唯一, 形如 `1.1.2` |
| 5 | `name` | VARCHAR(256) | ✅ | — | 任务名 |
| 6 | `task_type` | VARCHAR(16) | ✅ | — | SUMMARY/EXECUTION/MILESTONE/DELIVERABLE |
| 7 | `status` | VARCHAR(16) | ✅ | 'NOT_STARTED' | 5 态, 见 §6 |
| 8 | `owner_user_id` | BIGINT FK→app_user | ❌ | NULL | 负责人 |
| 9 | `plan_start_date` | DATE | ❌ | NULL | 计划开始 |
| 10 | `plan_end_date` | DATE | ❌ | NULL | 计划结束 |
| 11 | `actual_start_date` | DATE | ❌ | NULL | 实际开始 |
| 12 | `actual_end_date` | DATE | ❌ | NULL | 实际结束 |
| 13 | `plan_hours` | NUMERIC(10,2) | ✅ | 0 | 计划工时 (人时) |
| 14 | `actual_hours` | NUMERIC(10,2) | ✅ | 0 | 实际工时 (从工时表汇总) |
| 15 | `progress_pct` | INT | ✅ | 0 | 0-100 |
| 16 | `weight` | INT | ✅ | 1 | 1-10, 加权用 |
| 17 | `is_critical` | BOOLEAN | ✅ | FALSE | 关键路径 (CPM) |
| 18 | `is_milestone` | BOOLEAN | ✅ | FALSE | 里程碑节点 |
| 19 | `milestone_id` | BIGINT FK→milestone | ❌ | NULL | 软关联现有 milestone |
| 20 | `predecessor_ids` | BIGINT[] (PG) / JSON (MySQL) | ✅ | '{}' | 前置任务 ID 列表 (CPM) |
| 21 | `deliverable` | TEXT | ❌ | NULL | 交付物描述 |
| 22 | `remark` | TEXT | ❌ | NULL | 备注 |
| 23 | `created_by` | BIGINT FK→app_user | ❌ | NULL | 创建人 |
| 24 | `created_at` | TIMESTAMPTZ | ✅ | NOW() | — |
| 25 | `updated_at` | TIMESTAMPTZ | ✅ | NOW() | JPA @PreUpdate 自动维护 |
| 26 | `deleted` | BOOLEAN | ✅ | FALSE | 软删标志 |

### 2.3 约束 (5 条)

| 约束 | 类型 | 字段 | 规则 |
|---|---|---|---|
| `uq_wbs_project_code` | UNIQUE | (project_id, wbs_code) | 项目内编码唯一 |
| `ck_wbs_progress` | CHECK | progress_pct | BETWEEN 0 AND 100 |
| `ck_wbs_weight` | CHECK | weight | BETWEEN 1 AND 10 |
| `ck_wbs_plan_hours` | CHECK | plan_hours | >= 0 |
| `ck_wbs_actual_hours` | CHECK | actual_hours | >= 0 |
| `ck_wbs_task_type` | CHECK | task_type | IN 4 值 |
| `ck_wbs_status` | CHECK | status | IN 5 值 |

### 2.4 数据库差异 (PG vs MySQL vs H2)

| 字段 | PostgreSQL (生产) | MySQL (兼容) | H2 (测试) |
|---|---|---|---|
| `id` | BIGSERIAL | BIGINT AUTO_INCREMENT | BIGINT IDENTITY |
| `predecessor_ids` | `BIGINT[]` (原生数组) | `JSON` | `BIGINT ARRAY` |
| `created_at` | TIMESTAMPTZ | DATETIME(6) | TIMESTAMP WITH TIME ZONE |
| `updated_at` 触发器 | TRIGGER `set_updated_at` | 应用层 @PreUpdate | 应用层 @PreUpdate |

## 3. 关联表: wbs_assignment + budget_snapshot

### 3.1 wbs_assignment — 任务-人员分配

**业务定位**: 解决"谁负责这任务, 占多少工时", 矩阵视图数据源.

#### 3.1.1 字段表 (10 字段)

| # | 字段 | 类型 | 必填 | 默认 | 说明 |
|:---:|---|---|:---:|---|---|
| 1 | `id` | BIGSERIAL PK | ✅ | auto | 主键 |
| 2 | `wbs_task_id` | BIGINT FK→wbs_task | ✅ | — | 任务 ID, ON DELETE CASCADE |
| 3 | `user_id` | BIGINT FK→app_user | ✅ | — | 人员 ID |
| 4 | `role` | VARCHAR(64) | ❌ | 'DOER' | LEAD/DOER/REVIEWER/QA/OBSERVER |
| 5 | `planned_hours` | NUMERIC(10,2) | ✅ | 0 | 计划工时 (人时) |
| 6 | `actual_hours` | NUMERIC(10,2) | ✅ | 0 | 实际工时 |
| 7 | `start_date` | DATE | ❌ | NULL | 分配开始 |
| 8 | `end_date` | DATE | ❌ | NULL | 分配结束 |
| 9 | `note` | TEXT | ❌ | NULL | 备注 |
| 10 | 软删字段 (3) | — | — | — | deleted, created_at, updated_at |

#### 3.1.2 约束

- `uq_wbs_assignment_task_user` UNIQUE (wbs_task_id, user_id) — 同一任务同一人员只一行, 改工时用 UPDATE 不用新增
- ON DELETE CASCADE (删任务 → 删分配)

### 3.2 budget_snapshot — EVM 快照 (append-only)

**业务定位**: 不可变历史, 触发器禁 UPDATE/DELETE, 趋势图/最新指标全读此表.

#### 3.2.1 字段表 (16 字段)

| # | 字段 | 类型 | 必填 | 默认 | 说明 |
|:---:|---|---|:---:|---|---|
| 1 | `id` | BIGSERIAL PK | ✅ | auto | 主键 |
| 2 | `project_id` | BIGINT FK→project | ✅ | — | 所属项目 |
| 3 | `snapshot_date` | DATE | ✅ | — | 快照日期 (一天可多次, version 累加) |
| 4 | `version` | INT | ✅ | 1 | 同一天第几次, 默认 1 |
| 5 | `reason` | VARCHAR(256) | ❌ | NULL | 触发原因 (MANUAL/AUTO/MILESTONE) |
| 6-9 | `bac/pv/ev/ac` | NUMERIC(14,2) | ✅ | 0 | 4 大原始值 |
| 10-11 | `cpi/spi` | NUMERIC(6,3) | ✅ | 1.0 | 成本/进度绩效 |
| 12 | `eac` | NUMERIC(14,2) | ✅ | 0 | 完工估算 |
| 13 | `etc` | NUMERIC(14,2) | ✅ | 0 | 完工尚需 |
| 14 | `vac` | NUMERIC(14,2) | ✅ | 0 | 完工偏差 |
| 15 | `created_by` | BIGINT FK→app_user | ❌ | NULL | 触发人 |
| 16 | `created_at` | TIMESTAMPTZ | ✅ | NOW() | — |

#### 3.2.2 触发器 (PG 专属)

```sql
CREATE OR REPLACE FUNCTION fn_budget_snapshot_immutable()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'budget_snapshot 是 append-only, 不允许 UPDATE/DELETE';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_budget_snapshot_no_update
BEFORE UPDATE ON budget_snapshot
FOR EACH ROW EXECUTE FUNCTION fn_budget_snapshot_immutable();

CREATE TRIGGER trg_budget_snapshot_no_delete
BEFORE DELETE ON budget_snapshot
FOR EACH ROW EXECUTE FUNCTION fn_budget_snapshot_immutable();
```

**H2 测试兼容**: 测试用 ddl-auto=create-drop, **无触发器**, 单测要绕开"禁更新"假设.

## 4. 风险 3 表: risk + risk_response + risk_history

### 4.1 risk — 项目风险登记

**业务定位**: 5×5 概率×影响矩阵源数据, 框架: PMBOK 7 + ISO 31000.

#### 4.1.1 字段表 (17 字段)

| # | 字段 | 类型 | 必填 | 默认 | 说明 |
|:---:|---|---|:---:|---|---|
| 1 | `id` | BIGSERIAL PK | ✅ | auto | 主键 |
| 2 | `project_id` | BIGINT FK→project | ✅ | — | 所属项目, ON DELETE CASCADE |
| 3 | `code` | VARCHAR(32) | ✅ | — | 项目内唯一, R-001/R-002... |
| 4 | `title` | VARCHAR(256) | ✅ | — | 风险标题 |
| 5 | `description` | TEXT | ❌ | NULL | 详细描述 |
| 6 | `category` | VARCHAR(16) | ✅ | — | TECHNICAL/SCHEDULE/COST/QUALITY/EXTERNAL/ORGANIZATIONAL/OTHER |
| 7 | `probability` | INT | ✅ | — | 1-5 |
| 8 | `impact` | INT | ✅ | — | 1-5 |
| 9 | `score` | INT | ✅ | — | 1-25 (probability × impact, 后端自动算) |
| 10 | `level` | VARCHAR(16) | ✅ | — | LOW/MEDIUM/HIGH/CRITICAL (由 score 自动推导) |
| 11 | `status` | VARCHAR(16) | ✅ | 'OPEN' | OPEN/MITIGATING/CLOSED/OCCURRED/ACCEPTED |
| 12 | `owner_user_id` | BIGINT FK→app_user | ❌ | NULL | 责任人 |
| 13 | `mitigation` | TEXT | ❌ | NULL | 预防/缓解措施 |
| 14 | `contingency` | TEXT | ❌ | NULL | 应急/兜底措施 |
| 15 | `response_strategy` | VARCHAR(16) | ❌ | NULL | AVOID/MITIGATE/TRANSFER/ACCEPT/EXPLOIT/ENHANCE/SHARE |
| 16 | `identified_date` | DATE | ✅ | today | 识别日期 |
| 17 | `target_close_date` | DATE | ❌ | NULL | 目标关闭日期 |

#### 4.1.2 约束

- `uk_risk_project_code` UNIQUE (project_id, code)
- 软删字段 (3): deleted, created_at, updated_at

### 4.2 risk_response — 风险应对行动

**业务定位**: 一个风险可挂多条措施, 解决"一个风险多个动作"的场景 (PMBOK 7 推荐).

#### 4.2.1 字段表 (8 字段)

| # | 字段 | 类型 | 必填 | 默认 | 说明 |
|:---:|---|---|:---:|---|---|
| 1 | `id` | BIGSERIAL PK | ✅ | auto | 主键 |
| 2 | `risk_id` | BIGINT FK→risk | ✅ | — | 风险 ID, ON DELETE CASCADE |
| 3 | `action` | VARCHAR(256) | ✅ | — | 应对动作描述 |
| 4 | `owner_user_id` | BIGINT FK→app_user | ❌ | NULL | 负责人 |
| 5 | `due_date` | DATE | ❌ | NULL | 截止日期 |
| 6 | `completed_at` | TIMESTAMP | ❌ | NULL | 完成时间 |
| 7 | `status` | VARCHAR(16) | ✅ | 'PLANNED' | PLANNED/IN_PROGRESS/DONE/CANCELLED |
| 8 | `note` | TEXT | ❌ | NULL | 备注 |

### 4.3 risk_history — 风险变更历史 (审计追踪)

**业务定位**: 记录状态/分数/责任人/评论变更, 软删也写 history, **不物理删**.

#### 4.3.1 字段表 (8 字段)

| # | 字段 | 类型 | 必填 | 默认 | 说明 |
|:---:|---|---|:---:|---|---|
| 1 | `id` | BIGSERIAL PK | ✅ | auto | 主键 |
| 2 | `risk_id` | BIGINT FK→risk | ✅ | — | 风险 ID |
| 3 | `action` | VARCHAR(32) | ✅ | — | 8 种: CREATED/STATUS_CHANGED/SCORE_CHANGED/OWNER_CHANGED/LEVEL_CHANGED/COMMENTED/RESPONSE_ADDED/RESPONSE_DONE/DELETED |
| 4 | `field_name` | VARCHAR(64) | ❌ | NULL | 字段名 (e.g. status, score) |
| 5 | `old_value` | TEXT | ❌ | NULL | 旧值 (JSON) |
| 6 | `new_value` | TEXT | ❌ | NULL | 新值 (JSON) |
| 7 | `comment` | TEXT | ❌ | NULL | 评论 |
| 8 | `operator_id` | BIGINT FK→app_user | ❌ | NULL | 操作人 |
| 9 | `created_at` | TIMESTAMPTZ | ✅ | NOW() | DB 默认值, 不可改 |

#### 4.3.2 V2.7 补丁 (action 新增 DELETED)

`V2.7__risk_history_deleted_action.sql` 扩大 action 字段长度并允许 'DELETED' 值, 适配软删场景.

## 5. 约束矩阵

### 5.1 唯一约束 (4 条)

| 表 | 约束名 | 字段 | 规则 |
|---|---|---|---|
| wbs_task | `uq_wbs_project_code` | (project_id, wbs_code) | 项目内编码唯一 |
| wbs_assignment | `uk_wbs_assignment_task_user` | (wbs_task_id, user_id) | 同一任务同一人员只一行 |
| risk | `uk_risk_project_code` | (project_id, code) | 项目内风险编号唯一 |
| budget_snapshot | (隐式 PK) | (project_id, snapshot_date, version) | 同一天可多次, version 累加 |

### 5.2 CHECK 约束 (8 条)

| 表 | 约束 | 规则 |
|---|---|---|
| wbs_task | progress_pct | BETWEEN 0 AND 100 |
| wbs_task | weight | BETWEEN 1 AND 10 |
| wbs_task | plan_hours | >= 0 |
| wbs_task | actual_hours | >= 0 |
| wbs_task | task_type | IN 4 值 |
| wbs_task | status | IN 5 值 |
| risk | probability | BETWEEN 1 AND 5 |
| risk | impact | BETWEEN 1 AND 5 |
| risk | score | BETWEEN 1 AND 25 |

### 5.3 外键与级联

| 表.字段 | 引用 | ON DELETE | ON UPDATE |
|---|---|---|---|
| wbs_task.project_id | project.id | CASCADE | RESTRICT |
| wbs_task.parent_id | wbs_task.id | CASCADE | RESTRICT |
| wbs_task.owner_user_id | app_user.id | SET NULL | RESTRICT |
| wbs_task.milestone_id | milestone.id | SET NULL | RESTRICT |
| wbs_assignment.wbs_task_id | wbs_task.id | CASCADE | RESTRICT |
| wbs_assignment.user_id | app_user.id | RESTRICT | RESTRICT |
| budget_snapshot.project_id | project.id | CASCADE | RESTRICT |
| risk.project_id | project.id | CASCADE | RESTRICT |
| risk_response.risk_id | risk.id | CASCADE | RESTRICT |
| risk_history.risk_id | risk.id | CASCADE | RESTRICT |

**关键风险**:
- `wbs_task.project_id` CASCADE → 删项目会**级联删全部 WBS**, 需 PMO 二次确认
- `wbs_task.parent_id` CASCADE → 删父任务**级联删子树**, 删前需前端提示"将级联删除 N 个子任务"

## 6. 状态机

### 6.1 wbs_task.status (5 态)

```
            ┌──────────┐
            │ NOT_     │
       ┌───►│ STARTED  │◄──┐
       │    └────┬─────┘   │
       │         │         │
       │    ┌────▼─────┐   │
       │    │   IN_    │   │
       │    │ PROGRESS │───┤ (回到 IN_PROGRESS, 重启任务)
       │    └────┬─────┘   │
       │         │         │
  取消 │    ┌────▼─────┐   │ 阻塞解除
       └────┤ BLOCKED  │───┘
            └──────────┘
                │
       ┌────────┴────────┐
       ▼                 ▼
  ┌─────────┐       ┌──────────┐
  │COMPLETED│       │ CANCELLED│ (终态)
  └─────────┘       └──────────┘
```

| 状态 | 终态? | 触发 |
|---|---|---|
| NOT_STARTED | ❌ | 初始 / 从 IN_PROGRESS/BLOCKED 回滚 |
| IN_PROGRESS | ❌ | 开始执行 / 阻塞解除 |
| BLOCKED | ❌ | 遇到依赖/资源/问题 |
| COMPLETED | ✅ | 进度 100% + 实际结束日期已填 |
| CANCELLED | ✅ | PM 取消 (不打算做了) |

### 6.2 risk.status (5 态)

```
         ┌─────┐
         │OPEN │ (识别)
         └──┬──┘
            │
   ┌────────┼────────┐
   ▼        ▼        ▼
┌──────┐ ┌──────┐ ┌────────┐
│MITI- │ │ACCEPT│ │OCCURRED│
│GATING│ │ED    │ │(已发生)│
└──┬───┘ └──┬───┘ └───┬────┘
   │        │         │
   └────────┼─────────┘
            ▼
        ┌──────┐
        │CLOSED│ (终态)
        └──────┘
```

### 6.3 risk_response.status (4 态)

```
PLANNED → IN_PROGRESS → DONE  (终态)
                    ↘ CANCELLED (终态)
```

## 7. 编码规则

### 7.1 wbs_task.wbs_code

- **格式**: `1`, `1.1`, `1.1.2` (点分数字, ≤ 5 层)
- **项目内唯一**: 同一 project_id 下唯一
- **生成策略**: 拖拽重排时自动重编号 (a 步: 换 parent + 子树级联)
- **可手动改**: 但要保持项目内唯一
- **建议深度**: ≤ 5 层 (PMBOK 推荐 3-6 层)
- **长度**: ≤ 32 字符

### 7.2 risk.code

- **格式**: `R-001`, `R-002`, `R-003`...
- **项目内唯一**: 同 project_id
- **生成策略**: 新建时自动 = `MAX(code) + 1` (项目内)
- **软删后不复用**: 删除 R-003 后, 新建风险从 R-004 开始 (不补 003)
- **长度**: ≤ 32 字符

## 8. 索引策略

### 8.1 wbs_task (5 个索引)

| 索引名 | 字段 | 类型 | 场景 |
|---|---|---|---|
| `pk_wbs_task` | id | BTREE PK | 主键 |
| `uq_wbs_project_code` | (project_id, wbs_code) | UNIQUE BTREE | 唯一 + 按项目查 |
| `idx_wbs_project_parent` | (project_id, parent_id) | BTREE | 树组装 (按项目+父) |
| `idx_wbs_status` | (project_id, status) | BTREE | 状态过滤 |
| `idx_wbs_critical` | (project_id, is_critical) WHERE is_critical | 部分 BTREE | 关键路径查询 |
| `idx_wbs_milestone` | (project_id, is_milestone) WHERE is_milestone | 部分 BTREE | 里程碑过滤 |

### 8.2 wbs_assignment (2 个索引)

| 索引名 | 字段 | 场景 |
|---|---|---|
| `pk_wbs_assignment` | id | 主键 |
| `uk_wbs_assignment_task_user` | (wbs_task_id, user_id) | 唯一 + 按任务查 |
| `idx_wbs_assignment_user` | (user_id) | 资源模块"我的任务" |

### 8.3 budget_snapshot (3 个索引)

| 索引名 | 字段 | 场景 |
|---|---|---|
| `pk_budget_snapshot` | id | 主键 |
| `idx_budget_project_date` | (project_id, snapshot_date DESC) | 最新一条 / 趋势 |
| `idx_budget_project_range` | (project_id, snapshot_date) | 日期区间 |

### 8.4 risk / risk_response / risk_history (各 2-3 个)

| 表 | 索引 | 字段 |
|---|---|---|
| risk | pk + uk + idx | id, (project_id, code), (project_id, status), (project_id, score DESC) |
| risk_response | pk + idx | id, (risk_id), (status, due_date) |
| risk_history | pk + idx | id, (risk_id, created_at DESC) |

## 9. 验收 + 风险

### 9.1 验收标准 (本 PR-2 维度)

| # | 验收项 | 验证方式 |
|:---:|---|---|
| 1 | 8 实体字段表与实际 DDL 一致 | 对照 V2.5/V2.6/V2.7 SQL |
| 2 | 4 条唯一约束可被 SQL 拒绝重复 | 集成测试 |
| 3 | 8 条 CHECK 约束可被 SQL 拒绝越界 | 集成测试 |
| 4 | 状态机转换图与代码 if/else 分支一致 | WbsServiceTest, RiskServiceTest |
| 5 | 索引覆盖度 ≥ 80% 高频查询 | EXPLAIN ANALYZE |

### 9.2 风险与待定

| # | 风险 | 缓解 |
|:---:|---|---|
| 1 | `predecessor_ids` PG 数组, MySQL 不支持 | MySQL 用 JSON + Java 端 List<Long> 互转 |
| 2 | 删项目 CASCADE 删 WBS, 风险大 | 软删项目 (deleted=true), 不真删 |
| 3 | budget_snapshot PG 触发器 H2 测不到 | 单测用 @Sql 跳过, 集成测用 Testcontainers PG |
| 4 | risk.code 软删不复用, 长期可能编号稀疏 | 加 `version` 或允许手动指定 (后续) |
| 5 | wbs_code 重编号 a 步, 性能 O(n²) | 限制子树规模 ≤ 500 节点, 超过分批 |

---

## 10. 关联文档

- 前置: [PR-1-概述与角色.md](./PR-1-概述与角色.md)
- 后续: [PR-3-核心功能-任务与资源.md](./PR-3-核心功能-任务与资源.md)
- SQL: `V2.5__wbs.sql` / `V2.6__risk.sql` / `V2.7__risk_history_deleted_action.sql`

