#!/usr/bin/env bash
# seed-crm-gantt.sh
#
# 执行 docs/seeds/ 目录下的种子数据 SQL,用于演示/培训/联调。
# 当前支持:
#   - 2026-06-08-crm-gantt-seed.sql   客户CRM系统(id=3)甘特图样例
#
# 用法:
#   bash scripts/seed-crm-gantt.sh                           # 执行默认 seed(crm-gantt)
#   bash scripts/seed-crm-gantt.sh --file 2026-06-08-crm-gantt-seed.sql
#   bash scripts/seed-crm-gantt.sh --rollback                # 回滚当前 seed
#
# 环境变量(可覆盖默认值):
#   PGHOST=localhost
#   PGPORT=5432
#   PGUSER=pmo_pms
#   PGPASSWORD=***(必传,脚本内不 echo)
#   PGDATABASE=pmo_pms
#
# 退出码:
#   0 = 执行成功
#   1 = SQL 执行失败(已自动 rollback)
#   2 = 依赖工具缺失 (psql 未装)
#   3 = 参数错误 / seed 文件找不到
#   4 = 用户取消(交互式 confirm 时)

set -uo pipefail

# ---------------------------------------------------------------
# 0) 路径 & 参数
# ---------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SEED_DIR="$ROOT_DIR/docs/seeds"

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-pmo_pms}"
PGDATABASE="${PGDATABASE:-pmo_pms}"
# PGPASSWORD 由环境传入,脚本内不 echo

SEED_FILE=""
ROLLBACK=false
AUTO_YES=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --file)       SEED_FILE="$2"; shift 2 ;;
    --rollback)   ROLLBACK=true; shift ;;
    --yes|-y)     AUTO_YES=true; shift ;;
    -h|--help)
      grep -E '^#( |$)' "$0" | sed -E 's/^# ?//'
      exit 0
      ;;
    *)
      echo "❌ 未知参数: $1" >&2
      echo "   用 --help 看用法" >&2
      exit 3
      ;;
  esac
done

# 默认 seed
if [[ -z "$SEED_FILE" ]]; then
  SEED_FILE="2026-06-08-crm-gantt-seed.sql"
fi

SEED_PATH="$SEED_DIR/$SEED_FILE"
if [[ ! -f "$SEED_PATH" ]]; then
  echo "❌ 找不到 seed 文件: $SEED_PATH" >&2
  echo "   目录内容:" >&2
  ls -1 "$SEED_DIR" 2>/dev/null | sed 's/^/     /' >&2
  exit 3
fi

# ---------------------------------------------------------------
# 1) 依赖检查
# ---------------------------------------------------------------
if ! command -v psql >/dev/null 2>&1; then
  echo "❌ psql 未安装,无法执行 seed" >&2
  exit 2
fi

# ---------------------------------------------------------------
# 2) 连接预检
# ---------------------------------------------------------------
echo "🔌 预检数据库连接..."
if ! PGPASSWORD="$PGPASSWORD" psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" \
     -c "SELECT 1" -tA -q >/dev/null 2>&1; then
  echo "❌ 数据库连接失败: $PGUSER@$PGHOST:$PGPORT/$PGDATABASE" >&2
  echo "   检查 PGHOST/PGPORT/PGUSER/PGPASSWORD/PGDATABASE" >&2
  exit 1
fi
echo "   ✅ 连接正常"

# ---------------------------------------------------------------
# 3) 交互确认(除非 --yes)
# ---------------------------------------------------------------
if [[ "$ROLLBACK" == "true" ]]; then
  ACTION_DESC="回滚"
  WARN="⚠️  回滚将把数据还原到 seed 之前的状态"
else
  ACTION_DESC="执行"
  WARN="⚠️  本次操作会修改生产数据,确认已备份或非生产环境"
fi

echo ""
echo "📄 Seed 文件 : $SEED_FILE"
echo "📦 数据库   : $PGUSER@$PGHOST:$PGPORT/$PGDATABASE"
echo "🎬 动作     : $ACTION_DESC"
echo "$WARN"
echo ""

if [[ "$AUTO_YES" == "false" ]]; then
  read -r -p "确认继续? [y/N] " ans
  case "$ans" in
    [yY][eE][sS]|[yY]) ;;
    *) echo "已取消"; exit 4 ;;
  esac
fi

# ---------------------------------------------------------------
# 4) 执行
# ---------------------------------------------------------------
echo ""
echo "🚀 $ACTION_DESC 中..."

# 用 --single-transaction + ON_ERROR_STOP=1,失败自动 rollback
# 同时把 NOTICE/RAISE WARNING 也带上
if PGPASSWORD="$PGPASSWORD" psql \
     -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" \
     --single-transaction \
     --variable=ON_ERROR_STOP=1 \
     --echo-errors \
     -v VERBOSITY=verbose \
     -f "$SEED_PATH"; then
  echo ""
  echo "✅ $ACTION_DESC 成功"
  echo ""
  echo "💡 验证 SQL(可手动跑):"
  echo "   SELECT id, name, plan_start_date, plan_end_date, progress_pct FROM project WHERE id=3;"
  echo "   SELECT id, name, plan_date, actual_date, weight FROM milestone WHERE project_id=3 ORDER BY sequence;"
  exit 0
else
  rc=$?
  echo ""
  echo "❌ $ACTION_DESC 失败(已自动 rollback),退出码 $rc" >&2
  exit 1
fi
