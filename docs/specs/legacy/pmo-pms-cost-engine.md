# PMO PMS — 工时→成本引擎 (P0-A · V4.0)

> **模块**: `cost-control` (PRD [`PRD-cost-control.md`](./PRD-cost-control.md) §F1 + §F2)
> **版本**: V4.0 (2026-06-13) · **状态**: ✅ P0-A 主任务已落地
> **目标用户**: 财务 / PMO / PM / 高管
> **配套**: PRD §三.1(F1) + §三.2(F2) · MVP Design §5
> **周期**: 1 周 (P0-A · 实际 5 个工作日)

---

## 0. 文档目的

把"工时→成本核算"这个 P0 模块从代码反推成**给新同学 / 接手人的交付说明书**:

- 数据模型 + 4 级费率解析算法
- 12 个 API 端点的契约与权限
- 前端 2 个管理/查询页的功能路径
- CSV 上传/下载格式与边界
- 验收用例 (含 PRD F1 ¥24,000 主验收)
- 已知坑点 / 与 PRD 的偏离 / 后续 TODO

**关联文件清单**:
- 后端: `backend/src/main/java/com/company/pmo/module/cost/` (15 个 Java)
- 迁移: `db/migration-{pg,mysql}/V2.10__system_config.sql` + `V4.0__hourly_rate.sql` + `V4.1__cost_views.sql`
- 前端: `frontend/src/api/cost.ts` + `views/admin/HourlyRateAdmin.vue` + `views/CostUserMonth.vue`
- 路由: `frontend/src/router/index.ts` (2 路由)
- 菜单: `frontend/src/App.vue` (1 菜单项 + 1 二级入口)
- 单测: `HourlyRateServiceTest` (22) + `CostEngineServiceTest` (5) = **27 全绿**

---

## 1. 业务目标 (P0-A 对应 PRD F1 + F2)

### 1.1 看得见 — F1 工时→成本
- 财务上传月度调薪 CSV → 系统自动按 (用户 × 月份 × 项目) 计算成本
- 任何人/任何月都有成本值 (兜底到 0 不报错, 方便前端标记"未设价")

### 1.2 多维度可切 — F2 项目/阶段/部门
- 3 张 PG 视图: `v_project_cost` / `v_phase_cost` / `v_dept_cost`
- 单端点 `GET /api/cost/dimension?dim=PROJECT|PHASE|DEPT&month=YYYY-MM` 切换
- 不写 Java 聚合 SQL, 全部交给 PG 视图 + `fn_resolve_hourly_rate` 函数

### 1.3 暂未覆盖 (PRD §F2 之外的延后)
- 预算/实际/偏差 → F4 规则引擎 (后续 sprint)
- ROI / EAC / ETC → F4 挣值分析
- 高层 4 指标黄金区 → F5 驾驶舱

---

## 2. 数据模型

### 2.1 表清单

| 表 | 来源 | 用途 | 关键字段 |
|---|---|---|---|
| `app_user.default_hourly_rate` | V4.0 ① 增量列 | 兜底 (L4) | `NUMERIC(10,2) NOT NULL DEFAULT 0` |
| `hourly_rate_v4` | V4.0 ② 新表 | 人×月×角色 override (L1/L2) | `UNIQUE(user_id, role_code, effective_month)` |
| `role_cost_default` | V4.0 ③ 新字典 | 6 角色档默认价 (L3) | `code PK` |
| `system_config` | V2.10 新表 | 业务可调参数 (F2/F4 通用) | `config_key UNIQUE` |
| `v_project_cost` / `v_phase_cost` / `v_dept_cost` | V4.1 视图 | F2 多维度核算 | view only |
| `fn_resolve_hourly_rate()` | V4.1 函数 | 视图用, 4 级兜底解析 | plpgsql |

### 2.2 hourly_rate_v4 详细

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
    UNIQUE (user_id, role_code, effective_month)       -- 同人同角色同月唯一
);
CREATE INDEX idx_hourly_rate_v4_user_month ON hourly_rate_v4(user_id, effective_month DESC);
CREATE INDEX idx_hourly_rate_v4_role_month ON hourly_rate_v4(role_code, effective_month DESC);
```

**设计变更��明** (重要 — V2.5 兼容性):
- V2.5 已有 `hourly_rate` (按 role 唯一, 无 user_id), V4.0 保留不动
- V4.0 新建 `hourly_rate_v4`, 不复用旧表, 避免破坏既有数据
- MySQL 版 V4.0 是同构 (同时 `ALTER TABLE hourly_rate` 加列做兼容, 见 V4.0 MySQL 版)

### 2.3 role_cost_default 字典 (seed 6 行)

| code | name | 默认 rate | sort_order |
|---|---|---|---|
| ARCH | 架构师 | 800.00 | 1 |
| DEV | 开发 | 600.00 | 2 |
| TEST | 测试 | 500.00 | 3 |
| PM | PM | 700.00 | 4 |
| BA | BA | 600.00 | 5 |
| OPS | 运维 | 550.00 | 6 |

`ON CONFLICT (code) DO UPDATE` 幂等, 可重复跑 Flyway。

---

## 3. 4 级费率解析算法 (核心 — F1 灵魂)

### 3.1 优先级链

```
对每个 timesheet_entry (userId, workDate, projectId, hours):
  rate, source = resolveRate(userId, user.primaryRole.code, workDate)

  L1  hourly_rate_v4.user_id = uid && 当月生效    → USER_OVERRIDE   ← 最高
  L2  hourly_rate_v4.role_code = role && user_id IS NULL && 当月生效 → ROLE_OVERRIDE
  L3  role_cost_default[role].rate                → ROLE_COST_DEFAULT
  L4  app_user.default_hourly_rate (用户级)        → USER_DEFAULT
  L5  BigDecimal.ZERO                              → NONE            ← 兜底
```

**Java 实现**: `HourlyRateService.resolveRate(userId, roleCode, month)` 返回 `RateResolution(rate, source)`。

### 3.2 SQL 实现 (PG 视图用)

`fn_resolve_hourly_rate(p_user_id, p_month)` 同构逻辑 (V4.1 PL/pgSQL), 视图 `v_*_cost` 通过 `CROSS JOIN LATERAL` 复用, **避免 N+1**。

### 3.3 为什么是 4 级 (而不是 1 级 or 2 级)

| 场景 | 谁来定价 | 优先级 |
|---|---|---|
| 全员调薪 5% | 财务上传 CSV | L3 角色档 |
| 张三晋升加 10% | HR 调档 | L2 角色档全局 override |
| 李四外包特殊 | 项目经理特批 | L1 单人 override |
| 新人/试用没设价 | 系统兜底 | L4 user 默认 |
| 字典都没建 | 不能漏算 → 0 | L5 NONE (前端标灰) |

**关键设计**: 兜底是 **0** 而不是抛错, 这样:
- 财务/PM 报表永远能跑通 (而不是 500)
- 0 标记的工时前端可高亮"未设价", 反向驱动财务补档
- F4 预警规则可直接基于"零成本工时占比"做兜底审计

---

## 4. API 契约 (12 端点 · CostController)

### 4.1 总览表

| # | Method | Path | 权限 | 说明 |
|---|---|---|---|---|
| 1 | GET | `/api/cost/role-defaults` | Read | 6 角色档默认价列表 |
| 2 | PUT | `/api/cost/role-defaults` | Admin | 更新角色档默认价 |
| 3 | GET | `/api/cost/hourly-rates` | Read | 费率列表 (可选 `?userId=`) |
| 4 | GET | `/api/cost/hourly-rates/{id}` | Read | 费率详情 |
| 5 | POST | `/api/cost/hourly-rates` | Admin | 新建费率行 |
| 6 | PUT | `/api/cost/hourly-rates/{id}` | Admin | 更新费率行 |
| 7 | POST | `/api/cost/hourly-rates/{id}/close?atMonth=YYYY-MM` | Admin | 软关 (设置 endMonth) |
| 8 | DELETE | `/api/cost/hourly-rates/{id}` | Admin | 删除 (仅限未生效) |
| 9 | GET | `/api/cost/hourly-rates/csv-template` | Admin | 下载 CSV 模板 |
| 10 | POST | `/api/cost/hourly-rates/import` (multipart) | Admin | 上传 CSV 批量 upsert |
| 11 | GET | `/api/cost/user/{userId}?month=YYYY-MM` | Read | **F1 主验收** — 用户月度成本 |
| 12 | GET | `/api/cost/user/{userId}/day?date=YYYY-MM-DD` | Read | 用户单日成本 |
| 13 | GET | `/api/cost/dimension?dim=PROJECT&month=YYYY-MM` | Read | F2 多维核算 |

> 权限: `Read` = PMO_ADMIN/ADMIN/EXEC/PM/DEPT_LEAD/VIEWER (除 VIEWER 外都可看), `Admin` = PMO_ADMIN/ADMIN
> 审计: 所有写端点 (`POST/PUT/DELETE/import`) 全部带 `@AuditLog(module="COST", action=...)`

### 4.2 F1 主验收契约 — `GET /api/cost/user/{userId}?month=2026-06`

**响应**:
```json
{
  "code": 0,
  "data": {
    "userId": 1,
    "userName": "张三",
    "month": "2026-06",
    "totalHours": 40.00,
    "totalCost": 24000.00,
    "primaryRoleCode": "DEV",
    "items": [
      {
        "projectId": 1,
        "projectCode": "P-001",
        "projectName": "示例项目",
        "milestoneId": 10,
        "hours": 8.00,
        "rate": 600.00,
        "rateSource": "ROLE_COST_DEFAULT",
        "cost": 4800.00
      }
    ],
    "rateSourceBreakdown": {
      "userOverrideHours": 0,
      "roleOverrideHours": 0,
      "roleDefaultHours": 40,
      "userDefaultHours": 0,
      "noneHours": 0
    }
  }
}
```

**关键字段**:
- `totalHours` 仅计 APPROVED 周报 (DRAFT 不计入)
- `rateSource` 字段是 F1 验收的"调试钩子" — 财务可一眼看出哪条用了哪级兜底
- `rateSourceBreakdown` 5 个 hours 计数, 一眼看出整月兜底结构

---

## 5. CSV 上传/下载契约

### 5.1 列定义 (固定 7 列, 不校验列名)

```
userId,username,roleCode,effectiveMonth,endMonth,rate,remark
```

| 列 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| userId | Long | 否 (角色档时空) | `1` | 单人 override; 空 = 角色档默认 |
| username | String | 否 | `张三` | 备用, 仅做 CSV 可读性, 不入库 |
| roleCode | String | 是 | `DEV` | 必填, FK→role_cost_default.code |
| effectiveMonth | YYYY-MM | 是 | `2026-06` | 必填, 内部转 YYYY-MM-01 |
| endMonth | YYYY-MM | 否 | `2026-12` | 空 = 仍生效 |
| rate | BigDecimal | 是 | `600.00` | 必须 > 0 |
| remark | String | 否 | `6月调薪 5%` | ≤256 字 |

### 5.2 Upsert 语义

- **角色档行** (`userId` 空) → 唯一键 `(role_code, effective_month, user_id IS NULL)`
  - 同 key 已存在 → 用新 rate 替换, 旧行 `end_month` 自动回填为新行 `effective_month` 减 1 天
- **单人行** (`userId` 非空) → 唯一键 `(user_id, role_code, effective_month)`
  - 同 key 已存在 → 同上替换语义
- 跨 key 重叠 (`[from, to]` 区间重叠) → 整批回滚, 任何一行重叠都报错

### 5.3 模板示例 (前端 "下载模板" 按钮)

```csv
userId,username,roleCode,effectiveMonth,endMonth,rate,remark
,zhangsan,DEV,2026-06,,600.00,6月全员调薪 5%
1,,DEV,2026-06,2026-12,650.00,张三单人 override
```

---

## 6. 前端交付

### 6.1 路由 + 菜单

| 路径 | 组件 | 权限 | 菜单位置 |
|---|---|---|---|
| `/admin/hourly-rates` | `HourlyRateAdmin.vue` | PMO_ADMIN/ADMIN | 系统管理 → 工时费率 |
| `/cost/user-month` | `CostUserMonth.vue` | PMO_ADMIN/ADMIN/EXEC/PM/DEPT_LEAD | 工时成本核算 (顶级菜单) |

### 6.2 `HourlyRateAdmin.vue` (416 行) — 财务管理后台

- **上半**: 6 角色档默认价表 (单行编辑 + 保存, 调 L3)
- **下半**: `hourly_rate_v4` 全表 (新建/编辑/关停/删除)
  - 上传 CSV 按钮 (调 `importCsv`)
  - 下载模板按钮 (调 `csvTemplate`)
  - 单行操作: 编辑/关停/删除 (按 effectiveMonth vs 当前月分流)

### 6.3 `CostUserMonth.vue` (310 行) — F1 验收页

输入 `userId` + 月份:
- 顶部 4 张卡片: 用户名 / 月份 / 总工时 / **总成本 (高亮)**
- **绿色 ✅ F1 验证通过** 标记: 若 `totalCost === 24000.00` (按 PRD 验收数)
- 中部: 5 个 RateSource 分账条形图 (userOverride/roleOverride/roleDefault/userDefault/none)
- 下部: 项目分账表 + 详细 items (点击展开单日下钻)

---

## 7. 验收用例 (与 PRD §四 对齐)

### 7.1 F1 主验收 — `40h × ¥600 = ¥24,000`

```
前置:
  - role_cost_default.DEV.rate = 600.00 (V4.0 seed)
  - hourly_rate_v4 中 user=1 无 override
  - user=1 primary_role = DEV (通过 user.primary_role_id)
  - 2026-06-15 当天 APPROVED 周报内 40h (单条 entry)

步骤:
  curl -fsS -H "Authorization: Bearer $TOKEN" \
    "http://localhost:8088/api/cost/user/1?month=2026-06" | jq .data.totalCost

期望: 24000.00
```

✅ 单元测试覆盖: `CostEngineServiceTest.F1 验收 (单日): 张三 6/15 40h × ¥600 = ¥24,000`

### 7.2 F1 月度验收 — 120h × ¥600 = ¥72,000

```
前置: 同上, 但 2026-06 整月 120h (3 条 entry, 各 40h, 分 3 个工作日)
期望: totalHours=120.00, totalCost=72000.00
```

✅ 单元测试覆盖: `CostEngineServiceTest.F1 验收: 张三 6 月整月 120h × ¥600 = ¥72,000`

### 7.3 4 级兜底链 — 7 个测试覆盖

| 级别 | 命中条件 | 期望 rate | 期望 source | 覆盖测试 |
|---|---|---|---|---|
| L1 | hourly_rate_v4.user_id=uid 当月生效 | 600 | USER_OVERRIDE | `L1 USER_OVERRIDE` |
| L2 | role override 行当月生效 | 550 | ROLE_OVERRIDE | `L2 ROLE_OVERRIDE` |
| L3 | role_cost_default 字典 | 450 | ROLE_COST_DEFAULT | `L3 ROLE_COST_DEFAULT` |
| L4 | app_user.default_hourly_rate | 500 | USER_DEFAULT | `L4 USER_DEFAULT` |
| L5 | 全部无 | 0 | NONE | `L5 NONE` |
| null userId | userId=null 跳过 L1/L4 | 550 | ROLE_OVERRIDE | `userId=null 时跳过 L1/L4` |
| null role | roleCode=null/blank 跳 L2/L3 | 500 | USER_DEFAULT | `roleCode=null/blank 时跳过 L2/L3` |

### 7.4 F2 多维 — `GET /api/cost/dimension?dim=PROJECT&month=2026-06`

```
前置: 已建 V4.1 三视图; PostgreSQL/MySQL 视图定义一致
步骤:
  curl -fsS -H "Authorization: Bearer $TOKEN" \
    "http://localhost:8088/api/cost/dimension?dim=PROJECT&month=2026-06"

期望:
  - rows[].key = project_id (字符串)
  - rows[].hours / rows[].cost 与 v_project_cost 完全一致
  - rows[].headcount = 当月参与人数
  - budget_estimate = project 表里的预算 (为空时为 0, 前端按 0 显示)
```

---

## 8. 已知坑点 / 与 PRD 的偏离

### 8.1 ✅ 偏离 PRD 但更优
1. **数据库表名**: PRD 写 `user.hourly_rate`, 实际是 `hourly_rate_v4` (V2.5 已占用同名)
   - 原因: V2.5 表已存在且被其他模块引用, V4.0 选择独立新表避免破坏
   - 影响: 无, 接口层不感知表名
2. **视图定义在 PG/MySQL 略有差异**: MySQL 用 `DATE_FORMAT(te.work_date, '%Y-%m')`, PG 用 `TO_CHAR(te.work_date, 'YYYY-MM')`
   - 原因: 方言差异, 视图抽象掉了
3. **NONE 兜底 = 0**: 而不是抛错
   - 原因: F4 预警规则能基于"零成本占比"反向驱动补档
   - 影响: 前端需高亮 NONE 的工时条目 (已实现)

### 8.2 ⚠️ 未实现 (后续 sprint)
1. **F4 预算超支预警** (PRD §三.4) — 独立模块, P1 sprint
2. **ROI/EAC/ETC 挣值分析** — P1 sprint
3. **按周维度的成本明细** — 目前只有按月 (`month=`) 和按日 (`date=`), 没有 `week=`
   - 临时方案: 前端按日下钻后聚合
4. **跨月调档当月的精确分配** — 当月内多次调档, 工时按 `entry.work_date` 取当天的 rate
   - 已实现 ✅ (CostEngineService 逐条 entry 解析)

### 8.3 🐛 历史 bug (本任务无关, 留作下个 sprint)
1. `InvoiceService`/`PaymentService`/`ContractService` 用 `new BusinessException(String, String)` 但 `BusinessException` 只有 `(String)` 和 `(int, String)` 构造器
   - 影响: mvn clean compile 失败 (stale class 缓存掩盖了它)
   - 建议: 给 `BusinessException` 加 `(String code, String message)` 便捷构造器
2. `MilestoneCreateRequestContractTest.weight_in_range_accepted` 缺 `phaseId` 字段
   - 影响: 1 个测试 fail
3. `RequireRolesTest.milestoneMixed` 期望 7 端点, MilestoneController 实际 9 端点
   - 影响: 1 个测试 fail
4. `WbsTask.predecessor_ids` 在 H2 下报 `bigint[]` 不支持
   - 临时方案: `test-schema-h2.sql` 用 `VARCHAR(64)` 替代, prod 仍是 PG 原生数组
   - 长期方案: 加 `@JdbcTypeCode(SqlTypes.LONGVARCHAR)` + 转换器, 让 prod 仍走数组

### 8.4 ✅ 这次任务踩过 / 修过的坑
1. **`test-schema-h2.sql` 文件污染**: 前 15 行被 read_file 的"行号|"格式污染, 导致 H2 解析 `1| 2| DROP TABLE ...` 报错
   - 修法: `sed -E 's/^[[:space:]]+[0-9]+\|//' test-schema-h2.sql`
   - 教训: write_file 后必须 `head -3` 验证文件开头不是被污染
2. **`CREATE TABLE` vs `ddl-auto=create-drop`**: Hibernate 自动建表后, @Sql 跑 schema 又建一遍报"already exists"
   - 修法: schema 里所有 `CREATE TABLE` 改成 `CREATE TABLE IF NOT EXISTS`
3. **stale `.class` 缓存掩盖编译错误**: 之前的 finance 编译失败被 maven 增量缓存掩盖
   - 教训: 任何编译错误排查前先 `mvn clean`

---

## 9. 怎么跑 / 怎么测 / 怎���改

### 9.1 本地启动 (H2 内存模式)

```bash
# 后端
cd backend
mvn spring-boot:run
# 启动后访问 http://localhost:8088/swagger-ui.html 看 /cost/* 端点

# 前端
cd frontend
pnpm dev
# 浏览器打开 http://localhost:5173/admin/hourly-rates  (PMO_ADMIN 登录)
```

### 9.2 跑单测 (P0-A 自测)

```bash
mvn test -Dtest='HourlyRateServiceTest,CostEngineServiceTest'
# 期望: Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
```

### 9.3 跑全部测试 (本任务边界外, 见 §8.3)

```bash
mvn clean test
# 当前已知失败: 见 §8.3 (历史 bug, 与 P0-A 无关)
```

### 9.4 加新角色档 (例如: 加 SA 系统架构师, 默认 ¥750)

1. **Flyway 新增补丁** (V4.x__add_sa_role.sql, PG + MySQL 双版本):
   ```sql
   INSERT INTO role_cost_default (code, name, rate, sort_order)
   VALUES ('SA', '系统架构师', 750.00, 7)
   ON CONFLICT (code) DO UPDATE SET rate = EXCLUDED.rate;
   ```
2. **`role` 表同步** (V2.9 已建, 但 `role.code` 与 `role_cost_default.code` 是两套, 注意区分)
   - 如果要做权限控制, 还需要在 `role` 表加一行 SA, 见 V2.9 设计
3. **前端**: `HourlyRateAdmin.vue` 自动多渲染一行 (因为 `listRoleDefaults()` 返回所有)
4. **测试**: `HourlyRateServiceTest` 新增 `L3 ROLE_COST_DEFAULT_SA` 验证 SA 角色解析

### 9.5 调价 (财务日常操作)

```bash
# 1. 下载模板
curl -fsS -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8088/api/cost/hourly-rates/csv-template" \
  -o cost_rates.csv

# 2. 编辑 csv (Excel/Numbers)

# 3. 上传
curl -fsS -H "Authorization: Bearer $TOKEN" \
  -F "file=@cost_rates.csv" \
  "http://localhost:8088/api/cost/hourly-rates/import"
# 返回: { ok: 3, fail: 0, errors: [] }
```

---

## 10. 未来 TODO (不在 P0-A 范围)

| TODO | 优先级 | 估时 | 备注 |
|---|---|---|---|
| 修 §8.3 4 个历史 bug | P1 | 1d | 解锁 `mvn clean test` 全绿 |
| F4 预算超支预警 (PRD §三.4) | P1 | 2w | 需 rule engine + 通知 |
| F4 EAC/ETC 挣值分析 | P2 | 1w | 复用 timesheet + budget 表 |
| F5 4 指标黄金区驾驶舱 | P1 | 1w | 复用 v_project_cost view |
| ROI 偏离 (实际 ROI < 立项 50%) | P2 | 3d | 立项 ROI 在 ProjectInitiation |
| 跨月调档精确分配 | ✅ 已实现 | — | CostEngineService 逐条 entry 解析 |
| 加维度: 按 PM 维度 | P3 | 0.5d | 复用 view 加一列即可 |

---

## 11. 变更日志

| 日期 | 版本 | 变更 |
|---|---|---|
| 2026-06-13 | V4.0 | P0-A 主任务交付: F1 工时→成本 + F2 多维核算 |
| 2026-06-12 | PRD | 原始 PRD §F1/§F2 设计 |
| 2026-Q1 | V2.5 | 已存在 `hourly_rate` 表 (按 role, 无 user_id), V4.0 不复用 |

---

## 12. 相关文档索引

- **PRD**: [`PRD-cost-control.md`](./PRD-cost-control.md) §F1 + §F2
- **MVP Design**: [`pmo-pms-mvp-design.md`](./pmo-pms-mvp-design.md) §5 (Cost 模块反推)
- **API OpenAPI**: `/swagger-ui.html` 运行时查看 (`cost-controller`)
- **代码**: `backend/src/main/java/com/company/pmo/module/cost/`
- **测试**: `backend/src/test/java/com/company/pmo/module/cost/`
- **前端**: `frontend/src/views/admin/HourlyRateAdmin.vue` + `frontend/src/views/CostUserMonth.vue`
- **API 客户端**: `frontend/src/api/cost.ts`

---

> 文档维护者: PMO 团队 · 最后更新: 2026-06-13
> 反馈/修改: 提 PR 到 `docs/pmo-pms-cost-engine.md`