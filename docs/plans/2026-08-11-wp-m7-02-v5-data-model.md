---
status: active
created: 2026-08-11
updated: 2026-08-11
summary: WP-M7-02 v5 数据模型增量 — 8 新表 + 6 表扩展 + 5 状态机 + 12 索引
---

# Plan · WP-M7-02 v5 数据模型增量

> 对应 WBS 工作包:[`WP-M7-02 v5 数据模型增量`](../WBS.md#wp-m7-02-v5-数据模型增量)
> 对应里程碑:**M7**(v5 立项:AI·移动·治理)
> 对应 ADR:[ADR-005 v5 立项范围与关键决策](../decisions/005-m7-v5-scope.md) D4/D5/D7
> 对应 spec:
> - [`reporting.md`](../specs/reporting.md) — 报表/BI/导出 业务范围
> - [`reporting-api.md`](../specs/reporting-api.md) — 报表域 API 契约
> 当前状态:**active**(2026-08-11 启动,WP-M7-01 D+7 整合会议拍板后正式执行)
> 阻塞项:WP-M7-01 D+7 整合会议未拍板(ADR-005 status=proposed → accepted)

---

## 1. 目标与范围

### 1.1 一句话

为 v5 治理轴(报表/BI/导出/数据质量)落地 **8 张新表 + 6 表扩展���段 + 5 状态机 + 12 索引**,
支撑 WP-M7-03 报表/导出后端 + WP-M7-04 可视化与 AI 看板。

### 1.2 范围内

- **8 张新表**(`reporting` 模块)
- **6 张已有表扩展字段**(非破坏性,加列 + 索引,所有列 nullable/有默认值)
- **5 个状态机枚举**(Java enum + Flyway check 注释)
- **12 个新索引**(查询路径优化,D5 决策"禁止实时跨表 JOIN"的预聚合前置)
- **1 个 Flyway 迁移脚本**(`migration-pg` + `migration-mysql` 双轨,沿用 V6.x 命名 → **V7.0**)
- **1 个 reporting 模块骨架**(`module/reporting/` package + 实体 + Repository)

### 1.3 出范围

- **WP-M7-03 报表 API 实现**:本 plan 只到数据模型,API/前端留给 WP-M7-03
- **WP-M7-04 数据质量看板**:数据模型占位预留(quality_alert_rule 表),业务实现留给 WP-M7-04
- **报表数据物化调度任务**:报表快照构建逻辑留给 WP-M7-03(Spring `@Scheduled`)
- **第三方 BI 嵌入**:D4 已拒,不做

---

## 2. 8 张新表设计

> 命名规范:`snake_case`,主键 `BIGSERIAL`/`BIGINT AUTO_INCREMENT`,所有表加 `created_at`/`updated_at`。
> 双轨 Flyway:`migration-pg` 与 `migration-mysql` 各一份,**先写 PG 版,MySQL 版字段 1:1 翻译**(类型映射见 §6)。

### 2.1 `dashboard`(仪表盘)

```sql
CREATE TABLE dashboard (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(64) UNIQUE NOT NULL,        -- 业务编码: PMO_DASH_HOME
    name            VARCHAR(128) NOT NULL,
    scope           VARCHAR(16) NOT NULL,               -- PERSONAL/ROLE/PROJECT/PROGRAM/PORTFOLIO
    scope_id        BIGINT,                             -- scope=ROLE 时为 null
    owner_id        BIGINT,                             -- 创建人 user_id
    layout          JSON,                               -- 布局: {cols, rowHeight, margin}
    filters         JSON,                               -- 默认过滤条件
    refresh_interval_sec INT NOT NULL DEFAULT 300,
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,     -- 角色默认仪表盘标记
    is_shared       BOOLEAN NOT NULL DEFAULT FALSE,
    share_url       VARCHAR(128),                       -- 分享 token
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',  -- DRAFT/PUBLISHED/ARCHIVED
    description     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_dashboard_scope ON dashboard(scope, scope_id);
CREATE INDEX idx_dashboard_owner ON dashboard(owner_id);
CREATE INDEX idx_dashboard_status ON dashboard(status);
```

### 2.2 `dashboard_widget`(Widget)

```sql
CREATE TABLE dashboard_widget (
    id              BIGSERIAL PRIMARY KEY,
    dashboard_id    BIGINT NOT NULL REFERENCES dashboard(id) ON DELETE CASCADE,
    widget_type     VARCHAR(16) NOT NULL,               -- KPI_CARD/CHART/TABLE/GANTT/HEATMAP/LIST/IFRAME
    chart_type      VARCHAR(16),                        -- LINE/BAR/PIE/SCATTER/FUNNEL/SANKEY/GAUGE/STACKED
    title           VARCHAR(128) NOT NULL,
    dataset_id      BIGINT REFERENCES dataset(id) ON DELETE SET NULL,
    query           JSON,                               -- 自定义 query override
    config          JSON,                               -- 图表配置(颜色/坐标轴/单位)
    position        JSON NOT NULL,                      -- {x, y, w, h}
    sort_order      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_widget_dashboard ON dashboard_widget(dashboard_id);
CREATE INDEX idx_widget_dataset ON dashboard_widget(dataset_id);
```

### 2.3 `dataset`(数据集 / 指标语义层)

```sql
CREATE TABLE dataset (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(64) UNIQUE NOT NULL,        -- 业务编码: DS_PROJECT_PROFIT
    name            VARCHAR(128) NOT NULL,
    domain          VARCHAR(32) NOT NULL,               -- PROJECT/MILESTONE/RESOURCE/RISK/FINANCE/TIMESHEET
    source_table    VARCHAR(64),                        -- 物理表名(用于权限校验)
    sql_template    TEXT,                               -- 预聚合 SQL 模板(D5:禁止实时跨表 JOIN)
    refresh_policy  VARCHAR(16) NOT NULL DEFAULT 'MANUAL',  -- MANUAL/HOURLY/DAILY/WEEKLY
    last_refresh_at TIMESTAMPTZ,
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',  -- DRAFT/PUBLISHED/DEPRECATED
    description     TEXT,
    created_by      BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_dataset_domain ON dataset(domain);
CREATE INDEX idx_dataset_status ON dataset(status);
```

### 2.4 `dataset_field`(数据集字段 / 维度 + 度量)

```sql
CREATE TABLE dataset_field (
    id              BIGSERIAL PRIMARY KEY,
    dataset_id      BIGINT NOT NULL REFERENCES dataset(id) ON DELETE CASCADE,
    field_name      VARCHAR(64) NOT NULL,
    display_name    VARCHAR(128) NOT NULL,
    field_type      VARCHAR(16) NOT NULL,               -- DIMENSION/MEASURE/CALCULATED
    data_type       VARCHAR(16) NOT NULL,               -- STRING/DECIMAL/INT/DATE/DATETIME/BOOLEAN
    agg_func        VARCHAR(16),                        -- SUM/AVG/COUNT/MIN/MAX (MEASURE 必填)
    formula         TEXT,                               -- CALCULATED 时填公式
    dim_role        VARCHAR(16),                        -- DIMENSION 时: ROW/COLUMN/FILTER
    sort_order      INT NOT NULL DEFAULT 0,
    UNIQUE (dataset_id, field_name)
);
CREATE INDEX idx_dataset_field_dataset ON dataset_field(dataset_id);
```

### 2.5 `report_template`(报表模板)

```sql
CREATE TABLE report_template (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(64) UNIQUE NOT NULL,        -- RPT_STATUS_WEEKLY / RPT_EVM / RPT_RISK_HEATMAP
    category        VARCHAR(32) NOT NULL,               -- STATUS/EVM/RESOURCE/RISK/QUALITY/BUDGET/PORTFOLIO/PERFORMANCE/AUDIT
    name            VARCHAR(128) NOT NULL,
    dataset_id      BIGINT REFERENCES dataset(id) ON DELETE SET NULL,
    format          VARCHAR(16) NOT NULL DEFAULT 'TABLE',  -- TABLE/CHART/MIXED
    default_filters JSON,
    layout          JSON,                               -- 报表布局(与 dashboard 不同,此处为打印/导出布局)
    schedule_cron   VARCHAR(32),                         -- 订阅 cron 表达式(可选)
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    description     TEXT,
    created_by      BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_report_template_category ON report_template(category);
CREATE INDEX idx_report_template_status ON report_template(status);
```

### 2.6 `report_export`(导出任务,D4 决策)

```sql
CREATE TABLE report_export (
    id              BIGSERIAL PRIMARY KEY,
    task_id         VARCHAR(64) UNIQUE NOT NULL,        -- UUID,前端轮询/SSE 用
    template_id     BIGINT REFERENCES report_template(id) ON DELETE SET NULL,
    dashboard_id    BIGINT REFERENCES dashboard(id) ON DELETE SET NULL,
    user_id         BIGINT NOT NULL,
    format          VARCHAR(8) NOT NULL,                -- PDF/EXCEL/CSV/PNG
    params          JSON,                               -- 查询参数
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',  -- PENDING/RUNNING/SUCCESS/FAILED
    progress        INT NOT NULL DEFAULT 0,             -- 0-100
    file_path       TEXT,                               -- 对象存储 key(MinIO/OSS)
    file_size       BIGINT,
    error_message   TEXT,
    expires_at      TIMESTAMPTZ,                        -- D7:TTL 24h
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_report_export_user ON report_export(user_id);
CREATE INDEX idx_report_export_status ON report_export(status);
CREATE INDEX idx_report_export_expires ON report_export(expires_at);
```

### 2.7 `report_snapshot`(报表物化快照,D5 决策)

```sql
CREATE TABLE report_snapshot (
    id              BIGSERIAL PRIMARY KEY,
    template_id     BIGINT NOT NULL REFERENCES report_template(id) ON DELETE CASCADE,
    period          VARCHAR(16) NOT NULL,               -- 周期标识: 2026-W32 / 2026-08 / 2026-Q3
    data            JSON NOT NULL,                      -- 物化结果(预聚合后)
    row_count       INT NOT NULL,
    file_size       BIGINT,
    status          VARCHAR(16) NOT NULL DEFAULT 'BUILDING',  -- BUILDING/READY/STALE
    built_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    stale_at        TIMESTAMPTZ,
    UNIQUE (template_id, period)
);
CREATE INDEX idx_report_snapshot_template ON report_snapshot(template_id);
CREATE INDEX idx_report_snapshot_status ON report_snapshot(status);
CREATE INDEX idx_report_snapshot_period ON report_snapshot(period);
```

### 2.8 `report_subscription`(订阅分发,D6 决策)

```sql
CREATE TABLE report_subscription (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(64) UNIQUE NOT NULL,
    user_id         BIGINT NOT NULL,
    template_id     BIGINT REFERENCES report_template(id) ON DELETE SET NULL,
    dashboard_id    BIGINT REFERENCES dashboard(id) ON DELETE SET NULL,
    channel_set     VARCHAR(64) NOT NULL,               -- 通道组合: "EMAIL,IM,LINK"
    cron            VARCHAR(32) NOT NULL,               -- cron 表达式
    recipients      JSON,                               -- 额外接收人(订阅者之外)
    params          JSON,                               -- 报表参数
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE/PAUSED/CANCELLED
    last_run_at     TIMESTAMPTZ,
    next_run_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_subscription_user ON report_subscription(user_id);
CREATE INDEX idx_subscription_status ON report_subscription(status);
CREATE INDEX idx_subscription_next_run ON report_subscription(next_run_at);
```

### 2.9 角色 → 默认仪表盘映射(借力 `system_config`)

D8 决策要求"8 角色 × 默认 dashboard 可配置"。
实现策略:**不新建表**,借力已有 `system_config`(`config_group='REPORTING'`),
config_key = `role_dashboard.PMO_ADMIN`(角色编码作为后缀),
config_value = dashboard.code。

> 例:`system_config.config_key='role_dashboard.PMO_ADMIN'`, `config_value='DASH_PMO_HOME'`。
> 这是 Out of Scope(API 增删改)的最小实现,WP-M7-03 可扩展为独立 `role_dashboard` 表。

---

## 3. 6 张已有表扩展

> 原则:**只加列 + 索引,不动现有列;所有新列 nullable 或有默认值**。

### 3.1 `project` — 健康度评分(D8 数据质量看板前置)

```sql
-- PG 版
ALTER TABLE project ADD COLUMN IF NOT EXISTS health_score        INT;          -- 0-100
ALTER TABLE project ADD COLUMN IF NOT EXISTS health_score_updated_at TIMESTAMPTZ;
-- MySQL 版
ALTER TABLE project ADD COLUMN health_score        INT NULL;
ALTER TABLE project ADD COLUMN health_score_updated_at TIMESTAMP NULL;
```

### 3.2 `milestone` — 挣值(EVM)字段

```sql
-- PG
ALTER TABLE milestone ADD COLUMN IF NOT EXISTS planned_value     DECIMAL(18,2);  -- PV
ALTER TABLE milestone ADD COLUMN IF NOT EXISTS earned_value      DECIMAL(18,2);  -- EV
ALTER TABLE milestone ADD COLUMN IF NOT EXISTS actual_cost       DECIMAL(18,2);  -- AC
-- MySQL
ALTER TABLE milestone ADD COLUMN planned_value     DECIMAL(18,2) NULL;
ALTER TABLE milestone ADD COLUMN earned_value      DECIMAL(18,2) NULL;
ALTER TABLE milestone ADD COLUMN actual_cost       DECIMAL(18,2) NULL;
```

### 3.3 `wbs_task` — 进度百分比

```sql
-- PG
ALTER TABLE wbs_task ADD COLUMN IF NOT EXISTS progress_percent NUMERIC(5,2);  -- 0.00-100.00
ALTER TABLE wbs_task ADD COLUMN IF NOT EXISTS progress_updated_at TIMESTAMPTZ;
-- MySQL
ALTER TABLE wbs_task ADD COLUMN progress_percent DECIMAL(5,2) NULL;
ALTER TABLE wbs_task ADD COLUMN progress_updated_at TIMESTAMP NULL;
```

### 3.4 `risk` — 风险热力分数(D5 预聚合)

```sql
-- PG
ALTER TABLE risk ADD COLUMN IF NOT EXISTS heat_score INT;          -- P*I
ALTER TABLE risk ADD COLUMN IF NOT EXISTS heat_score_calculated_at TIMESTAMPTZ;
-- MySQL
ALTER TABLE risk ADD COLUMN heat_score INT NULL;
ALTER TABLE risk ADD COLUMN heat_score_calculated_at TIMESTAMP NULL;
```

### 3.5 `app_user` — 默认仪表盘

```sql
-- PG
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS default_dashboard_id BIGINT REFERENCES dashboard(id) ON DELETE SET NULL;
-- MySQL
ALTER TABLE app_user ADD COLUMN default_dashboard_id BIGINT NULL;
ALTER TABLE app_user ADD CONSTRAINT fk_user_default_dashboard FOREIGN KEY (default_dashboard_id) REFERENCES dashboard(id) ON DELETE SET NULL;
```

### 3.6 `initiation_ai_wbs_draft` — 报表引用

> AI 轴产出(wbs draft)被报表域引用,**不**扩 schema,但加索引 `idx_ai_draft_project_status` 以加速报表聚合。

```sql
-- PG
CREATE INDEX IF NOT EXISTS idx_ai_draft_project_status
    ON initiation_ai_wbs_draft(project_id, status);
-- MySQL (idx 幂等保护)
DROP PROCEDURE IF EXISTS create_idx_ai_draft_project_status;
DELIMITER //
CREATE PROCEDURE create_idx_ai_draft_project_status()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'initiation_ai_wbs_draft'
                     AND INDEX_NAME = 'idx_ai_draft_project_status') THEN
        CREATE INDEX idx_ai_draft_project_status ON initiation_ai_wbs_draft(project_id, status);
    END IF;
END //
DELIMITER ;
CALL create_idx_ai_draft_project_status();
DROP PROCEDURE create_idx_ai_draft_project_status;
```

---

## 4. 5 个状态机枚举(Java)

> 包路径:`com.hex.projectgovern.module.reporting.enums.*`
> 命名:`@Enumerated(EnumType.STRING)` 持久化为字符串(��于跨 DB 一致)。

```java
// DashboardStatus.java
public enum DashboardStatus { DRAFT, PUBLISHED, ARCHIVED }

// DatasetStatus.java
public enum DatasetStatus { DRAFT, PUBLISHED, DEPRECATED }

// ReportExportStatus.java
public enum ReportExportStatus { PENDING, RUNNING, SUCCESS, FAILED }

// ReportSnapshotStatus.java
public enum ReportSnapshotStatus { BUILDING, READY, STALE }

// SubscriptionStatus.java
public enum SubscriptionStatus { ACTIVE, PAUSED, CANCELLED }
```

> 每个 enum 文件加 `@JsonValue` + `@JsonCreator` 兼容 Jackson 反序列化。

---

## 5. 12 个索引清单(D5 性能门禁)

| # | 表 | 索引名 | 列 | 用途 |
|:--:|---|---|---|---|
| 01 | dashboard | idx_dashboard_scope | (scope, scope_id) | 角色/项目范围查询 |
| 02 | dashboard | idx_dashboard_owner | (owner_id) | 个人仪表盘列表 |
| 03 | dashboard_widget | idx_widget_dashboard | (dashboard_id) | 仪表盘 widget 加载 |
| 04 | dataset | idx_dataset_domain | (domain) | 按业务域筛选 |
| 05 | report_template | idx_report_template_category | (category) | 9 类报表筛选 |
| 06 | report_export | idx_report_export_user | (user_id) | 用户导出历史 |
| 07 | report_export | idx_report_export_status | (status) | 异步任务状态轮询 |
| 08 | report_export | idx_report_export_expires | (expires_at) | D7 TTL 清理 |
| 09 | report_snapshot | idx_report_snapshot_template | (template_id) | 模板物化查询 |
| 10 | report_snapshot | idx_report_snapshot_period | (period) | 周期快照 |
| 11 | report_subscription | idx_subscription_user | (user_id) | 用户订阅列表 |
| 12 | report_subscription | idx_subscription_next_run | (next_run_at) | 调度任务扫描 |

> **索引策略**:D5 决策"禁止实时跨表 JOIN",所有报表查询走 `report_snapshot`(预聚合);
> 上述索引全部服务于 **预聚合写路径** + **物化读路径**,不服务于实时跨表。

---

## 6. 双轨 Flyway 迁移

| 版本 | 文件 | 内容 |
|:--:|---|---|
| V7.0 | `migration-pg/V7.0__reporting_schema.sql` | §2 八张新表 + §3 六表扩展 + §5 索引(全量 PG 语法,`IF NOT EXISTS` 幂等) |
| V7.0 | `migration-mysql/V7.0__reporting_schema.sql` | §2 八张新表(MySQL 翻译版,`BIGINT AUTO_INCREMENT`/`JSON`/`TIMESTAMP`)+ §3 六表扩展(无 `IF NOT EXISTS`,用 `INFORMATION_SCHEMA` 守卫) + §5 索引(用 `DROP PROCEDURE IF EXISTS` + `CALL` 模式幂等,沿用 V6.x 模式) |

### 6.1 类型映射表(MySQL ↔ PG)

| PG | MySQL | 说明 |
|---|---|---|
| `BIGSERIAL PRIMARY KEY` | `BIGINT AUTO_INCREMENT PRIMARY KEY` | 自增主键 |
| `TIMESTAMPTZ` | `TIMESTAMP` | 时间戳(MySQL 不支持 TZ,业务层处理) |
| `JSON` | `JSON` | 通用 |
| `BOOLEAN` | `TINYINT(1)` 或 `BOOLEAN` | MySQL 接受 `BOOLEAN` 别名 |
| `VARCHAR(N)` | `VARCHAR(N)` | 通用 |
| `TEXT` | `TEXT` | 通用 |
| `DECIMAL(18,2)` | `DECIMAL(18,2)` | 通用 |
| `NUMERIC(5,2)` | `DECIMAL(5,2)` | 通用 |
| `IF NOT EXISTS` (CREATE/ALTER) | `INFORMATION_SCHEMA` 守卫 + 存储过程 | MySQL 8.0 才支持 `IF NOT EXISTS`,双轨分别处理 |

### 6.2 V7.0 种子数据(seed)

| 表 | 种子内容 | 数量 |
|---|---|---|
| dashboard | 8 角色默认仪表盘 (`DASH_PMO_DIRECTOR_HOME` ... `DASH_TASK_USER_HOME`) | 8 |
| report_template | 9 类报表模板 (RPT_STATUS_WEEKLY / RPT_EVM / RPT_RISK_HEATMAP / RPT_BUDGET_VS_ACTUAL / RPT_RESOURCE_UTILIZATION / RPT_QUALITY_DEFECT / RPT_PORTFOLIO / RPT_PERFORMANCE / RPT_AUDIT_LOG) | 9 |
| dataset | 5 核心数据集 (DS_PROJECT_PROFIT / DS_RESOURCE_UTIL / DS_RISK_MATRIX / DS_MILESTONE_EVM / DS_BUDGET_VS_ACTUAL) | 5 |
| system_config | 8 角色 → 默认 dashboard 映射 (config_group='REPORTING', config_key='role_dashboard.<ROLE>') | 8 |

> **种子数据 SQL 文件**:`migration-pg/V7.1__reporting_seed.sql` + `migration-mysql/V7.1__reporting_seed.sql`(INSERT IGNORE / ON CONFLICT 幂等,沿用 V1.4 模式)

---

## 7. 实现步骤

> 顺序执行,每步独立 commit。

### T-01 V7.0 PG 版 Flyway 迁移

- 文件:`backend/src/main/resources/db/migration-pg/V7.0__reporting_schema.sql`
- 内容:§2 八张新表 + §3 六表扩展 + §5 索引(PG 版)
- 验证:`make docs-lint` + H2/PG 启动正常

### T-02 V7.0 MySQL 版 Flyway 迁移

- 文件:`backend/src/main/resources/db/migration-mysql/V7.0__reporting_schema.sql`
- 内容:§2-§5 MySQL 翻译版(`INFORMATION_SCHEMA` 守卫 + 存储过程)
- 验证:`make docs-lint` + 与 PG 字段 1:1 比对(详见 §6 类型映射)

### T-03 V7.1 种子数据(PG + MySQL 双轨)

- 文件:`migration-pg/V7.1__reporting_seed.sql` + `migration-mysql/V7.1__reporting_seed.sql`
- 内容:§6.2 种子数据
- 验证:`mvn -B test` 跑现有 `InitiationAiWbsDraftTest`(已用 `if (count == 0)` 守卫,不会因新增表失败)

### T-04 Java 实体 + Repository

- 包:`com.hex.projectgovern.module.reporting.entity.*`(8 个 `@Entity`)
- 包:`com.hex.projectgovern.module.reporting.repository.*`(8 个 `JpaRepository`)
- 命名:`Dashboard` / `DashboardWidget` / `Dataset` / `DatasetField` /
       `ReportTemplate` / `ReportExport` / `ReportSnapshot` / `ReportSubscription`
- 验证:`mvn -B compile`

### T-05 5 个状态机枚举

- 包:`com.hex.projectgovern.module.reporting.enums.*`(5 个 enum)
- 内容:§4
- 验证:`mvn -B compile` + 现有 `approval` 模块 enum 模式一致

### T-06 实体 ↔ 表 映射测试

- 新增:`ReportingSchemaMappingTest`(`@SpringBootTest` + `@AutoConfigureTestDatabase`)
- 覆盖:8 个新表 + 6 张扩展表的列存在性检查(用 `JdbcTemplate.metaData()`)
- 验证:`mvn -B test -Dtest=ReportingSchemaMappingTest -Djacoco.skip=true` 全绿

### T-07 迁移幂等性测试

- 跑两次 V7.0 迁移,验证 `IF NOT EXISTS` + `INFORMATION_SCHEMA` 守卫不报错
- 命令:`mvn -B flyway:clean flyway:migrate -Dflyway.locations=...` × 2
- 验证:`BUILD SUCCESS` × 2

### T-08 状态机单元测试

- 新增:`EnumsTest`(5 个 enum 的 `valueOf` + `JsonValue`/`JsonCreator` 序列化往返测试)
- 验证:`mvn -B test -Dtest=EnumsTest -Djacoco.skip=true` 全绿

### T-09 索引存在性测试

- 新增:`ReportingIndexesExistTest`(`@SpringBootTest`,查询 `pg_indexes` / `INFORMATION_SCHEMA.STATISTICS`)
- 覆盖:§5 12 个索引全部存在
- 验证:`mvn -B test -Dtest=ReportingIndexesExistTest -Djacoco.skip=true` 全绿

### T-10 文档同步

- `docs/WBS.md`:WP-M7-02 状态 ⏸ draft → 🟡 active →(执行完后)✅ done
- `docs/STATUS.md`:M7-02 entry + last_head 同步
- `docs/CHANGELOG.md`:M7-02 entry
- `docs/decisions/README.md`:新增 005 反链(已存在,补 ADR 列表位置)

---

## 8. 验收标准(DoD)

### 8.1 数据模型

- [ ] V7.0 PG 版 Flyway 迁移文件落地
- [ ] V7.0 MySQL 版 Flyway 迁移文件落地(与 PG 字段 1:1)
- [ ] V7.1 PG + MySQL 种子数据文件落地(8 dashboard + 9 template + 5 dataset + 8 system_config)
- [ ] 8 张新表 + 6 表扩展 + 12 索引全部存在
- [ ] 5 个状态机 enum 文件落地

### 8.2 代码

- [ ] 8 个 JPA 实体 + 8 个 Repository 类落地
- [ ] `ReportingSchemaMappingTest` 全过(8 新表 + 6 扩展列存在性)
- [ ] `ReportingIndexesExistTest` 全过(12 索引存在性)
- [ ] `EnumsTest` 全过(5 enum 序列化往返)
- [ ] 迁移幂等性测试通过(连续 migrate 2 次无报错)

### 8.3 文档

- [ ] `WBS.md` WP-M7-02 状态更新为 ✅ done
- [ ] `STATUS.md` last_head 同步到 WP-M7-02 commit hash + M7-02 entry
- [ ] `CHANGELOG.md` 添加 M7-02 entry
- [ ] `docs/decisions/README.md` 005 位置补齐

### 8.4 门禁

- [ ] `make docs-lint` 全绿
- [ ] `mvn -B compile` 成功
- [ ] `mvn -B test -Djacoco.skip=true` 现有测试不破坏(`InitiationAiWbsDraftTest` 等)
- [ ] `mvn -B test -Djacoco.skip=true` 新增 3 个测试类全过

---

## 9. 风险登记

| # | 风险 | 概率 | 影响 | 缓解 |
|:--:|---|:--:|:--:|---|
| R-M7-02-01 | MySQL/PG 字段类型不一致导致生产事故 | 中 | 高 | §6 类型映射表 + T-02 `INFORMATION_SCHEMA` 守卫 + 字段 1:1 diff review |
| R-M7-02-02 | 现有测试因新表被破坏(例如外键引用) | 中 | 中 | T-04 用 `@Entity` 默认 lazy,不主动查;扩展列 nullable |
| R-M7-02-03 | 种子数据与 PMO_ADMIN 配置的 dashboard 命名不一致 | 低 | 中 | §6.2 种子命名固定,文档化于 `system_config` README |
| R-M7-02-04 | D5 决策"禁止实时跨表 JOIN"被违反(报表 SQL 写错) | 中 | 高 | §5 索引策略只服务于预聚合路径;`ReportingIndexesExistTest` 验证读路径 |

---

## 10. 关联

- WBS:[`WP-M7-02 v5 数据模型增量`](../WBS.md#wp-m7-02-v5-数据模型增量)(本 plan 落地)
- Spec:
  - [`reporting.md`](../specs/reporting.md) — 业务范围
  - [`reporting-api.md`](../specs/reporting-api.md) — API 契约(DTO 字段对应 §2 表 schema)
- Plan:
  - [`WP-M7-01 v5 立项评审`](../plans/2026-08-07-wp-m7-01-v5-scope-freeze.md)(前置依赖)
  - `WP-M7-03 v5 核心功能(报表后端 + 导出服务)`: 计划文件待定 (后续启动)
  - `WP-M7-04 v5 可视化与 AI 看板`: 计划文件待定 (后续启动)
- ADR:[ADR-005 v5 立项范围与关键决策](../decisions/005-m7-v5-scope.md) D4(导出)/D5(数据集)/D7(安全)
- 关联 ADR:[ADR 004 · IM 平台回调接入推迟到 v5](../decisions/004-im-callback-deferred.md)(D6 IM 通道依赖)

---

## 评审记录

| 日期 | 评审人 | 意见 |
|---|---|---|
| 2026-08-11 | PMO | 通过 plan,等 WP-M7-01 D+7 整合会议拍板后正式启动 |
| 2026-08-11 | 架构师 | 通过 §6 双轨迁移策略,建议 T-07 幂等测试覆盖 2 种 DB |
| 2026-08-11 | DBA | 通过 §2/§3 schema 设计,建议 §5 索引先单列后复合 |
| 2026-08-11 | 后端 | 通过 §7 实现步骤,建议 §6.2 种子数据用 SQL 而非 Java(便于运营维护) |
| ⏳ D+7 | Sponsor | 待整合会议拍板 → 启动 |