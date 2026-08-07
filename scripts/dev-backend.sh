#!/usr/bin/env bash
# dev-backend.sh — 本地启动后端(Spring Boot fat-jar)
#
# 解决问题:
#   1) 后端 jar 被 mvn package 重打后, 如果老 java 进程没杀, 8088 仍然在跑老 jar,
#      出现 "代码改了但行为没变" 的鬼故事 (典型表现: NoClassDefFoundError)。
#   2) 手动 `kill <pid>` 经常只杀 bash 父进程, java 子进程残留导致端口被占。
#
# 流程:
#   1) 解析参数 (--no-kill / --port=8088 / --skip-build)
#   2) 如有遗留 java 进程 (pid + 监听 8088) → 强杀 (SIGKILL)
#   3) 等端口空闲
#   4) (可选) mvn package 重新打包
#   5) exec java -jar 启动 (前台运行, Ctrl+C 即停)
#
# 用法:
#   ./scripts/dev-backend.sh                  # 杀旧 + 重新打包 + 前台启动
#   ./scripts/dev-backend.sh --no-kill        # 不杀旧 (并行调试用)
#   ./scripts/dev-backend.sh --skip-build     # 跳过 mvn package, 直接跑当前 jar
#   ./scripts/dev-backend.sh --port=9090      # 改端口 (本地多实例)
#
# 要求:
#   - 数据库 / Mailpit 在 Docker 跑 (pmo-mysql / pmo-mailpit 容器)
#   - backend/target/pmo-pms-backend.jar 已存在 (或传 --skip-build=false 触发 mvn package)
#
set -euo pipefail

# --- 颜色 / 输出 ---
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
step() { printf "\n${CYAN}▶ %s${NC}\n" "$*"; }
ok()   { printf "${GREEN}✅ %s${NC}\n" "$*"; }
warn() { printf "${YELLOW}⚠️  %s${NC}\n" "$*"; }
err()  { printf "${RED}❌ %s${NC}\n" "$*" >&2; }

# --- 解析参数 ---
KILL_OLD=true
SKIP_BUILD=false
SKIP_HEALTHCHECK=false
PORT="${SERVER_PORT:-8088}"
LOG_FILE="${PMO_BACKEND_LOG:-$HOME/Documents/pmo-pms/logs/backend.log}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-kill)          KILL_OLD=false; shift ;;
    --skip-build)       SKIP_BUILD=true; shift ;;
    --skip-healthcheck) SKIP_HEALTHCHECK=true; shift ;;
    --port=*)           PORT="${1#*=}"; shift ;;
    --log=*)            LOG_FILE="${1#*=}"; shift ;;
    -h|--help)
      # 只打印 3~26 行的连续块注释 (流程 + 用法 + 要求), 不打印代码 (set -euo / 颜色等)
      awk 'NR>=3 && NR<=26 && /^#/' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *) err "未知参数: $1"; exit 2 ;;
  esac
done

# --- 路径定位 ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKEND_DIR="$REPO_ROOT/backend"
JAR="$BACKEND_DIR/target/pmo-pms-backend.jar"

if [[ ! -d "$BACKEND_DIR" ]]; then
  err "找不到 backend 目录: $BACKEND_DIR"; exit 1
fi

# --- 步骤 1: 杀老进程 ---
if $KILL_OLD; then
  step "1. 清理遗留 java 进程"
  # 找所有 java -jar.*pmo-pms-backend.jar 的 pid (排除 grep / 当前 shell)
  PIDS=$(pgrep -f "java -jar.*pmo-pms-backend\.jar" || true)
  if [[ -n "$PIDS" ]]; then
    # 把 pid 自己 ($$) 和 grep 过滤掉
    FILTERED=""
    for p in $PIDS; do
      [[ "$p" == "$$" ]] && continue
      FILTERED+="$p "
    done
    if [[ -n "$FILTERED" ]]; then
      warn "发现运行中的后端 pid: $FILTERED"
      # 先 SIGTERM, 3 秒后还活着就 SIGKILL
      kill $FILTERED 2>/dev/null || true
      sleep 2
      REMAIN=$(pgrep -f "java -jar.*pmo-pms-backend\.jar" || true)
      if [[ -n "$REMAIN" ]]; then
        warn "老进程未退出, 强制 SIGKILL: $REMAIN"
        kill -9 $REMAIN 2>/dev/null || true
        sleep 1
      fi
      ok "已清理老进程"
    fi
  else
    ok "无遗留 java 进程"
  fi

  # 等端口空闲 (避免 TIME_WAIT 等)
  if command -v lsof >/dev/null 2>&1; then
    PORT_PID=$(lsof -nP -iTCP:"$PORT" -sTCP:LISTEN -t 2>/dev/null || true)
    if [[ -n "$PORT_PID" ]]; then
      err "端口 $PORT 仍被 pid $PORT_PID 占用, 请手动检查"; exit 1
    fi
  fi
fi

# --- 步骤 2: 打包 ---
if ! $SKIP_BUILD; then
  step "2. 重新打包 backend/target/pmo-pms-backend.jar"
  cd "$BACKEND_DIR"
  if ! mvn -B -q -DskipTests package; then
    err "mvn package 失败"; exit 1
  fi
  ok "打包完成"
else
  step "2. 跳过打包 (--skip-build)"
fi

if [[ ! -f "$JAR" ]]; then
  err "找不到 jar: $JAR (试试去掉 --skip-build)"; exit 1
fi
ok "jar: $(ls -lh "$JAR" | awk '{print $5, $9}')"

# --- 步骤 3: 准备日志目录 ---
LOG_DIR="$(dirname "$LOG_FILE")"
mkdir -p "$LOG_DIR"

# --- 步骤 4: 启动 ---
step "3. 启动后端 (前台运行, Ctrl+C 退出)"
printf "  日志: %s\n" "$LOG_FILE"
printf "  端口: %s\n" "$PORT"
printf "  pid:  $$\n\n"

cd "$BACKEND_DIR"
exec java -jar \
  -Dspring.profiles.active=mysql \
  -DSPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:mysql://localhost:3306/pmo_pms?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=UTF-8}" \
  -DSPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-pmo_pms}" \
  -DSPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-pmo_pms_dev_2025}" \
  -DSPRING_MAIL_HOST="${SPRING_MAIL_HOST:-localhost}" \
  -DSPRING_MAIL_PORT="${SPRING_MAIL_PORT:-1025}" \
  -DPMO_MAIL_ENABLED="${PMO_MAIL_ENABLED:-true}" \
  -DPMO_CORS_ALLOWED_ORIGINS="${PMO_CORS_ALLOWED_ORIGINS:-http://localhost:5173,http://localhost:8080}" \
  -DSERVER_PORT="$PORT" \
  "$JAR" \
  2>&1 | tee "$LOG_FILE"