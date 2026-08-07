GET /api/workload/users 兜底加固 — V2.4 视图重建

## 问题
线上 `relation "v_active_user" does not exist` 导致人员负载页 500。
V1.6__timesheet.sql 里 `CREATE OR REPLACE VIEW v_active_user` 是幂等的,
但实际环境里视图会偶发性消失(怀疑 DBeaver 整 schema 重生成 / 其他 DROP
脚本误操作),Flyway 不重跑历史版本,启动后端不复建视图。

## 修复

### 1) V2.4 兜底补丁 (Flyway 一次性扫描)
- `backend/src/main/resources/db/migration-pg/V2.4__ensure_load_views.sql`
  - `DO $$` 块探测 `pg_views`,缺则按 V1.6 同构 DDL 重建;有则跳过
  - 已有则不重定义 → 不会破坏其他依赖该视图的对象
- `backend/src/main/resources/db/migration-mysql/V2.4__ensure_load_views.sql`
  - MySQL 没有 `DO $$` 块,用 `DROP VIEW IF EXISTS + CREATE VIEW`(跟 V1.6 同风格)
  - 重建是无副作用的(v_active_user 没有依赖下游)

### 2) 启动前视图健康校验脚本
- `scripts/db-views-healthcheck.sh`
  - `psql \dv` 探测两个视图,顺带跑一次 `SELECT ... FROM v_active_user LIMIT 1`
  - 退出码 0/1/2 明确:0=健康,1=缺失或不可达,2=psql 未装
  - 用环境变量驱动连接参数,可直接接 CI

### 3) CI 集成 (`.github/workflows/ci.yml`)
- e2e job 在跑完 `docs/api-testing/smoke.sh` 后,加一步:
  1. 跑 `db-views-healthcheck.sh` 校验视图
  2. 调 `/api/workload/users` API,断言 `data.rows` 非空 + `weekCount > 0`
- 任一失败 → CI 红灯,阻断合并

## 验证

```bash
# 1) 健康校验
PGPASSWORD=project_govern_dev_2025 bash scripts/db-views-healthcheck.sh
# ✅ 视图健康: v_active_user / v_user_weekly_load 均存在
# ✅ v_active_user 可查询,WorkloadService 启动无忧

# 2) API 验证
TOKEN=$(curl -fsS -X POST http://localhost:8088/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"pmo123"}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
curl -fsS -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8088/api/workload/users?from=2026-06-01&to=2026-06-30" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print("rows=%d weekCount=%d" % (len(d["rows"]), d["weekCount"]))'
# rows=30 weekCount=5
```

## 复盘
- V1.6 用 `CREATE OR REPLACE` 看起来"安全",但 Flyway 一旦记录
  V1.6 success,后续启动不会重跑该 migration → 视图被外部 drop
  后只有靠这个 V2.4 兜底
- 真正的根因(谁/什么 drop 了视图)还需 DBA 侧追查
  (PostgreSQL `pg_stat_activity` + 审计日志定位 DROP VIEW 时点)
- 后续可考虑给视图加 ACL / 改用 `CREATE OR REPLACE` 在每次启动的
  `ApplicationRunner` 里跑一次(更主动,无需 V2.4 触发),但先以 V2.4
  + CI 兜底为最小修复
