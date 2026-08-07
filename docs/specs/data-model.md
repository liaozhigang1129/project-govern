---
status: active
created: 2026-08-07
updated: 2026-08-07
summary: 数据模型总览(ER 概览 + Flyway 迁移 + 软删除约定 + 审计基类)
---

# 数据模型(Data Model)

> 单一事实来源:数据库 ER 概览、Flyway 迁移脚本清单、软删除与审计基类约定。
> 对应来源:[`legacy/pmo-pms-mvp-design.md` §4](legacy/pmo-pms-mvp-design.md) + [`legacy/pmo-pms-cost-engine.md` §2](legacy/pmo-pms-cost-engine.md)
> 表结构详细字段见 `docs/drafts/扩展文档/A1-数据字典/`(老仓库归档)。

---

## 1. ER 概览

```
┌─────────────┐         ┌──────────────┐         ┌──────────────┐
│ department  │1───────*│  app_user    │*───────1│    role      │
│  (树形)      │         │              │         │              │
└─────────────┘         └──────┬───────┘         └──────────────┘
                               │
                               │ 1
                               │
                               │ *
                    ┌──────────▼─────────┐         ┌──────────────┐
                    │  project           │*────────1│ project_type │
                    │  (SoftDeletable)   │         └──────────────┘
                    │                    │*────────1│ project_status│
                    │                    │?────────1│ health_level │
                    └────┬───────────────┘         └──────────────┘
                         │ 1
                         │
                         │ *
                    ┌────▼─────────┐              ┌──────────────┐
                    │  milestone   │*────────────1│milestone_stat│
                    └──────────────┘              └──────────────┘

                    ┌──────────────────┐         ┌──────────────┐
                    │ project_initiation│*───────1│initiation_sta│
                    │  (立项申请)       │         └──────────────┘
                    └────┬─────────────┐
                         │ 1
                         │ *
                    ┌────▼─────────────┐         ┌──────────────┐
                    │ approval_record  │*───────1│ approval_step│
                    │  (审批流水)       │         └──────────────┘
                    └──────────────────┘
```

> M3 之后,WBS / Cost / Finance / Alert 等模块陆续增加:详见 §3 增量迁移表。

---

## 2. Flyway 迁移脚本清单(M1-M2 基线)

| 版本 | 文件 | 内容 |
|---|---|---|
| V1.0 | `V1.0__init_extensions.sql` | `pgcrypto` 扩展 + `pmo` schema + 通用 `fn_set_updated_at()` 触发器函数 |
| V1.1 | `V1.1__core_org.sql` | `department` (树形) / `role` (内置 5 种) / `app_user` (BCrypt) / `user_role` (预留多对多) |
| V1.2 | `V1.2__project_main.sql` | `project_type` / `project_status` / `health_level` 字典 + `project` 主表 |
| V1.3 | `V1.3__initiation_milestone.sql` | `initiation_status` / `approval_step` 字典 + `project_initiation` / `approval_record` / `milestone_status` / `milestone` + `operation_log` + 3 个 updated_at triggers |
| V1.4 | `V1.4__seed_data.sql` | seed 全部字典值 + 5 角色 + 4 部门 + 6 用户(密码统一 `pmo123`) |

> **MySQL 兼容**:仓库 SQL 写的是 PG 方言(`BIGSERIAL` / `TIMESTAMPTZ`)。生产 MySQL 8 由 **应用启动时 + 容器 entrypoint** 把 SQL 翻成 MySQL 方言(若不走 PG)。当前 docker-compose 默认 MySQL,**测试环境是 PG**,MySQL/PG 双轨详见 [decisions/002-mysql-pg-dual-track.md](../decisions/002-mysql-pg-dual-track.md)。

---

## 3. M3-M7 增量迁移(后续模块)

| 版本段 | 模块 | 关键表 |
|---|---|---|
| V1.5-V1.7 | 通知 + 工时 | `notification` / `timesheet_week` / `timesheet_entry` / `timesheet_approval` |
| V2.x | 工时/甘特/项目/财务/预警 | `workload` / `gantt` / `project_member` / `cost_item` / `role_rate` / `contract` / `invoice` / `payment` / `alert` / `alert_rule` |
| V3.x | 立项全流程 / 里程碑 | `initiation_*` 扩展 / `milestone_phase` / `wbs_*` |
| V4.x | 成本引擎 / 财务 3-way match / 预警 | `hourly_rate` / `cost_view` / `cost_reconciliation` / `alert` 扩展 |

详细 SQL 清单:`backend/src/main/resources/db/migration-{pg,mysql}/V*.sql`(共 50+ 个版本)。

---

## 4. 软删除统一约定

所有业务表继承 `SoftDeletableEntity`,带 `deleted BOOLEAN DEFAULT FALSE`:

- 查询:**默认**走 `findByXxxAndDeletedFalse` / `@Query("... AND m.deleted = false")`
- 删除:Service 调 `softDelete(id)`,set `deleted = true`,**不进 SQL DELETE**
- 字典表不软删(只 seed,不业务写入)
- `OperationLog` 不软删(审计永不丢)

---

## 5. 审计 / 时间戳

`AuditableEntity` 基类带 `@CreatedDate` / `@LastModifiedDate`,由 `JpaAuditingConfig` 启用:
- 生产环境:走 `@EntityListeners(AuditingEntityListener.class)`,**自动写**
- 单测 (`@DataJpaTest`):审计监听器**默认不启用**,`createdAt` 可能为 null → DashboardService 已做 null 过滤

---

## 6. 跨方言类型约定

避免 MySQL / PG 方言差异:

| 字段 | 用 | 不用 |
|---|---|---|
| 主键 | `BIGSERIAL`(PG)/`BIGINT AUTO_INCREMENT`(MySQL) | UUID / 雪花 ID |
| 时间戳 | `TIMESTAMP`(双库通用) | `TIMESTAMPTZ`(PG 专属) / `DATETIME`(MySQL 专属) |
| 布尔 | `BOOLEAN` | `TINYINT(1)`(MySQL 别名) |
| JSON | `TEXT`(应用层 Jackson 序列化) | `JSONB`(PG 专属) |
| 枚举 | `VARCHAR(32) + code` | `ENUM`(方言专属) |

---

## 7. 视图与跨域聚合

跨域查询走视图,**不进 Service 层多次单表查询**:

- `v_active_user` — 在职用户
- `v_user_weekly_load` — 用户周负载
- `v_project_cost` — 项目成本
- `v_phase_cost` — 阶段成本
- `v_dept_cost` — 部门成本

**视图健康检查**:`scripts/db-views-healthcheck.sh` + CI `e2e` job 末尾必跑。
**视图漂移兜底**:`V2.4__ensure_load_views.sql`(PG `DO $$` + MySQL `DROP IF EXISTS + CREATE`)。
