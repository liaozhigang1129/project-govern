---
status: active
created: 2026-08-07
updated: 2026-08-07
summary: 工时→成本引擎 P0-A(数据模型 + 4 级费率解析 + 12 端点 + 多维度视图)
---

# 成本引擎(Cost Engine)

> 单一事实来源:工时→成本核算的数据模型、4 级费率解析算法、12 个 API 端点、多维度视图。
> 对应来源:[`legacy/pmo-pms-cost-engine.md`](legacy/pmo-pms-cost-engine.md)(legacy 留作完整对照)

---

## 1. 业务目标(P0-A 对应 PRD F1 + F2)

### 1.1 看得见 — F1 工时→成本

- 财务上传月度调薪 CSV → 系统自动按(用户 × 月份 × 项目)计算成本
- 任何人/任何月都有成本值(兜底到 0 不报错,前端标记"未设价")

### 1.2 多维度可切 — F2 项目/阶段/部门

- 3 张 PG 视图:`v_project_cost` / `v_phase_cost` / `v_dept_cost`
- 单端点 `GET /api/cost/dimension?dim=PROJECT|PHASE|DEPT&month=YYYY-MM` 切换
- 不写 Java 聚合 SQL,全部交给 PG 视图 + `fn_resolve_hourly_rate` 函数

### 1.3 暂未覆盖(留后续)

- 预算/实际/偏差 → F4 规则引擎
- ROI / EAC / ETC → F4 挣值分析
- 高层 4 指标黄金区 → F5 驾驶舱

---

## 2. 数据模型

### 2.1 表清单

| 表 | 来源 | 用途 | 关键字段 |
|---|---|---|---|
| `app_user.default_hourly_rate` | V4.0 ① 增量列 | 兜底(L4) | `NUMERIC(10,2) NOT NULL DEFAULT 0` |
| `hourly_rate_v4` | V4.0 ② 新表 | 人×月×角色 override(L1/L2) | `UNIQUE(user_id, role_code, effective_month)` |
| `role_cost_default` | V4.0 ③ 新字典 | 6 角色档默认价(L3) | `code PK` |
| `system_config` | V2.10 新表 | 业务可调参数(F2/F4 通用) | `config_key UNIQUE` |
| `v_project_cost` / `v_phase_cost` / `v_dept_cost` | V4.1 视图 | F2 多维度核算 | view only |
| `fn_resolve_hourly_rate()` | V4.1 函数 | 视图用,4 级兜底解析 | plpgsql |

### 2.2 hourly_rate_v4 字段设计

```sql
CREATE TABLE hourly_rate_v4 (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role_code       VARCHAR(32) NOT NULL REFERENCES role_cost_default(code),
    rate            NUMERIC(10,2) NOT NULL,           -- 元/小时, 必须 > 0
    effective_month DATE NOT NULL,                     -- YYYY-MM-01, 生效起点
    end_month       DATE,                              -- YYYY-MM-01, 含; null=开放
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT REFERENCES app_user(id),
    remark          VARCHAR(256),
    UNIQUE (user_id, role_code, effective_month)
);
CREATE INDEX idx_hourly_rate_v4_user_month ON hourly_rate_v4(user_id, effective_month DESC);
CREATE INDEX idx_hourly_rate_v4_role_month ON hourly_rate_v4(role_code, effective_month DESC);
```

---

## 3. 4 级费率解析算法(核心)

`CostRateResolver.resolveRate(timesheetEntry, project, user, period)`:

```
1. (project_id, user_id, role_code, period)  ← 项目级角色特定 (L1)
2. (project_id, user_id, period)             ← 项目级人员特定 (L2)
3. (project_id, role_code, period)           ← 项目级角色通用 (L3)
4. (department_id, role_code, period)        ← 部门级角色通用 (L4)
```

**算法**:
1. 用 4 个 key 分别查 `hourly_rate_v4` 表
2. **按优先级返回第一个非空**
3. 全 null → 兜底到 `app_user.default_hourly_rate`(允许 0)

详见 [`algorithms.md` §5](algorithms.md)。

---

## 4. API 契约(12 端点 · CostController)

> 完整 OpenAPI 见 [`openapi/openapi.json`](openapi/openapi.json)。
> 以下为摘要,字段级契约请查 swagger UI。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/cost/dimension` | 多维度核算(项目/阶段/部门),按月查询 |
| GET | `/api/cost/user/{userId}` | 单人月度成本曲线 |
| GET | `/api/cost/project/{projectId}` | 单项目月度成本 |
| GET | `/api/cost/dashboard` | 成本驾驶舱 4 指标 |
| GET | `/api/cost/rates` | hourly_rate_v4 列表 |
| POST | `/api/cost/rates` | 新增费率 |
| PUT | `/api/cost/rates/{id}` | 更新费率 |
| DELETE | `/api/cost/rates/{id}` | 删除费率 |
| GET | `/api/cost/rates/template` | 下载 CSV 模板 |
| POST | `/api/cost/rates/import` | 上传 CSV 批量 |
| GET | `/api/cost/roles` | role_cost_default 字典 |
| POST | `/api/cost/roles/{code}/default-rate` | 更新角色默认费率 |

---

## 5. CSV 上传/下载契约

模板格式:`user_id, role_code, rate, effective_month, end_month, remark`

- 上传:服务端解析后���条校验,失败行返回 `{row, error}` 不阻塞其他行
- 下载:导出当前生效的所有费率(按 user_id 排序)
- 详见 `legacy/pmo-pms-cost-engine.md` §5

---

## 6. 前端交付

- `frontend/src/api/cost.ts` — 12 端点客户端
- `frontend/src/views/admin/HourlyRateAdmin.vue` — 费率管理页(CRUD + CSV 导入)
- `frontend/src/views/CostUserMonth.vue` — 单人月度成本曲线(ECharts 折线图)
- 路由 + 菜单权限同步

---

## 7. 验收用例(与 PRD §四 对齐)

主验收:**¥24,000 月度工时→成本核算**(PRD F1)。

27 个单测全绿(`HourlyRateServiceTest` 22 + `CostEngineServiceTest` 5)。详见 `legacy/pmo-pms-cost-engine.md` §7。
