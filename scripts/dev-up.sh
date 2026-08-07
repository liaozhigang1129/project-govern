#!/usr/bin/env bash
# dev-up.sh — 一条龙起 backend + frontend,Ctrl+C 两个一起干净退出
#
# 思路:
#   - backend 复用 dev-backend.sh (它已经做了"杀旧进程 + 打包 + 前台启动"全套)
#   - frontend 直接 exec pnpm dev (前台 + 颜色输出)
#   - 把这两个进程放到同一个进程组,父脚本捕获 SIGINT/SIGTERM 后
#     用 kill -PID -<signal> 把信号广播到整个进程组,
#     backend 的 java 子进程和 frontend 的 vite/node 子进程都会收到,
#     不会出现"Ctrl+C 只杀了 bash, java/node 残留"的问题
#   - 父进程最后 wait,任意一个先退出就杀掉另一个并退出
#
# 用法:
#   ./scripts/dev-up.sh                       # 默认 backend 8088 + frontend 5173
#   ./scripts/dev-up.sh --no-kill             # 不杀旧 backend 进程
#   ./scripts/dev-up.sh --skip-build          # backend 不重新打包
#   ./scripts/dev-up.sh --skip-frontend       # 只起 backend
#   ./scripts/dev-up.sh --skip-backend        # 只起 frontend
#   ./scripts/dev-up.sh --port=9090           # backend 改端口
#   ./scripts/dev-up.sh --log=/tmp/be.log     # backend 日志改路径
#
# 环境变量透传给 dev-backend.sh:
#   SERVER_PORT / SPRING_DATASOURCE_* / SPRING_MAIL_* / PMO_* 等
#
# 要求:
#   - pnpm / java / mvn 都在 PATH
#   - 数据库 / Mailpit 在 Docker 跑 (zhiyu-mysql / zhiyu-mailpit 容器)
#
set -euo pipefail

# --- 颜色 / 输出 ---
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
step() { printf "\n${CYAN}▶ %s${NC}\n" "$*"; }
ok()   { printf "${GREEN}✅ %s${NC}\n" "$*"; }
warn() { printf "${YELLOW}⚠️  %s${NC}\n" "$*"; }
err()  { printf "${RED}❌ %s${NC}\n" "$*" >&2; }

# --- 路径定位 ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKEND_DIR="$REPO_ROOT/backend"
FRONTEND_DIR="$REPO_ROOT/frontend"

if [[ ! -d "$BACKEND_DIR" ]]; then err "找不到 backend: $BACKEND_DIR"; exit 1; fi
if [[ ! -d "$FRONTEND_DIR" ]]; then err "找不到 frontend: $FRONTEND_DIR"; exit 1; fi

# --- 解析参数 ---
SKIP_FRONTEND=false
SKIP_BACKEND=false
BACKEND_ARGS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-frontend) SKIP_FRONTEND=true; shift ;;
    --skip-backend)  SKIP_BACKEND=true;  shift ;;
    -h|--help)
      awk 'NR>=3 && NR<=27 && /^#/' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    --no-kill|--skip-build|--skip-healthcheck) BACKEND_ARGS+=("$1"); shift ;;
    --port=*|--log=*)                            BACKEND_ARGS+=("$1"); shift ;;
    *)
      err "未知参数: $1 (用 --help 看用法)"; exit 2 ;;
  esac
done

$SKIP_FRONTEND && $SKIP_BACKEND && { err "--skip-frontend 和 --skip-backend 不能同时给"; exit 2; }

# --- 全局状态 (用于 cleanup) ---
BACKEND_PID=""
FRONTEND_PID=""
SHUTTING_DOWN=false

cleanup() {
  # 防止 SIGINT 信号 handler 互相触发
  $SHUTTING_DOWN && return
  SHUTTING_DOWN=true

  printf "\n"
  warn "收到退出信号, 正在关闭 backend + frontend ..."

  # 走"自己发的信号"路径: 用负号 PID 把信号发给整个进程组
  # setpgid 之后 backend/frontend 各自在一个进程组里,
  # 父脚本 set -m 默认就在自己的进程组里, 所以直接 kill 两个子 PID 即可
  if [[ -n "$BACKEND_PID" ]] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    step "停 backend (pid=$BACKEND_PID)"
    kill -TERM "-$BACKEND_PID" 2>/dev/null || kill -TERM "$BACKEND_PID" 2>/dev/null || true
  fi
  if [[ -n "$FRONTEND_PID" ]] && kill -0 "$FRONTEND_PID" 2>/dev/null; then
    step "停 frontend (pid=$FRONTEND_PID)"
    kill -TERM "-$FRONTEND_PID" 2>/dev/null || kill -TERM "$FRONTEND_PID" 2>/dev/null || true
  fi

  # 给 5 秒优雅退出, 之后强杀
  local waited=0
  while [[ $waited -lt 5 ]]; do
    local alive=0
    [[ -n "$BACKEND_PID"  ]] && kill -0 "$BACKEND_PID"  2>/dev/null && alive=1
    [[ -n "$FRONTEND_PID" ]] && kill -0 "$FRONTEND_PID" 2>/dev/null && alive=1
    [[ $alive -eq 0 ]] && break
    sleep 1
    waited=$((waited+1))
  done

  if [[ -n "$BACKEND_PID"  ]] && kill -0 "$BACKEND_PID"  2>/dev/null; then
    warn "backend 未响应 SIGTERM, SIGKILL"
    kill -KILL "-$BACKEND_PID"  2>/dev/null || kill -KILL "$BACKEND_PID"  2>/dev/null || true
  fi
  if [[ -n "$FRONTEND_PID" ]] && kill -0 "$FRONTEND_PID" 2>/dev/null; then
    warn "frontend 未响应 SIGTERM, SIGKILL"
    kill -KILL "-$FRONTEND_PID" 2>/dev/null || kill -KILL "$FRONTEND_PID" 2>/dev/null || true
  fi

  # 回收僵尸
  wait 2>/dev/null || true
  ok "已退出"
  exit 0
}

# Ctrl+C / kill / 父进程退出 都触发清理
trap cleanup INT TERM EXIT

# --- 起 backend (后台) ---
start_backend() {
  step "▶ 启动 backend (后台)"
  # dev-backend.sh 最后是 exec java -jar, 我们让它跑在新的进程组里:
  # setpgid 之后 java / node 子进程都跟 dev-backend.sh 在同一组,
  # 我们给整个组发信号就能保证子进程也跟着退出
  setsid bash "$SCRIPT_DIR/dev-backend.sh" "${BACKEND_ARGS[@]}" \
    > /tmp/zhiyu-backend.stdout 2>&1 &
  BACKEND_PID=$!
  ok "backend pid=$BACKEND_PID, 日志: /tmp/zhiyu-backend.stdout"
}

# --- 起 frontend (后台) ---
start_frontend() {
  step "▶ 启动 frontend (后台)"
  cd "$FRONTEND_DIR"
  setsid pnpm dev > /tmp/zhiyu-frontend.stdout 2>&1 &
  FRONTEND_PID=$!
  ok "frontend pid=$FRONTEND_PID, 日志: /tmp/zhiyu-frontend.stdout"
}

$SKIP_BACKEND  || start_backend
$SKIP_FRONTEND || start_frontend

# --- 等两边的"ready"信号, 然后把 stdout 喂给当前 tty ---
wait_ready() {
  local name="$1" pid="$2" log="$3" pattern="$4" timeout="${5:-90}"
  local waited=0
  while [[ $waited -lt $timeout ]]; do
    # 进程死了就别等了
    if ! kill -0 "$pid" 2>/dev/null; then
      err "$name 进程已退出, 看日志: $log"
      tail -n 40 "$log" >&2 || true
      return 1
    fi
    if grep -qE "$pattern" "$log" 2>/dev/null; then
      ok "$name 就绪 (用了 ${waited}s)"
      return 0
    fi
    sleep 1
    waited=$((waited+1))
    # 每 10 秒打个点, 提示用户还活着
    if (( waited % 10 == 0 )); then
      printf "  ⏳ 等 %s ready ... %ss\n" "$name" "$waited"
    fi
  done
  warn "$name 在 ${timeout}s 内未出现 '$pattern', 先继续"
  return 0
}

if [[ -n "$BACKEND_PID" ]]; then
  # Spring Boot 启动后一般会有 "Started ... in X.XXX seconds" / "Tomcat started on port"
  wait_ready "backend"  "$BACKEND_PID" /tmp/zhiyu-backend.stdout  "(Started .*Application|Tomcat started on port)" 120 \
    || { cleanup; exit 1; }
fi

if [[ -n "$FRONTEND_PID" ]]; then
  # vite 启动后会有 "Local:   http://localhost:xxxx/"
  wait_ready "frontend" "$FRONTEND_PID" /tmp/zhiyu-frontend.stdout "Local:.*http://localhost" 60 \
    || { cleanup; exit 1; }
fi

printf "\n${GREEN}══════════════════════════════════════════════════════════${NC}\n"
printf "${GREEN}  🚀 backend  + frontend 已就绪${NC}\n"
printf "${GREEN}  backend  : http://localhost:%s${NC}\n" "${SERVER_PORT:-8088}"
printf "${GREEN}  frontend : http://localhost:5173 (默认)${NC}\n"
printf "${GREEN}  按 Ctrl+C 同时关闭两边${NC}\n"
printf "${GREEN}══════════════════════════════════════════════════════${NC}\n\n"

# --- 把后台日志持续转发到当前 tty ---
# 用 tail -F 跟随, 用户能实时看到两边输出, 又不阻塞我们的 wait
if [[ -n "$BACKEND_PID" && -n "$FRONTEND_PID" ]]; then
  tail -n +1 -F /tmp/zhiyu-backend.stdout /tmp/zhiyu-frontend.stdout &
  TAIL_PID=$!
elif [[ -n "$BACKEND_PID" ]]; then
  tail -n +1 -F /tmp/zhiyu-backend.stdout &
  TAIL_PID=$!
else
  tail -n +1 -F /tmp/zhiyu-frontend.stdout &
  TAIL_PID=$!
fi

# --- 主循环: 等任意一个先退出, 就关另一个 ---
# wait -n 等任意一个子进程退出, 然后我们就触发 cleanup
while true; do
  set +e
  wait -n
  rc=$?
  set -e

  # 如果正在 shutting down, 让 cleanup 走完
  $SHUTTING_DOWN && break

  # 看看是哪个退了
  if [[ -n "$BACKEND_PID"  ]] && ! kill -0 "$BACKEND_PID"  2>/dev/null; then
    err "backend 进程已退出 (rc=$rc), 正在关 frontend"
    break
  fi
  if [[ -n "$FRONTEND_PID" ]] && ! kill -0 "$FRONTEND_PID" 2>/dev/null; then
    err "frontend 进程已退出 (rc=$rc), 正在关 backend"
    break
  fi
done

# 关掉 tail, 让 cleanup 接手
kill -TERM "$TAIL_PID" 2>/dev/null || true
sleep 0.2

# cleanup 会被 EXIT trap 触发