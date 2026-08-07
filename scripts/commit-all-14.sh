#!/usr/bin/env bash
# ============================================================
# 14-commit 拆分脚本 — V3.x + V4.x 累积改动按模块提交
# ============================================================
# 用法:
#   bash scripts/commit-all-14.sh                # 全部 14 个 commit 一气呵成
#   bash scripts/commit-all-14.sh dry-run        # 只预览不执行
#   bash scripts/commit-all-14.sh dry-run 1 3 5  # dry-run c1/c3/c5
#   bash scripts/commit-all-14.sh 1 3 5           # live c1/c3/c5
#
# 依赖:
#   - docs/commit-splits/cN.txt (精确文件清单,14 个)
#   - 当前工作区干净(只有本次累积 195 个文件未提交)
# ============================================================
set -euo pipefail
cd "$(dirname "$0")/.."

# ------------------ 1. 14 个 commit title ------------------
get_title() {
  case "$1" in
    1) echo "feat(milestone): V3.1 七阶段字典 + 4 端点分析" ;;
    2) echo "feat(cost): V4.0 工时→成本引擎 + 角色档 + 视图 (P0-A)" ;;
    3) echo "feat(finance): V4.2 合同/发票/付款/成本项 (3-way match)" ;;
    4) echo "feat(alert): V4.3 预警实体 + 仓库 + 6 种子规则 (数据层半成品)" ;;
    5) echo "feat(wbs): P3 WBS 任务拆解 + EVM + 网络图 + 甘特" ;;
    6) echo "feat(initiation): V3.0 立项全流程 + 5 子模块" ;;
    7) echo "feat(org): V2.8/V2.9 用户/部门/角色 三类 AdminController" ;;
    8) echo "feat(notification): P2 多通道 IM (钉钉/飞书/企微/SSE) + 4 事件" ;;
    9) echo "feat(timesheet+workload+project): V2.11-V2.13 工时/甘特/项目增强" ;;
    10) echo "feat(risk): V2.6/V2.7 风险矩阵 + 历史快照" ;;
    11) echo "feat(frontend): V3.x + V4.x 配套 UI (14 view + 8 api + 10 component)" ;;
    12) echo "chore(infra): pom + application*.yml + Jwt/RevokedToken + test schema" ;;
    13) echo "feat(cross-module): MySQL 迁移 + admin/dingtalk/tools 模块" ;;
    14) echo "chore(repo): Makefile + scripts + docs + PRD + uploads" ;;
    *) echo "c$1" ;;
  esac
}

# ------------------ 2. 解析参数 ------------------
DRY_RUN=false
if [ "${1:-}" = "dry-run" ]; then
  DRY_RUN=true
  shift
fi
DEFAULT="1 2 3 4 5 6 7 8 9 10 11 12 13 14"
SELECTED="${*:-${DEFAULT}}"

echo "=========================================="
echo "14-commit 拆分计划"
echo "=========================================="
echo "模式: $([ "$DRY_RUN" = true ] && echo 'DRY-RUN' || echo 'LIVE')"
echo "选中: ${SELECTED}"
echo

# ------------------ 3. 逐个 commit ------------------
for n in $SELECTED; do
  LIST="docs/commit-splits/c${n}.txt"
  if [ ! -f "$LIST" ]; then
    echo "❌ $LIST 不存在, 跳过 c$n"
    continue
  fi

  COUNT=$(wc -l < "$LIST" | tr -d ' ')
  TITLE_N=$(get_title "$n")
  echo "============ c$n (${COUNT} files): ${TITLE_N} ============"
  head -5 "$LIST" | sed 's/^/  /'
  if [ "$COUNT" -gt 5 ]; then
    echo "  ... (还有 $((COUNT - 5)) 个)"
  fi
  echo

  if [ "$DRY_RUN" = true ]; then
    echo "  [DRY-RUN] 跳过 add + commit"
    echo
    continue
  fi

  # git add (用文件列表,空目录 git 自动 add)
  while IFS= read -r f; do
    [ -z "$f" ] && continue
    git add -- "$f" 2>/dev/null || git add "$f"
  done < "$LIST"

  # git commit
  git commit -m "${TITLE_N}"
  echo "✅ c$n committed"
  echo
done

echo "=========================================="
echo "✅ 全部完成"
echo "=========================================="
git log --oneline -16