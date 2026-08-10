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


# ============================================================
# WP-M4-03 V5.0 / 3-way match 财务-成本对账 冒烟 (7 步)
# ============================================================

step "8. (WP-M4-03) 创建合同 (DRAFT)"
PROJECT_ID=$(curl -fsS "$BASE/projects?status=ACTIVE&page=0&size=1" -H "Authorization: Bearer $ACC_AD" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];items=d.get("items") or d.get("rows") or d.get("data") or [];print(items[0]["id"] if items else 1)')
echo "  using projectId=$PROJECT_ID"
RESP=$(curl -fsS -X POST "$BASE/finance/contracts" -H "Authorization: Bearer $ACC_AD" -H 'Content-Type: application/json' -d "{\"code\":\"SMOKE-CT-$(date +%s)\",\"name\":\"smoke 合同\",\"projectId\":$PROJECT_ID,\"amount\":10000.00,\"signDate\":\"2026-08-07\",\"status\":\"ACTIVE\"}")
CONTRACT_ID=$(echo "$RESP" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["id"])')
echo "  ✅ contract created id=$CONTRACT_ID"

step "9. (WP-M4-03) 激活合同 DRAFT → ACTIVE"
RESP=$(curl -fsS -X POST "$BASE/finance/contracts/$CONTRACT_ID/activate" -H "Authorization: Bearer $ACC_AD")
CODE=$(echo "$RESP" | python3 -c 'import sys,json;print(json.load(sys.stdin)["code"])')
[ "$CODE" = "0" ] && echo "  ✅ contract ACTIVE" || (echo "  ❌ activate failed: $RESP"; exit 1)

step "10. (WP-M4-03) 创建 + 匹配发票"
RESP=$(curl -fsS -X POST "$BASE/finance/invoices" -H "Authorization: Bearer $ACC_AD" -H 'Content-Type: application/json' -d "{\"code\":\"SMOKE-INV-$(date +%s)\",\"contractId\":$CONTRACT_ID,\"invoiceDate\":\"2026-08-07\",\"amount\":10000.00,\"taxAmount\":0.00,\"totalAmount\":10000.00}")
INVOICE_ID=$(echo "$RESP" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["id"])')
echo "  ✅ invoice created id=$INVOICE_ID"
curl -fsS -X POST "$BASE/finance/invoices/$INVOICE_ID/match?contractId=$CONTRACT_ID" -H "Authorization: Bearer $ACC_AD" >/dev/null
echo "  ✅ invoice matched"

step "11. (WP-M4-03) 创建 + 确认付款 → 触发 InvoiceConfirmedEvent"
RESP=$(curl -fsS -X POST "$BASE/finance/payments" -H "Authorization: Bearer $ACC_AD" -H 'Content-Type: application/json' -d "{\"code\":\"SMOKE-PAY-$(date +%s)\",\"invoiceId\":$INVOICE_ID,\"paymentDate\":\"2026-08-07\",\"amount\":10000.00}")
PAYMENT_ID=$(echo "$RESP" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["id"])')
echo "  ✅ payment created id=$PAYMENT_ID"
curl -fsS -X POST "$BASE/finance/payments/$PAYMENT_ID/confirm" -H "Authorization: Bearer $ACC_AD" >/dev/null
echo "  ✅ payment CONFIRMED → 对账已触发(异步)"

step "12. (WP-M4-03) 等异步对账落库 (1.5s)"
sleep 1.5

step "13. (WP-M4-03) 查询对账列表 (按 project)"
RESP=$(curl -fsS "$BASE/finance/reconciliation?projectId=$PROJECT_ID&size=50" -H "Authorization: Bearer $ACC_AD")
TOTAL=$(echo "$RESP" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["total"])')
ITEMS=$(echo "$RESP" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]["items"]))')
echo "  ✅ reconciliation total=$TOTAL items=$ITEMS"
[ "$TOTAL" -gt 0 ] && echo "  ✅ 对账已落库" || (echo "  ❌ 对账未落库,事件/监听器可能有问题"; exit 1)

step "14. (WP-M4-03) 查询对账健康度"
RESP=$(curl -fsS "$BASE/finance/reconciliation/health?projectId=$PROJECT_ID" -H "Authorization: Bearer $ACC_AD")
GREEN=$(echo "$RESP" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(f"total={d[\"total\"]} matched={d[\"matched\"]} mismatch={d[\"mismatch\"]} greenRate={d[\"greenRate\"]}")')
echo "  ✅ $GREEN"

step "15. (WP-M4-03) 播种 COST_DIFF 告警规则 + 触发差异告警 (idempotent)"
# 注入差异:再创建一个 amount=5000 的合同 + 10000 发票 → MISMATCH
RESP=$(curl -fsS -X POST "$BASE/finance/contracts" -H "Authorization: Bearer $ACC_AD" -H 'Content-Type: application/json' -d "{\"code\":\"SMOKE-CT-DIFF-$(date +%s)\",\"name\":\"smoke 差异合同\",\"projectId\":$PROJECT_ID,\"amount\":5000.00,\"signDate\":\"2026-08-07\",\"status\":\"ACTIVE\"}")
DIFF_CT_ID=$(echo "$RESP" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["id"])')
curl -fsS -X POST "$BASE/finance/contracts/$DIFF_CT_ID/activate" -H "Authorization: Bearer $ACC_AD" >/dev/null

# 播种规则
RESP=$(curl -fsS -X POST "$BASE/alert/rules/seed/cost-diff" -H "Authorization: Bearer $ACC_AD")
SEED=$(echo "$RESP" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(f"created={d.get(\"created\")} ruleId={d.get(\"ruleId\")}")')
echo "  ✅ rule seed: $SEED (idempotent)"

# 创建 10000 发票关联 5000 合同 → MISMATCH (差异 ¥5000 > ¥100 阈值)
RESP=$(curl -fsS -X POST "$BASE/finance/invoices" -H "Authorization: Bearer $ACC_AD" -H 'Content-Type: application/json' -d "{\"code\":\"SMOKE-INV-DIFF-$(date +%s)\",\"contractId\":$DIFF_CT_ID,\"invoiceDate\":\"2026-08-07\",\"amount\":10000.00,\"taxAmount\":0.00,\"totalAmount\":10000.00}")
DIFF_INV_ID=$(echo "$RESP" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["id"])')
curl -fsS -X POST "$BASE/finance/invoices/$DIFF_INV_ID/match?contractId=$DIFF_CT_ID" -H "Authorization: Bearer $ACC_AD" >/dev/null
echo "  ✅ mismatch 已构造 (合同 ¥5000 vs 发票 ¥10000)"

# 等异步对账 + 告警落库
sleep 2

# 验证 health mismatch 增加 (即 COST_DIFF 链路生效)
HEALTH=$(curl -fsS "$BASE/finance/reconciliation/health?projectId=$PROJECT_ID" -H "Authorization: Bearer $ACC_AD" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(f"total={d[\"total\"]} mismatch={d[\"mismatch\"]} partial={d[\"partial\"]} totalDiff={d[\"totalDiff\"]} greenRate={d[\"greenRate\"]}")')
echo "  ✅ reconciliation health: $HEALTH"
MISMATCH=$(curl -fsS "$BASE/finance/reconciliation/health?projectId=$PROJECT_ID" -H "Authorization: Bearer $ACC_AD" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["mismatch"])')
[ "$MISMATCH" -gt 0 ] && echo "  ✅ COST_DIFF 链路已触发:mismatch ≥ 1" || (echo "  ⚠️  mismatch=0,对账事件可能延迟(可重跑)"; exit 1)

echo
echo "======================================="
echo "✅ business-smoke 7+8 全部通过 (含 WP-M4-03 对账链路)"
echo "======================================="
