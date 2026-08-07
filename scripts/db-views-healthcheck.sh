#!/usr/bin/env bash
# db-views-healthcheck.sh
#
# 校验 P2.B 负载查询依赖的两个视图是否齐全:
#   - v_active_user
#   - v_user_weekly_load
#
# 用法:
#   bash scripts/db-views-healthcheck.sh                 # 用默认值
#   PGHOST=localhost PGPORT=5432 PGUSER=pmo_pms \
#   PGPASSWORD=pmo_pms_dev_2025 PGDATABASE=pmo_pms \
#       bash scripts/db-views-healthcheck.sh
#
# 退出码:
#   0 = 视图齐全(可继续启动后端)
#   1 = 缺失或不可访问(后端会 500,勿启)
#   2 = 依赖工具缺失 (psql 未装)

set -uo pipefail

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-pmo_pms}"
PGDATABASE="${PGDATABASE:-pmo_pms}"
# PGPASSWORD 由环境传入,脚本内不 echo

if ! command -v psql >/dev/null 2>&1; then
    echo "❌ psql 未安装,无法执行校验" >&2
    exit 2
fi

REQUIRED=(v_active_user v_user_weekly_load)

run_psql() {
    PGPASSWORD="$PGPASSWORD" psql \
        -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" \
        -t -A -c "$1" 2>/dev/null
}

# 1) 连通性
if ! run_psql "SELECT 1" >/dev/null; then
    echo "❌ 连不上数据库: $PGUSER@$PGHOST:$PGPORT/$PGDATABASE" >&2
    exit 1
fi

# 2) 视图存在性
EXISTING=$(run_psql "SELECT viewname FROM pg_views WHERE schemaname='public' AND viewname IN ('v_active_user','v_user_weekly_load')")

MISSING=()
for v in "${REQUIRED[@]}"; do
    if ! grep -qx "$v" <<<"$EXISTING"; then
        MISSING+=("$v")
    fi
done

if [ ${#MISSING[@]} -eq 0 ]; then
    echo "✅ 视图健康: v_active_user / v_user_weekly_load 均存在"
    # 顺手探一下 SQL 能跑通(列是否存在)
    if run_psql "SELECT id, username, full_name, department_id, department_name FROM v_active_user ORDER BY id LIMIT 1" >/dev/null; then
        echo "✅ v_active_user 可查询,WorkloadService 启动无忧"
        exit 0
    else
        echo "⚠️  v_active_user 存在但查询失败(可能列结构错位)" >&2
        exit 1
    fi
else
    echo "❌ 缺失视图: ${MISSING[*]}" >&2
    echo "   修复方式:让后端启动一次(应用 Flyway V2.4 兜底),或手动 psql 执行:" >&2
    echo "     CREATE OR REPLACE VIEW v_active_user AS ...;   -- 见 V1.6__timesheet.sql" >&2
    exit 1
fi
