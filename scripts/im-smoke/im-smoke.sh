#!/usr/bin/env bash
# im-smoke.sh — IM 通道端到端冒烟(企业微信 / 钉钉 / 飞书)
#
# 流程:
#   1. 启动本地 mock 接收器(若未跑)
#   2. 确认后端在跑(检查 :8088)
#   3. login → admin token(创 binding / 配 DND)
#   4. 准备 lead_wu(4) 三通道 binding
#   5. 触发立项 → 验证 3 个 jsonl 增量
#   6. (可选)测 DND / 启停 binding / 错误凭证
#
# 用法:
#   ./im-smoke.sh                 # 跑基线
#   ./im-smoke.sh --with-dnd      # 含 DND 场景
#   ./im-smoke.sh --with-bad-creds# 含错误凭证
#   ./im-smoke.sh --cleanup       # 复位 binding + DND

set -uo pipefail

# ====== 可配 ======
REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BACKEND_URL="${BACKEND_URL:-http://localhost:8088}"
RECEIVER_PORT="${RECEIVER_PORT:-18080}"
LOG_DIR="${LOG_DIR:-$REPO_ROOT/logs}"
RECEIVER_LOG="${RECEIVER_LOG:-/tmp/im-webhook.log}"
BACKEND_LOG="${BACKEND_LOG:-/tmp/backend-im-smoke.log}"

ADMIN_USER="admin"
ADMIN_PASS="pmo123"
PM_USER="pm_zhang"
PM_PASS="pmo123"
LEAD_WU_ID=4
DEPT_ID=2
APPLICANT_ID=2

# ====== 工具 ======
red()   { printf "\033[31m%s\033[0m\n" "$*"; }
green() { printf "\033[32m%s\033[0m\n" "$*"; }
yell()  { printf "\033[33m%s\033[0m\n" "$*"; }

log_step() { echo; echo "========== $* =========="; }
log_info() { echo "  $*"; }
log_pass() { green "  ✅ $*"; }
log_fail() { red   "  ❌ $*"; }

# jsonl 行数(0 = 不存在)
count_jsonl() {
    local f="$LOG_DIR/$1"
    [ -f "$f" ] && wc -l < "$f" | tr -d ' ' || echo 0
}

ensure_receiver() {
    if curl -s --max-time 2 "http://localhost:${RECEIVER_PORT}/healthz" | grep -q UP; then
        log_info "receiver already up on :${RECEIVER_PORT}"
        return
    fi
    log_info "starting receiver on :${RECEIVER_PORT}..."
    cd "$REPO_ROOT"
    nohup python3 scripts/im-smoke/im_webhook_receiver.py \
        --port "$RECEIVER_PORT" --logdir "$LOG_DIR" \
        > "$RECEIVER_LOG" 2>&1 &
    sleep 1
    if curl -s --max-time 2 "http://localhost:${RECEIVER_PORT}/healthz" | grep -q UP; then
        log_pass "receiver started (pid=$!)"
    else
        log_fail "receiver failed to start; see $RECEIVER_LOG"
        exit 1
    fi
}

ensure_backend() {
    if ! curl -s --max-time 3 -o /dev/null -w "%{http_code}" "$BACKEND_URL/api/auth/login" \
         -H "Content-Type: application/json" -d "{}" | grep -qE "200|400|401"; then
        log_fail "backend not reachable at $BACKEND_URL"
        exit 1
    fi
    log_pass "backend reachable at $BACKEND_URL"
}

login() {
    local user="$1" pass="$2"
    local resp
    resp=$(curl -s -X POST "$BACKEND_URL/api/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"$user\",\"password\":\"$pass\"}")
    echo "$resp" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])"
}

# ====== 准备 binding ======
ensure_binding() {
    local token="$1" user_id="$2" channel="$3" ext_id="$4"
    local body
    body=$(curl -s -X POST "$BACKEND_URL/api/user-im-bindings" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $token" \
        -d "{\"userId\":$user_id,\"channel\":\"$channel\",\"externalUserId\":\"$ext_id\"}")
    echo "$body" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    if d.get('code') == 0: print('  [binding] ' + '$channel' + ' id=' + str(d['data']['id']))
    elif 'already exists' in d.get('message',''): print('  [binding] ' + '$channel' + ' already exists (skip)')
    else: print('  [binding] ' + '$channel' + ' failed: ' + d.get('message',''))
except Exception as e:
    print('  [binding] ' + '$channel' + ' parse err: ' + str(e))
"
}

disable_binding() {
    local token="$1" id="$2"
    curl -s -X PUT "$BACKEND_URL/api/user-im-bindings/$id" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $token" \
        -d '{"enabled":false}' > /dev/null
}

enable_binding() {
    local token="$1" id="$2"
    curl -s -X PUT "$BACKEND_URL/api/user-im-bindings/$id" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $token" \
        -d '{"enabled":true}' > /dev/null
}

# ====== 触发立项 ======
trigger_initiation() {
    local token="$1" tag="$2"
    local code="PRJ-$(date +%s)"
    local title="im-smoke $tag $code"
    curl -s -X POST "$BACKEND_URL/api/initiations" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $token" \
        -d "{
            \"code\":\"$code\",
            \"title\":\"$title\",
            \"background\":\"im-smoke\",
            \"goals\":\"smoke\",
            \"scope\":\"smoke\",
            \"applicantId\":$APPLICANT_ID,
            \"departmentId\":$DEPT_ID
        }" | python3 -c "import sys,json; d=json.load(sys.stdin); print('  [trigger] id='+str(d['data']['id'])+' code='+d['data']['code'])"
}

# ====== 验证 ======
verify_deltas() {
    local label="$1"
    local expect_wc="$2" expect_dd="$3" expect_fs="$4"
    local wc dd fs
    sleep 3
    wc=$(count_jsonl wechat_work.jsonl)
    dd=$(count_jsonl dingtalk.jsonl)
    fs=$(count_jsonl feishu.jsonl)
    echo "  [$label] jsonl lines: wechat=$wc dingtalk=$dd feishu=$fs"
    echo "  [$label] expect:      wechat=$expect_wc dingtalk=$expect_dd feishu=$expect_fs"
    local pass=true
    [ "$wc" = "$expect_wc" ] || { log_fail "wechat $wc != $expect_wc"; pass=false; }
    [ "$dd" = "$expect_dd" ] || { log_fail "dingtalk $dd != $expect_dd"; pass=false; }
    [ "$fs" = "$expect_fs" ] || { log_fail "feishu $fs != $expect_fs"; pass=false; }
    $pass && log_pass "delta match" || return 1
}

# ====== 主流程 ======
main() {
    local with_dnd=false with_bad_creds=false cleanup=false
    for arg in "$@"; do
        case "$arg" in
            --with-dnd)       with_dnd=true ;;
            --with-bad-creds) with_bad_creds=true ;;
            --cleanup)        cleanup=true ;;
            *) yell "unknown arg: $arg"; exit 2 ;;
        esac
    done

    log_step "0. preflight"
    ensure_receiver
    ensure_backend
    mkdir -p "$LOG_DIR"

    log_step "1. login"
    ADMIN_TOKEN=$(login "$ADMIN_USER" "$ADMIN_PASS")
    PM_TOKEN=$(login "$PM_USER" "$PM_PASS")
    [ -n "$ADMIN_TOKEN" ] && [ -n "$PM_TOKEN" ] || { log_fail "login failed"; exit 1; }
    log_pass "admin + pm_zhang logged in"

    if $cleanup; then
        log_step "CLEANUP — remove all bindings for lead_wu + disable DND"
        for bid in $(curl -s "$BACKEND_URL/api/user-im-bindings?userId=$LEAD_WU_ID" \
            -H "Authorization: Bearer $ADMIN_TOKEN" | \
            python3 -c "import sys,json; print(' '.join(str(b['id']) for b in json.load(sys.stdin)['data']))"); do
            curl -s -X DELETE "$BACKEND_URL/api/user-im-bindings/$bid" \
                -H "Authorization: Bearer $ADMIN_TOKEN" > /dev/null
            log_info "deleted binding id=$bid"
        done
        log_pass "cleanup done"
        exit 0
    fi

    log_step "2. ensure bindings (wechat/dingtalk/feishu) for lead_wu($LEAD_WU_ID)"
    ensure_binding "$ADMIN_TOKEN" "$LEAD_WU_ID" wechat_work "lead_wu_wecom_id"
    ensure_binding "$ADMIN_TOKEN" "$LEAD_WU_ID" dingtalk    "lead_wu@ding.com"
    ensure_binding "$ADMIN_TOKEN" "$LEAD_WU_ID" feishu      "lead_wu@feishu.com"

    log_step "2b. disable any pre-existing DND windows for lead_wu (test isolation)"
    EXISTING_DND=$(curl -s "$BACKEND_URL/api/user-im-quiet-hours?userId=$LEAD_WU_ID" \
        -H "Authorization: Bearer $ADMIN_TOKEN" | \
        python3 -c "import sys,json; print(' '.join(str(w['id']) for w in json.load(sys.stdin)['data']))")
    for did in $EXISTING_DND; do
        curl -s -X PUT "$BACKEND_URL/api/user-im-quiet-hours/$did" \
            -H "Content-Type: application/json" \
            -H "Authorization: Bearer $ADMIN_TOKEN" \
            -d '{"enabled":false}' > /dev/null
        log_info "disabled existing DND id=$did"
    done

    log_step "3. BASELINE trigger — expect all 3 channels to receive"
    BASE_WC=$(count_jsonl wechat_work.jsonl)
    BASE_DD=$(count_jsonl dingtalk.jsonl)
    BASE_FS=$(count_jsonl feishu.jsonl)
    log_info "baseline: wechat=$BASE_WC dingtalk=$BASE_DD feishu=$BASE_FS"
    trigger_initiation "$PM_TOKEN" "baseline"
    verify_deltas "baseline" $((BASE_WC+1)) $((BASE_DD+1)) $((BASE_FS+1)) || exit 1

    if $with_dnd; then
        log_step "4. DND on — expect 0 IM (email only)"
        # 当前小时:00 - 当前小时:59 窗口(覆盖所有分钟)
        H=$(date +%H)
        DND_BODY="{\"userId\":$LEAD_WU_ID,\"startTime\":\"$H:00\",\"endTime\":\"$H:59\",\"timezone\":\"Asia/Shanghai\"}"
        DND_RESP=$(curl -s -X POST "$BACKEND_URL/api/user-im-quiet-hours" \
            -H "Content-Type: application/json" \
            -H "Authorization: Bearer $ADMIN_TOKEN" \
            -d "$DND_BODY")
        DND_ID=$(echo "$DND_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
        log_info "DND window created id=$DND_ID ($H:00-$H:59)"
        # DND 期望值 = baseline 触发后(不变)
        EXPECT_WC=$((BASE_WC+1))
        EXPECT_DD=$((BASE_DD+1))
        EXPECT_FS=$((BASE_FS+1))
        trigger_initiation "$PM_TOKEN" "dnd-on"
        verify_deltas "dnd-on" "$EXPECT_WC" "$EXPECT_DD" "$EXPECT_FS" || exit 1
        # disable DND
        curl -s -X PUT "$BACKEND_URL/api/user-im-quiet-hours/$DND_ID" \
            -H "Content-Type: application/json" \
            -H "Authorization: Bearer $ADMIN_TOKEN" \
            -d '{"enabled":false}' > /dev/null
        log_info "DND disabled"
        log_step "5. DND off — expect +1 all 3 channels"
        trigger_initiation "$PM_TOKEN" "dnd-off"
        verify_deltas "dnd-off" $((EXPECT_WC+1)) $((EXPECT_DD+1)) $((EXPECT_FS+1)) || exit 1
    fi

    if $with_bad_creds; then
        log_step "6. bad creds — set corpSecret=WRONG, restart backend, expect wechat fail other pass"
        yell "  (this requires backend restart with PMO_WECOM_APP_SECRET=WRONG)"
        yell "  (manually: kill backend, set env, java -jar ...)"
        log_info "  then trigger again and grep for: gettoken errcode=40013"
    fi

    log_step "DONE"
    log_pass "all checks passed"
    log_info "log files:"
    log_info "  $LOG_DIR/wechat_work.jsonl"
    log_info "  $LOG_DIR/dingtalk.jsonl"
    log_info "  $LOG_DIR/feishu.jsonl"
    log_info "  $RECEIVER_LOG"
    log_info "  $BACKEND_LOG"
}

main "$@"
