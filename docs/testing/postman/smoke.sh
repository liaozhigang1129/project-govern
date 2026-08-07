#!/usr/bin/env bash
# PMO PMS API 烟雾测试 - 16 个核心调用,需先启动后端 (端口 8088)
# 用法: chmod +x smoke.sh && ./smoke.sh
set -euo pipefail
BASE='http://localhost:8088/api'
echo '▶ 1. 登录'
LOGIN=$(curl -fsS -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -d '{"username":"admin","password":"pmo123"}')
echo "$LOGIN" | head -c 200; echo
TOKEN=$(echo "$LOGIN" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
H="-H Authorization:\ Bearer\ $TOKEN"

echo '▶ 2. 当前用户'
curl -fsS "$BASE/auth/me" -H "Authorization: Bearer $TOKEN" | head -c 200; echo

echo '▶ 3. Dashboard 4 项 KPI'
curl -fsS "$BASE/dashboard/kpis" -H "Authorization: Bearer $TOKEN" | head -c 200; echo

echo '▶ 4. Dashboard 项目卡片'
curl -fsS "$BASE/dashboard/active-projects" -H "Authorization: Bearer $TOKEN" | head -c 200; echo

echo '▶ 5. 健康度分布'
curl -fsS "$BASE/dashboard/health-distribution" -H "Authorization: Bearer $TOKEN" | head -c 200; echo

echo '▶ 6. 项目列表'
PROJECTS=$(curl -fsS "$BASE/projects" -H "Authorization: Bearer $TOKEN")
echo "$PROJECTS" | head -c 200; echo
PROJECT_ID=$(echo "$PROJECTS" | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d["data"][0]["id"] if d["data"] else 1)')

echo '▶ 7. 项目详情'
curl -fsS "$BASE/projects/$PROJECT_ID" -H "Authorization: Bearer $TOKEN" | head -c 200; echo

echo '▶ 8. 部门列表'
curl -fsS "$BASE/departments" -H "Authorization: Bearer $TOKEN" | head -c 200; echo

echo '▶ 9. 用户列表'
curl -fsS "$BASE/users" -H "Authorization: Bearer $TOKEN" | head -c 200; echo

echo '▶ 10. 项目状态字典'
curl -fsS "$BASE/dict/project-statuses" -H "Authorization: Bearer $TOKEN" | head -c 200; echo

echo '▶ 11. 立项状态字典'
curl -fsS "$BASE/dict/initiation-statuses" -H "Authorization: Bearer $TOKEN" | head -c 200; echo

echo '▶ 12. 审批步骤字典'
curl -fsS "$BASE/dict/approval-steps" -H "Authorization: Bearer $TOKEN" | head -c 200; echo

echo '▶ 13. 立项列表'
INITIATIONS=$(curl -fsS "$BASE/initiations" -H "Authorization: Bearer $TOKEN")
echo "$INITIATIONS" | head -c 200; echo

echo '▶ 14. 里程碑按项目'
curl -fsS "$BASE/milestones/by-project/$PROJECT_ID" -H "Authorization: Bearer $TOKEN" | head -c 200; echo

echo '▶ 15. 项目加权进度'
curl -fsS "$BASE/milestones/progress/$PROJECT_ID" -H "Authorization: Bearer $TOKEN" | head -c 200; echo

echo '▶ 16. 状态分布'
curl -fsS "$BASE/dashboard/status-distribution" -H "Authorization: Bearer $TOKEN" | head -c 200; echo

echo '✅ 16 个核心调用全部 2xx 通过'
