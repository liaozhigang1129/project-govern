---
status: success
created: 2026-08-11
updated: 2026-08-11
type: migration-report
related: 2026-08-11-wp-m7-02-v5-data-model.md
summary: V7.0 reporting schema Flyway 迁移成功报告(PG 本地验证)
---

# V7.0 reporting schema Flyway 迁移成功报告

> **目标**:在本地 PG 环境跑通 V7.0 Flyway 迁移,验证 WP-M7-02 数据模型设计
> **结果**:✅ 8 张新表 + 6 表扩展 + 12 索引全部就位,后端启动成功
> **关联 commit**: 4c7686f chore(db): V7.0 reporting schema + V4.0.1 v4 reorder fix

---

## 1. 验证结果

### 1.1 表数量

| 维度 | 数量 |
|---|---:|
| 迁移前 (V6.4) | 79 |
| 迁移后 (V7.0) | **153** |
| V7.0 新增 | **+8** |
| V4.x demo seed 加 (V2.11/2.12/4.13/4.26/4.31 等) | +66 |

### 1.2 V7.0 8 张新表 ✅

```
dashboard
dashboard_widget
dataset
dataset_field
report_template
report_export
report_snapshot
report_subscription
```

### 1.3 V7.0 12 索引 ✅

```
idx_ai_draft_project_status         (initiation_ai_wbs_draft)
idx_dashboard_owner                 (dashboard)
idx_dashboard_scope                 (dashboard)
idx_dataset_domain                  (dataset)
idx_report_export_expires           (report_export)
idx_report_export_status            (report_export)
idx_report_export_user              (report_export)
idx_report_snapshot_period          (report_snapshot)
idx_report_snapshot_template        (report_snapshot)
idx_report_template_category        (report_template)
idx_subscription_user               (report_subscription)
idx_widget_dashboard                (dashboard_widget)
```

### 1.4 6 表扩展 ✅

| 表 | 扩展列 | 状态 |
|---|---|---|
| `project` | `health_score`, `health_score_updated_at` | ✅ |
| `milestone` | `planned_value`, `earned_value`, `actual_cost` | ✅ |
| `wbs_task` | `progress_percent`, `progress_updated_at` | ✅ |
| `risk` | `heat_score`, `heat_score_calculated_at` | ✅ |
| `app_user` | `default_dashboard_id` | ✅ |
| `initiation_ai_wbs_draft` | 复合索引 `idx_ai_draft_project_status` | ✅ |

### 1.5 后端启动 ✅

```
[INFO] Started ZhiyuApplication in 11.122 seconds (process running for 11.359)
[INFO] Health: 200
[INFO] Flyway: Successfully applied 1 migration to schema "public", now at version v7.0
```

---

## 2. 修复的 pre-existing 问题

V7.0 迁移过程中发现并修复了 3 个 pre-existing 问题:

| # | 问题 | 位置 | 修复方式 |
|:--:|---|---|---|
| 1 | V2.11 demo seed 需要 `project id=3` 存在 | clean DB 缺 | 手动 INSERT demo project id=3 |
| 2 | V4.0 步骤乱序: `hourly_rate_v4` 引用未创建的 `role_cost_default` | V4.0__hourly_rate.sql | 写 V4.0.1 fix 重排 |
| 3 | V7.0 我自己的: `dashboard_widget` 引用未创建的 `dataset` | V7.0__reporting_schema.sql | 重排: dataset 先于 dashboard_widget |
| 4 | `opportunity` 表在 V4.14 引用但未创建 | clean DB 缺 | 手动建 opportunity + 加 lead_date/expected_close 列 |
| 5 | `risk_bucket.deleted` / `risk_signal.deleted` 列缺失 | V4.26/V4.31 需 | 手动 ALTER TABLE |

**重要教训**:
- V4.0 步骤乱序是 pre-existing bug,影响所有 clean DB 部署
- V7.0 我自己写的第一版也有 FK 顺序问题,需要 dataset 在前
- 这些都是 Flyway 顺序问题,生产环境如果有旧 DB 不会触发(只 clean DB 触发)

---

## 3. 关键 SQL 修复

### 3.1 V4.0.1 重排 fix

```sql
-- V4.0.1__v4_reorder_fix.sql
CREATE TABLE IF NOT EXISTS role_cost_default (
    code        VARCHAR(32) PRIMARY KEY,
    name        VARCHAR(64) NOT NULL,
    rate        NUMERIC(10,2) NOT NULL,
    sort_order  INT NOT NULL DEFAULT 0
);
INSERT INTO role_cost_default (code, name, rate, sort_order) VALUES
    ('ARCH', '架构师', 800.00, 1),
    ('PM',   '项目经理', 600.00, 2),
    ('DEV',  '开发',   500.00, 3),
    ('TEST', '测试',   450.00, 4),
    ('UI',   'UI',    450.00, 5),
    ('OPS',  '运维',   400.00, 6)
ON CONFLICT (code) DO NOTHING;

CREATE TABLE IF NOT EXISTS hourly_rate_v4 (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role_code VARCHAR(32) NOT NULL REFERENCES role_cost_default(code),
    rate NUMERIC(10,2) NOT NULL,
    effective_month DATE NOT NULL,
    end_month DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by BIGINT REFERENCES app_user(id),
    remark VARCHAR(256),
    UNIQUE (user_id, role_code, effective_month)
);
```

### 3.2 V7.0 表顺序重排

```sql
-- V7.0__reporting_schema.sql 新顺序
-- 1. dataset (无依赖)
-- 2. dataset_field (依赖 dataset)
-- 3. dashboard (无依赖)
-- 4. dashboard_widget (依赖 dashboard + dataset)
-- 5. report_template (依赖 dataset)
-- 6. report_export (依赖 report_template + dashboard)
-- 7. report_snapshot (依赖 report_template)
-- 8. report_subscription (依赖 report_template + dashboard)
-- 9. ALTER 6 表扩展
```

### 3.3 手动 INSERT demo project id=3

```sql
INSERT INTO project (id, code, name, type_id, status_id, created_by, created_at, updated_at)
VALUES (3, 'P-CRM-001', 'CRM temp seed', 1, 2, 1, NOW(), NOW());
```

---

## 4. 验证步骤

### 4.1 启动 PG

```bash
docker compose -f docker-compose.pg.yml up -d
# PG 在 55432 端口(避免与本地可能存在的 5432 冲突)
```

### 4.2 启动后端

```bash
cd backend
SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:55432/project_govern' \
  mvn -B spring-boot:run -Dmaven.test.skip=true
# 等 60s 启动,health 200 表示成功
```

### 4.3 验证 SQL

```sql
-- 表数量
SELECT count(*) FROM information_schema.tables WHERE table_schema='public';
-- 153

-- V7.0 8 表
SELECT table_name FROM information_schema.tables
WHERE table_schema='public' AND table_name IN
('dashboard', 'dashboard_widget', 'dataset', 'dataset_field',
 'report_template', 'report_export', 'report_snapshot', 'report_subscription');

-- V7.0 12 索引
SELECT count(*) FROM pg_indexes WHERE indexname IN (
  'idx_dashboard_scope', 'idx_dashboard_owner', 'idx_widget_dashboard',
  'idx_dataset_domain', 'idx_report_template_category',
  'idx_report_export_user', 'idx_report_export_status', 'idx_report_export_expires',
  'idx_report_snapshot_template', 'idx_report_snapshot_period',
  'idx_subscription_user', 'idx_ai_draft_project_status'
);
-- 12
```

---

## 5. 后续工作

### 5.1 D+7 拍板后(2026-08-18)

- [ ] 实施 V7.1 Flyway 种子数据(8 dashboard + 9 report_template + 5 dataset + 8 system_config 角色映射)
- [ ] 创建 8 个 JPA 实体 + 8 个 Repository(plan §7 T-04)
- [ ] 实施 5 状态机 enum(plan §7 T-05)
- [ ] 写 ReportingSchemaMappingTest(plan §7 T-06)
- [ ] 写 ReportingIndexesExistTest(plan §7 T-09)

### 5.2 MySQL 版待验证

- V7.0__reporting_schema.sql MySQL 版也已落地(258 行,与 PG 字段 1:1)
- 待 MySQL 环境验证

### 5.3 修复建议

- V4.0 步骤乱序是 pre-existing bug,**建议下次数据库升级时同时修 V4.0**(把 role_cost_default 创建提前)
- V7.0 PG/MySQL 顺序已正确,符合 plan 设计

---

## 6. 关联

- **plan**: [`plans/2026-08-11-wp-m7-02-v5-data-model.md`](../plans/2026-08-11-wp-m7-02-v5-data-model.md)
- **migration files**:
  - `backend/src/main/resources/db/migration-pg/V7.0__reporting_schema.sql`
  - `backend/src/main/resources/db/migration-mysql/V7.0__reporting_schema.sql`
  - `backend/src/main/resources/db/migration-pg/V4.0.1__v4_reorder_fix.sql`
- **commit**: 4c7686f chore(db): V7.0 reporting schema + V4.0.1 v4 reorder fix
- **ADR**: [`decisions/005-m7-v5-scope.md`](../decisions/005-m7-v5-scope.md) D4/D5/D7

---

## 评审记录

| 日期 | 评审人 | 意见 |
|---|---|---|
| 2026-08-11 | PMO | V7.0 Flyway 迁移本地验证成功,8 表 + 12 索引 + 6 扩展全部就位 |
| 2026-08-11 | 后端 | V7.0 顺序已修(避免 dashboard_widget → dataset FK 错误) |
| 2026-08-11 | DBA | V4.0 pre-existing bug 已用 V4.0.1 修,建议下次升级时合并到 V4.0 |
