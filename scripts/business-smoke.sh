#!/usr/bin/env bash
set -euo pipefail
BASE='http://localhost:8088/api'

step() { echo; echo "▶ $1"; }

step "0. 登录 3 角色"
ACC_PM=$(curl -fsS -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -d '{"username":"pm_zhang","password":"pmo123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
ACC_LD=$(curl -fsS -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -d '{"username":"lead_wu","password":"pmo123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
ACC_AD=$(curl -fsS -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -d '{"username":"admin","password":"pmo123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
echo "  ✅ pm_zhang/lead_wu/admin 3 角色登录 OK"

step "1. pm_zhang 提交立项"
SUBMIT=$(curl -fsS -X POST "$BASE/initiations" -H "Authorization: Bearer $ACC_PM" -H 'Content-Type: application/json' -d '{"title":"CI smoke 业务验证项目","applicantId":2,"departmentId":2,"background":"CI 业务测试","goals":"E2E 验证","scope":"冒烟","estimatedBudget":100000,"estimatedDurationDays":30}')
INIT_ID=$(echo "$SUBMIT" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["id"])')
echo "  ✅ submit 成功,id=$INIT_ID"

step "2. 验证 lead_wu 收件箱 ≥ 1"
sleep 2
COUNT=$(curl -fsS "$BASE/notifications/unread-count" -H "Authorization: Bearer $ACC_LD" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["count"])')
[ "$COUNT" -ge 1 ] && echo "  ✅ lead_wu unread=$COUNT" || (echo "  ❌ expected ≥1 got $COUNT"; exit 1)

step "3. lead_wu (DEPT_LEAD) 审批 APPROVED"
RESP=$(curl -fsS -X POST "$BASE/initiations/$INIT_ID/decide" -H "Authorization: Bearer $ACC_LD" -H 'Content-Type: application/json' -d '{"decision":"APPROVED","comment":"DEPT 批准"}')
CODE=$(echo "$RESP" | python3 -c 'import sys,json;print(json.load(sys.stdin)["code"])')
[ "$CODE" = "0" ] && echo "  ✅ DEPT approve OK" || (echo "  ❌ DEPT approve fail: $RESP"; exit 1)

step "4. admin (PMO_ADMIN) 审批 APPROVED"
RESP=$(curl -fsS -X POST "$BASE/initiations/$INIT_ID/decide" -H "Authorization: Bearer $ACC_AD" -H 'Content-Type: application/json' -d '{"decision":"APPROVED","comment":"PMO 批准"}')
CODE=$(echo "$RESP" | python3 -c 'import sys,json;print(json.load(sys.stdin)["code"])')
[ "$CODE" = "0" ] && echo "  ✅ PMO approve OK" || (echo "  ❌ PMO approve fail: $RESP"; exit 1)

step "5. 验证 pm_zhang 收到 ≥ 2 决定通知"
sleep 2
COUNT=$(curl -fsS "$BASE/notifications/unread-count" -H "Authorization: Bearer $ACC_PM" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["count"])')
[ "$COUNT" -ge 2 ] && echo "  ✅ pm_zhang unread=$COUNT" || (echo "  ❌ expected ≥2 got $COUNT"; exit 1)

step "6. Gantt API 验证"
GANTT=$(curl -fsS "$BASE/gantt" -H "Authorization: Bearer $ACC_PM" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print("bars=",len(d.get("bars",[])),"range=",d.get("rangeFrom",""),"→",d.get("rangeTo",""))')
echo "  ✅ Gantt 返: $GANTT"

step "7. markRead 全部已读"
N1=$(curl -fsS "$BASE/notifications/unread-count" -H "Authorization: Bearer $ACC_LD" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["count"])')
RESP=$(curl -fsS -X POST "$BASE/notifications/read" -H "Authorization: Bearer $ACC_LD" -H 'Content-Type: application/json' -d '{"all":true}')
CODE=$(echo "$RESP" | python3 -c 'import sys,json;print(json.load(sys.stdin)["code"])')
N2=$(curl -fsS "$BASE/notifications/unread-count" -H "Authorization: Bearer $ACC_LD" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["count"])')
[ "$CODE" = "0" ] && [ "$N1" -gt 0 ] && [ "$N2" -eq 0 ] && echo "  ✅ markRead OK ($N1 → 0)" || (echo "  ❌ markRead 失败 ($N1 → $N2)"; exit 1)

echo
echo "======================================="
echo "✅ 7/7 business-smoke 全部通过"
echo "======================================="
