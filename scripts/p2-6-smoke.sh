#!/usr/bin/env bash
# p2-6-smoke.sh — P2 #6 完整业务冒烟
#
# 在 P1.5 business-smoke 7 步业务流基础上,**端到端** 验证 P2 阶段新增能力:
#   - IM 通道(企业微信/钉钉/飞书)实时推送
#   - SSE 实时通知
#   - UNREAD 统一化(通道无关)
#   - 勿扰时段(命中启用窗口时 IM 跳过,邮件仍走)
#
# 流程:
#   Phase 0: preflight(mock 在跑 / 后端在跑 / binding 齐全 / DND 禁用)
#   Phase 1: 跑 business-smoke.sh 7 步业务流(基线)
#   Phase 2: 单独触发立项,断言 3 通道 jsonl 增量
#   Phase 3: 开 DND → 触发立项 → 断言 IM 0 增量(SSE 仍推)
#   Phase 4: 关 DND → 触发立项 → 断言 IM 恢复
#   Phase 5: 错误路径(binding 禁用 → IM 0 增量;恢复 → IM +1)
#
# 退出码: 0 = 全绿,非 0 = 任一阶段失败
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND_URL="${BACKEND_URL:-http://localhost:8088}"
RECEIVER_PORT="${RECEIVER_PORT:-18080}"
LOG_DIR="${LOG_DIR:-$REPO_ROOT/logs}"
LEAD_WU_ID=4
ADMIN_USER="admin"; ADMIN_PASS="pmo123"
PM_USER="pm_zhang"; PM_PASS="pmo123"
LD_USER="lead_wu";   LD_PASS="pmo123"
APPLICANT_ID=2
DEPT_ID=2

red()   { printf "\033[31m%s\033[0m\n" "$*"; }
green() { printf "\033[32m%s\033[0m\n" "$*"; }
yell()  { printf "\033[33m%s\033[0m\n" "$*"; }
step()  { echo; echo "▶ $*"; }
pass()  { green "  ✅ $*"; }
fail()  { red "  ❌ $*"; FAILED=1; }
info()  { echo "  $*"; }

FAILED=0

# ----- 工具 -----
count_jsonl() { [ -f "$LOG_DIR/$1" ] && wc -l < "$LOG_DIR/$1" | tr -d ' ' || echo 0; }

login() {
    curl -fsS -X POST "$BACKEND_URL/api/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"$1\",\"password\":\"$2\"}" | \
        python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])"
}

trigger() {
    local token="$1" tag="$2"
    local code="PRJ-$(date +%s)-$RANDOM"
    curl -fsS -X POST "$BACKEND_URL/api/initiations" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $token" \
        -d "{\"code\":\"$code\",\"title\":\"p2-6 $tag $code\",
             \"background\":\"smoke\",\"goals\":\"smoke\",\"scope\":\"smoke\",
             \"applicantId\":$APPLICANT_ID,\"departmentId\":$DEPT_ID}" | \
        python3 -c "import sys,json; d=json.load(sys.stdin)['data']; print('  [trigger] id='+str(d['id'])+' code='+d['code'])"
}

unread() {
    curl -fsS "$BACKEND_URL/api/notifications/unread-count" \
        -H "Authorization: Bearer $1" | \
        python3 -c "import sys,json; print(json.load(sys.stdin)['data']['count'])"
}

assert_delta() {
    local label="$1" expect_wc="$2" expect_dd="$3" expect_fs="$4"
    sleep 3
    local wc=$(count_jsonl wechat_work.jsonl)
    local dd=$(count_jsonl dingtalk.jsonl)
    local fs=$(count_jsonl feishu.jsonl)
    info "[$label] jsonl: wechat=$wc dingtalk=$dd feishu=$fs"
    info "[$label] expect: wechat=$expect_wc dingtalk=$expect_dd feishu=$expect_fs"
    [ "$wc" = "$expect_wc" ] || { fail "wechat $wc != $expect_wc"; return 1; }
    [ "$dd" = "$expect_dd" ] || { fail "dingtalk $dd != $expect_dd"; return 1; }
    [ "$fs" = "$expect_fs" ] || { fail "feishu $fs != $expect_fs"; return 1; }
    pass "$label delta match"
}

# ----- Phase 0: preflight -----
step "Phase 0. preflight"

if ! curl -s --max-time 2 "http://localhost:${RECEIVER_PORT}/healthz" | grep -q UP; then
    info "starting IM mock receiver on :${RECEIVER_PORT}"
    cd "$REPO_ROOT"
    nohup python3 scripts/im-smoke/im_webhook_receiver.py \
        --port "$RECEIVER_PORT" --logdir "$LOG_DIR" \
        > /tmp/im-webhook.log 2>&1 &
    sleep 1
fi
curl -s "http://localhost:${RECEIVER_PORT}/healthz" | grep -q UP && pass "mock receiver UP" || { fail "mock receiver DOWN"; exit 1; }

curl -s -o /dev/null -w "%{http_code}\n" -X POST "$BACKEND_URL/api/auth/login" \
    -H "Content-Type: application/json" -d '{}' 2>/dev/null | grep -qE "200|400|401" \
    && pass "backend reachable" || { fail "backend DOWN"; exit 1; }

mkdir -p "$LOG_DIR"

ADMIN_TOKEN=$(login "$ADMIN_USER" "$ADMIN_PASS")
PM_TOKEN=$(login "$PM_USER" "$PM_PASS")
LD_TOKEN=$(login "$LD_USER" "$LD_PASS")
[ -n "$ADMIN_TOKEN" ] && [ -n "$PM_TOKEN" ] && [ -n "$LD_TOKEN" ] || { fail "login failed"; exit 1; }
pass "3 角色登录 OK (admin / pm_zhang / lead_wu)"

# 准备 binding(已有则跳过)
for entry in "wechat_work:lead_wu_wecom_id" "dingtalk:lead_wu@ding.com" "feishu:lead_wu@feishu.com"; do
    ch="${entry%%:*}"; ext="${entry##*:}"
    RES=$(curl -s -X POST "$BACKEND_URL/api/user-im-bindings" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        -d "{\"userId\":$LEAD_WU_ID,\"channel\":\"$ch\",\"externalUserId\":\"$ext\"}")
    echo "$RES" | python3 -c "
import sys, json
d = json.load(sys.stdin)
if d.get('code') == 0: print('  [binding] '+'$ch'+' id='+str(d['data']['id']))
elif 'already exists' in d.get('message',''): print('  [binding] '+'$ch'+' already exists (skip)')
else: print('  [binding] '+'$ch'+' fail: '+d.get('message',''))
"
done

# 禁用任何已有 DND(测试隔离)
EXISTING=$(curl -s "$BACKEND_URL/api/user-im-quiet-hours?userId=$LEAD_WU_ID" \
    -H "Authorization: Bearer $ADMIN_TOKEN" | \
    python3 -c "import sys,json; print(' '.join(str(w['id']) for w in json.load(sys.stdin)['data']))")
for did in $EXISTING; do
    curl -s -X PUT "$BACKEND_URL/api/user-im-quiet-hours/$did" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        -d '{"enabled":false}' > /dev/null
    info "disabled DND id=$did"
done

# ----- Phase 1: 7 步业务流(原版) -----
step "Phase 1. 业务流 7 步(原 business-smoke.sh)"
info "跑 bash $REPO_ROOT/scripts/business-smoke.sh"
if bash "$REPO_ROOT/scripts/business-smoke.sh" > /tmp/business-smoke-run.log 2>&1; then
    pass "7 步业务流全绿"
    tail -3 /tmp/business-smoke-run.log
else
    fail "business-smoke.sh 失败,见 /tmp/business-smoke-run.log"
    cat /tmp/business-smoke-run.log
    exit 1
fi

# ----- Phase 2: IM 通道断言(独立 trigger) -----
step "Phase 2. IM 通道断言(企业微信/钉钉/飞书)"
BASE_WC=$(count_jsonl wechat_work.jsonl)
BASE_DD=$(count_jsonl dingtalk.jsonl)
BASE_FS=$(count_jsonl feishu.jsonl)
info "trigger 前基线: wechat=$BASE_WC dingtalk=$BASE_DD feishu=$BASE_FS"
trigger "$PM_TOKEN" "phase2"
assert_delta "phase2" $((BASE_WC+1)) $((BASE_DD+1)) $((BASE_FS+1))

# ----- Phase 3: DND on → IM 0 增量 -----
step "Phase 3. DND ON → IM 通道应被挡(SSE 仍推)"
H=$(date +%H)
DND_BODY="{\"userId\":$LEAD_WU_ID,\"startTime\":\"$H:00\",\"endTime\":\"$H:59\",\"timezone\":\"Asia/Shanghai\"}"
DND_RESP=$(curl -fsS -X POST "$BACKEND_URL/api/user-im-quiet-hours" \
    -H "Content-Type: application/json" -H "Authorization: Bearer $ADMIN_TOKEN" -d "$DND_BODY")
DND_ID=$(echo "$DND_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
info "DND window created id=$DND_ID ($H:00-$H:59)"

# 记录 SSE 推送是否仍发(看 lead_wu unread 增量)
BEFORE_UR=$(unread "$LD_TOKEN")
trigger "$PM_TOKEN" "phase3-dnd-on"
sleep 3
AFTER_UR=$(unread "$LD_TOKEN")
info "lead_wu unread: $BEFORE_UR → $AFTER_UR (DND 不挡 SSE,期望 +1)"
[ $((AFTER_UR - BEFORE_UR)) -ge 1 ] && pass "SSE 仍推 (UNREAD +1)" || fail "SSE 没推 (UNREAD +0)"

# phase3 期望值 = phase2 触发后(不变)
assert_delta "phase3-dnd-on" $((BASE_WC+1)) $((BASE_DD+1)) $((BASE_FS+1))

# 禁用 DND
curl -s -X PUT "$BACKEND_URL/api/user-im-quiet-hours/$DND_ID" \
    -H "Content-Type: application/json" -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d '{"enabled":false}' > /dev/null
info "DND disabled"

# ----- Phase 4: DND off → IM 恢复 -----
step "Phase 4. DND OFF → IM 通道恢复"
trigger "$PM_TOKEN" "phase4-dnd-off"
assert_delta "phase4-dnd-off" $((BASE_WC+2)) $((BASE_DD+2)) $((BASE_FS+2))

# ----- Phase 5: 错误路径(binding 启停) -----
step "Phase 5. 错误路径:binding 启停"
# 取 wechat binding id
WC_BID=$(curl -s "$BACKEND_URL/api/user-im-bindings?userId=$LEAD_WU_ID" \
    -H "Authorization: Bearer $ADMIN_TOKEN" | \
    python3 -c "import sys,json; d=json.load(sys.stdin)['data']; print(next((b['id'] for b in d if b['channel']=='wechat_work'), 0))")
info "wechat binding id=$WC_BID"
# 禁用 wechat
curl -s -X PUT "$BACKEND_URL/api/user-im-bindings/$WC_BID" \
    -H "Content-Type: application/json" -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d '{"enabled":false}' > /dev/null
info "wechat binding disabled"

# 抓 phase5-off 基线(trigger 之前)
PHASE5_OFF_BASE_WC=$(count_jsonl wechat_work.jsonl)
PHASE5_OFF_BASE_DD=$(count_jsonl dingtalk.jsonl)
PHASE5_OFF_BASE_FS=$(count_jsonl feishu.jsonl)
trigger "$PM_TOKEN" "phase5-binding-off"
# wechat 期望不变;dingtalk/feishu 期望 +1
assert_delta "phase5-binding-off" "$PHASE5_OFF_BASE_WC" $((PHASE5_OFF_BASE_DD+1)) $((PHASE5_OFF_BASE_FS+1))

# 恢复 wechat
curl -s -X PUT "$BACKEND_URL/api/user-im-bindings/$WC_BID" \
    -H "Content-Type: application/json" -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d '{"enabled":true}' > /dev/null
info "wechat binding re-enabled"

# 抓 phase5-on 基线(3 通道都应 +1,因为 wechat 已恢复)
PHASE5_ON_BASE_WC=$(count_jsonl wechat_work.jsonl)
PHASE5_ON_BASE_DD=$(count_jsonl dingtalk.jsonl)
PHASE5_ON_BASE_FS=$(count_jsonl feishu.jsonl)
trigger "$PM_TOKEN" "phase5-binding-on"
assert_delta "phase5-binding-on" $((PHASE5_ON_BASE_WC+1)) $((PHASE5_ON_BASE_DD+1)) $((PHASE5_ON_BASE_FS+1))

# ----- 总结 -----
echo
echo "======================================"
if [ $FAILED -eq 0 ]; then
    green "✅ P2 #6 完整业务冒烟 — 全绿"
    echo
    info "覆盖项:"
    info "  • Phase 1: business-smoke 7 步业务流(基线)"
    info "  • Phase 2: IM 3 通道实时推送"
    info "  • Phase 3: DND 启用 → IM 跳过,SSE/UNREAD 不受影响"
    info "  • Phase 4: DND 禁用 → IM 恢复"
    info "  • Phase 5: binding 启停 → 单通道隔离"
    echo
    info "jsonl 日志: $LOG_DIR/{wechat_work,dingtalk,feishu}.jsonl"
else
    red "❌ P2 #6 完整业务冒烟 — 失败,见上方 ❌ 行"
    exit 1
fi
echo "======================================"
