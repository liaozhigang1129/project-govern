#!/bin/bash
# 立项补料初始化脚本 — 创建 2 个项目立项,模拟"打回补料"流程并完成 V3.0 全 6 步
# 用法: ./seed-initiation-buliao.sh
set -e
BASE=http://localhost:8088/api
TS=$(date +%s)

login() {
  local user=$1
  local pwd=${2:-pmo123}
  curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
    -d "{\"username\":\"$user\",\"password\":\"$pwd\"}" \
    | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['accessToken'])"
}
api() {
  local method=$1 path=$2 token=$3 body=${4:-}
  if [ -n "$body" ]; then
    curl -s -X $method "$BASE$path" -H "Authorization: Bearer $token" \
      -H 'Content-Type: application/json' -d "$body"
  else
    curl -s -X $method "$BASE$path" -H "Authorization: Bearer $token"
  fi
}

PM_TOKEN=$(login pm_zhang)
PM2_TOKEN=$(login pm_li)
echo "✓ 登录 pm_zhang, pm_li"

# ===========================================================
# 立项 A: 客户CRM系统
# ===========================================================
echo ""
echo "===== A: 客户 CRM 系统 ====="
RES_A=$(curl -s -X POST $BASE/initiations -H "Authorization: Bearer $PM_TOKEN" \
  -H 'Content-Type: application/json' -d "{
    \"code\":\"IR-${TS}-A\",
    \"title\":\"客户CRM系统 V3.0\",
    \"clientName\":\"华兴银行\",
    \"clientContactName\":\"王经理\",
    \"clientContactPhone\":\"13900000001\",
    \"arUserName\":\"AR-王经理\",
    \"srUserName\":\"SR-李方案\",
    \"frUserName\":\"FR-张架构\",
    \"contractAmount\":1800000,
    \"contractCurrency\":\"CNY\",
    \"planWorkWeeks\":16,
    \"background\":\"客户现有 CRM 数据散落 5 套系统,客户经理 / 售前 / 实施 各自维护客户档案,信息不一致。本期统一整合到 V3.0,打通营销-销售-实施全链路。\",
    \"goals\":\"1) 客户档案一致性 100% \\n2) 客户经理人均跟进客户数 3x 提升 \\n3) 销售-实施交接时长从 5 天降到 1 天\",
    \"scope\":\"客户主数据 + 销售跟进 + 商机 + 合同 + 实施交付 + 客户健康度(本期) \\n\\u4e0d\\u542b: 财务结算, 营销活动 ROI 预测\"
  }")
ID_A=$(echo "$RES_A" | python3 -c "import json,sys;d=json.load(sys.stdin);print(d.get('data',{}).get('id',''))")
echo "✓ 立项 A 创建 id=$ID_A"

# SOW 上传 (mock PDF 文件, ~10KB random)
echo "dummy SOW content for customer CRM" > /tmp/sow_crm.pdf
SOW_A=$(curl -s -X POST $BASE/initiations/$ID_A/sow -H "Authorization: Bearer $PM_TOKEN" \
  -F "file=@/tmp/sow_crm.pdf;type=application/pdf")
echo "  SOW code=$(python3 -c "import json,sys;print(json.load(sys.stdin).get('code'))" <<< "$SOW_A")"

# AI WBS 生成
echo "→ AI 生成 WBS 草稿..."
AI_A=$(curl -s -X POST $BASE/initiations/$ID_A/ai-wbs/generate -H "Authorization: Bearer $PM_TOKEN" \
  -H 'Content-Type: application/json' -d '{"granularityWeeks":2,"sowText":"开发一个完整的客户关系管理系统 (CRM)。核心模块: 1) 客户主数据管理 (含公司/联系人/商机) 2) 销售漏斗与机会跟踪 3) 合同管理 (起草/审批/归档) 4) 销售订单 (SO) 与回款计划 5) 客户服务工单与 SLA 6) 数据看板 (销售/客户/回款多维分析)。要求: Java 17 + Spring Boot 3 后端,Vue 3 + Element Plus 前端,PostgreSQL 数据库,Docker 部署,工期 16 周,团队 8 人。"}')
echo "$AI_A" | python3 -c "
import json,sys
r=json.load(sys.stdin)
if r.get('code')!=0: print('  AI 失败:', r.get('message')); sys.exit(0)
d=r['data']['draft']
n_ms=len(d['milestones']); n_wp=len(d['workPackages']); n_rk=len(d['risks'])
print('  里程碑:', n_ms, '个工作包:', n_wp, '个风险:', n_rk, '个')
"

# 资源派遣
echo "→ 资源派遣..."
for row in \
  '{"userName":"王莉","roleCode":"PM","allocationPct":100,"planHours":480,"startDate":"2026-06-15","endDate":"2026-10-15"}' \
  '{"userName":"陈架构","roleCode":"ARCH","allocationPct":100,"planHours":240,"startDate":"2026-06-15","endDate":"2026-08-15"}' \
  '{"userName":"李前端","roleCode":"DEV","allocationPct":100,"planHours":640,"startDate":"2026-07-01","endDate":"2026-09-30"}' \
  '{"userName":"赵后端","roleCode":"DEV","allocationPct":100,"planHours":640,"startDate":"2026-07-01","endDate":"2026-09-30"}' \
  '{"userName":"周测试","roleCode":"QA","allocationPct":80,"planHours":400,"startDate":"2026-08-15","endDate":"2026-10-15"}' \
  '{"userName":"吴实施","roleCode":"CFG","allocationPct":60,"planHours":240,"startDate":"2026-09-15","endDate":"2026-10-15"}'; do
  api POST /initiations/$ID_A/resource-plans "$PM_TOKEN" "$row" > /dev/null
done
TOTAL_RES_A=$(curl -s -H "Authorization: Bearer $PM_TOKEN" "$BASE/initiations/$ID_A/resource-plans/total-cost" | python3 -c "import json,sys;print(json.load(sys.stdin)['data'])")
echo "  资源小计: ¥ $TOTAL_RES_A"

# 风险应对
echo "→ 风险应对..."
for r in \
  '{"riskTitle":"客户主数据迁移存在数据丢失风险","riskLevel":"HIGH","riskSuggestion":"分批次迁移, 每批做数据校验 + 双向同步 7 天","responseAction":"采购 ETL 工具 + 雇佣 2 名数据治理专家 4 周","responseCost":120000,"status":"PLANNED"}' \
  '{"riskTitle":"业务部门对 CRM 接受度低, 推广困难","riskLevel":"MEDIUM","riskSuggestion":"业务部门深度参与需求评审 + UAT","responseAction":"业务部门 1 名对接人 1 人天/周 + 培训 2 场","responseCost":30000,"status":"PLANNED"}' \
  '{"riskTitle":"三方短信/邮件网关对接周期长","riskLevel":"MEDIUM","riskSuggestion":"并行启动 2 家供应商 POC","responseAction":"商务提前签框架, POC 同步进行","responseCost":15000,"status":"PLANNED"}' \
  '{"riskTitle":"客户内网 VPN 性能瓶颈","riskLevel":"LOW","riskSuggestion":"提前压测, 准备云端 fallback","responseAction":"准备云端 SaaS 应急方案 1 周","responseCost":20000,"status":"PLANNED"}'; do
  api POST /initiations/$ID_A/risks "$PM_TOKEN" "$r" > /dev/null
done
RISK_A=$(curl -s -H "Authorization: Bearer $PM_TOKEN" "$BASE/initiations/$ID_A/risks/total-cost" | python3 -c "import json,sys;print(json.load(sys.stdin)['data'])")
echo "  风险小计: ¥ $RISK_A"

# 预算冻结
echo "→ 预算冻结..."
FREEZE_A=$(curl -s -X POST $BASE/initiations/$ID_A/budget-freeze -H "Authorization: Bearer $PM_TOKEN" \
  -H 'Content-Type: application/json' -d "{\"otherCost\":80000,\"contractAmountOverride\":1800000}")
echo "$FREEZE_A" | python3 -c "
import json,sys
d=json.load(sys.stdin)['data']
ca=d['contractAmount']; rc=d['resourceCost']; rk=d['riskCost']; oc=d['otherCost']
tc=d['totalCost']; mg=d['margin']; mp=d['marginPct']
print('  合同: ¥', format(ca, ',.2f'))
print('  资源: ¥', format(rc, ',.2f'))
print('  风险: ¥', format(rk, ',.2f'))
print('  其他: ¥', format(oc, ',.2f'))
print('  总成本: ¥', format(tc, ',.2f'))
print('  毛利: ¥', format(mg, ',.2f'), '(', mp, '%)')
"

# ===========================================================
# 立项 B: 供应链可视化
# ===========================================================
echo ""
echo "===== B: 供应链可视化平台 ====="
RES_B=$(curl -s -X POST $BASE/initiations -H "Authorization: Bearer $PM2_TOKEN" \
  -H 'Content-Type: application/json' -d "{
    \"code\":\"IR-${TS}-B\",
    \"title\":\"供应链可视化平台\",
    \"clientName\":\"宝莱制造\",
    \"clientContactName\":\"陈总监\",
    \"clientContactPhone\":\"13900000002\",
    \"arUserName\":\"AR-陈总监\",
    \"srUserName\":\"SR-周方案\",
    \"frUserName\":\"FR-吴架构\",
    \"contractAmount\":2400000,
    \"contractCurrency\":\"CNY\",
    \"planWorkWeeks\":20,
    \"background\":\"宝莱有 8 个工厂, 各自维护供应商档案 + 物流轨迹, 总部无法实时看到库存 / 在途 / 异常。本期建统一可视化平台, 接入 ERP/MES 数据。\",
    \"goals\":\"1) 8 工厂库存 / 在途 实时可视化 \\n2) 异常预警 30 分钟内触发 \\n3) 月度盘点工时降低 50%\",
    \"scope\":\"\\u91c7\\u8d2d\\u4e3b\\u6570\\u636e + \\u4f9b\\u5e94\\u5546\\u7ba1\\u7406 + \\u91c7\\u8d2d\\u8ba2\\u5355 + \\u5e93\\u5b58\\u53ef\\u89c6\\u5316 + \\u8fd0\\u8f93\\u8f68\\u8ff9 + \\u5f02\\u5e38\\u9884\\u8b66\\n\\n\\u4e0d\\u542b: \\u751f\\u4ea7\\u8c03\\u5ea6, \\u8d28\\u91cf\\u68c0\\u6d4b\"
  }")
ID_B=$(echo "$RES_B" | python3 -c "import json,sys;d=json.load(sys.stdin);print(d.get('data',{}).get('id',''))")
echo "✓ 立项 B 创建 id=$ID_B"

echo "dummy SOW for supply chain" > /tmp/sow_sc.pdf
SOW_B=$(curl -s -X POST $BASE/initiations/$ID_B/sow -H "Authorization: Bearer $PM2_TOKEN" \
  -F "file=@/tmp/sow_sc.pdf;type=application/pdf")
echo "  SOW code=$(python3 -c "import json,sys;print(json.load(sys.stdin).get('code'))" <<< "$SOW_B")"

echo "→ AI 生成 WBS 草稿..."
AI_B=$(curl -s -X POST $BASE/initiations/$ID_B/ai-wbs/generate -H "Authorization: Bearer $PM2_TOKEN" \
  -H 'Content-Type: application/json' -d '{"granularityWeeks":2,"sowText":"为宝莱制造搭建供应链可视化平台, 接入 8 个工厂的 ERP/MES 系统, 整合采购主数据 + 供应商 + 采购订单 + 库存 + 物流轨迹, 实现总部实时可视化, 异常 30 分钟内预警。工期 20 周, 团队 10 人, Java + Vue + ClickHouse 技术栈。"}')
echo "$AI_B" | python3 -c "
import json,sys
r=json.load(sys.stdin)
if r.get('code')!=0: print('  AI 失败:', r.get('message')); sys.exit(0)
d=r['data']['draft']
n_ms=len(d['milestones']); n_wp=len(d['workPackages']); n_rk=len(d['risks'])
print('  里程碑:', n_ms, '个工作包:', n_wp, '个风险:', n_rk, '个')
"

echo "→ 资源派遣..."
for row in \
  '{"userName":"钱管理","roleCode":"PM","allocationPct":100,"planHours":640,"startDate":"2026-06-20","endDate":"2026-11-15"}' \
  '{"userName":"孙架构","roleCode":"ARCH","allocationPct":100,"planHours":320,"startDate":"2026-06-20","endDate":"2026-08-31"}' \
  '{"userName":"李数据","roleCode":"DEV","allocationPct":100,"planHours":800,"startDate":"2026-07-15","endDate":"2026-10-31"}' \
  '{"userName":"周前后","roleCode":"DEV","allocationPct":100,"planHours":800,"startDate":"2026-07-15","endDate":"2026-10-31"}' \
  '{"userName":"吴算法","roleCode":"DEV","allocationPct":100,"planHours":480,"startDate":"2026-08-15","endDate":"2026-10-31"}' \
  '{"userName":"郑测试","roleCode":"QA","allocationPct":100,"planHours":480,"startDate":"2026-09-15","endDate":"2026-11-15"}' \
  '{"userName":"王实施","roleCode":"CFG","allocationPct":80,"planHours":320,"startDate":"2026-10-15","endDate":"2026-11-15"}'; do
  api POST /initiations/$ID_B/resource-plans "$PM2_TOKEN" "$row" > /dev/null
done
TOTAL_RES_B=$(curl -s -H "Authorization: Bearer $PM2_TOKEN" "$BASE/initiations/$ID_B/resource-plans/total-cost" | python3 -c "import json,sys;print(json.load(sys.stdin)['data'])")
echo "  资源小计: ¥ $TOTAL_RES_B"

echo "→ 风险应对..."
for r in \
  '{"riskTitle":"8 工厂 ERP 数据格式差异大, 集成成本不可控","riskLevel":"CRITICAL","riskSuggestion":"先做 1 工厂 POC, 模板化后再批量复制","responseAction":"POC 阶段投入 2 名数据集成专家 6 周 + 商务谈判 1 工厂折扣","responseCost":280000,"status":"PLANNED"}' \
  '{"riskTitle":"物流轨迹 API 三方供应商价格高","riskLevel":"HIGH","riskSuggestion":"3 家供应商竞标 + 自建降级方案","responseAction":"商务锁定 1 家主供应商 + 1 家备, 谈判 30% 折扣","responseCost":60000,"status":"PLANNED"}' \
  '{"riskTitle":"异常预警规则误报率高","riskLevel":"MEDIUM","riskSuggestion":"上线后 2 周静默期, 业务方确认阈值","responseAction":"准备业务方 1 人天/周 + 调优 4 周","responseCost":40000,"status":"PLANNED"}' \
  '{"riskTitle":"总部 vs 工厂 KPI 冲突,推广阻力","riskLevel":"HIGH","riskSuggestion":"总部一把手站台, 工厂 KPI 不挂钩但可参考","responseAction":"2 场总部宣导会 + 工厂参观 1 次","responseCost":25000,"status":"PLANNED"}'; do
  api POST /initiations/$ID_B/risks "$PM2_TOKEN" "$r" > /dev/null
done
RISK_B=$(curl -s -H "Authorization: Bearer $PM2_TOKEN" "$BASE/initiations/$ID_B/risks/total-cost" | python3 -c "import json,sys;print(json.load(sys.stdin)['data'])")
echo "  风险小计: ¥ $RISK_B"

echo "→ 预算冻结..."
FREEZE_B=$(curl -s -X POST $BASE/initiations/$ID_B/budget-freeze -H "Authorization: Bearer $PM2_TOKEN" \
  -H 'Content-Type: application/json' -d "{\"otherCost\":150000,\"contractAmountOverride\":2400000}")
echo "$FREEZE_B" | python3 -c "
import json,sys
d=json.load(sys.stdin)['data']
ca=d['contractAmount']; rc=d['resourceCost']; rk=d['riskCost']; oc=d['otherCost']
tc=d['totalCost']; mg=d['margin']; mp=d['marginPct']
print('  合同: ¥', format(ca, ',.2f'))
print('  资源: ¥', format(rc, ',.2f'))
print('  风险: ¥', format(rk, ',.2f'))
print('  其他: ¥', format(oc, ',.2f'))
print('  总成本: ¥', format(tc, ',.2f'))
print('  毛利: ¥', format(mg, ',.2f'), '(', mp, '%)')
"

echo ""
echo "===== 初始化完成 ====="
echo "A. 客户CRM系统:  http://localhost:5173/initiations (搜 IR-${TS}-A)"
echo "B. 供应链可视化: http://localhost:5173/initiations (搜 IR-${TS}-B)"
