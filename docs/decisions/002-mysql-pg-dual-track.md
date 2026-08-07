# 002 · 数据库 MySQL(生产)+ PostgreSQL(CI)双轨

- 状态:**accepted**
- 日期:2026-08-07(回溯立项于 v4.0.0 release 2026-06-13)
- 决定人:PMO + DBA + 架构组

## 背景

老 v2.x 用 H2 in-memory 跑测试,生产用 MySQL。问题:
- H2 方言与 MySQL 不一致,生产端偶发 `syntax error` 难以在测试期发现;
- MySQL 8.0 的窗口函数、CTE、JSON 字段等特性在 H2 中行为不一致,导致 EVM / 成本引擎等聚合查询在测试期不报错,生产端才暴露。

## 决定

数据库采用**双轨 + 一源**:
- **生产 / 开发**:MySQL 8.0(全功能、托管方便、运维熟悉)
- **CI / 集成测试**:PostgreSQL 16(标准 SQL 严格,语法兼容性问题更早暴露)
- **单元测试**:H2 in-memory with PostgreSQL compatibility mode(快速)

迁移脚本**一份写两套**,放在 `backend/src/main/resources/db/migration-pg/` 与 `backend/src/main/resources/db/migration-mysql/`,用 Flyway 区分环境加载。

新增 View 必须用 PostgreSQL 与 MySQL 双方言写两份 DDL,且 CI 必须跑 PG + MySQL 两套 schema migration。

## 不采用的方案

- **方案 B:只跑 PG,生产也用 PG**
  缺点:运维团队 MySQL 经验更丰富;生产托管服务 RDS-MySQL 价格更低;`v_active_user` 等视图已用 PG 写法,迁回 MySQL 工作量大。
- **方案 C:只跑 MySQL,CI 用 testcontainers 临时拉 MySQL 容器**
  缺点:CI 启动慢 30-60s;本地开发体验差;testcontainers 在 macOS Apple Silicon 上偶发网络拉镜像失败。
- **方案 D:统一用 SQLite**
  缺点:窗口函数 / CTE 支持不全,无法满足 EVM 视图需求;Flyway SQLite 适配差。

## 影响

- **迁移脚本双倍工作量**:新增一条 migration 必须 PG + MySQL 各一份,见 `backend/src/main/resources/db/migration-{pg,mysql}/V*`。
- **视图健康检查**:见 `scripts/db-views-healthcheck.sh`,CI 中 `e2e` job 跑完后必须调用此脚本,缺视图则红灯。
- **方言差异**:用 `BIGINT`/`VARCHAR`/`TIMESTAMP` 等跨方言兼容类型,避免 `ENUM`、`BOOLEAN`(MySQL 的 BOOLEAN 是 TINYINT 别名)等方言特定写法。
- **view 兜底机制**:V2.4 `__ensure_load_views.sql` 用 PG 的 `DO $$` 块与 MySQL 的 `DROP IF EXISTS + CREATE` 同构写法,见 `reviews/2026-06-09-p2-b-workload-views-fix-pmo-hex.md`。

## 验收

- ✅ Flyway PG ~40 个版本 + MySQL ~36 个版本,均成功迁移
- ✅ CI 4 jobs 中 `api-smoke` 在 PostgreSQL 上跑通 16 端点
- ✅ `db-views-healthcheck.sh` 在 CI 中绿灯
