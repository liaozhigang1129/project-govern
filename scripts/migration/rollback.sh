#!/usr/bin/env bash
# 数据迁移回退脚本(占位实现,生产前需补全)
# 用法: ./rollback.sh <target>
#   target: 0=旧库 1=新库
set -euo pipefail
TARGET=${1:?target required (0/1)}

echo "🔴 紧急回退到 $TARGET,开始时间: $(date)"

# 1. 紧急停写
echo "  1. 紧急停写"
# ./scripts/emergency_stop_write.sh
sleep 1

# 2. 流量回切
echo "  2. 流量回切到 $TARGET"
# ./scripts/switch_traffic.sh $TARGET
sleep 1

# 3. 单写切回
echo "  3. 单写切回 $TARGET"
# ./scripts/single_write.sh $TARGET
sleep 1

# 4. 通知
echo "  4. 通知 PMO + Sponsor"
# ./scripts/notify_rollback.sh
sleep 1

# 5. 输出回退报告
REPORT=/tmp/rollback-$(date +%Y%m%d-%H%M%S).log
echo "  5. 回退报告 → $REPORT"
echo "target=$TARGET, time=$(date), operator=$(whoami)" > "$REPORT"

echo "🔴 回退完成 target=$TARGET,时间: $(date)"
