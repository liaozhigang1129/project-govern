#!/usr/bin/env bash
# 数据迁移切流脚本(占位实现,生产前需补全)
# 用法: ./cutover.sh <from> <to>
#   from/to: 0=旧库 1=新库
set -euo pipefail
FROM=${1:?from required (0/1)}
TO=${2:?to required (0/1)}

echo "▶ 切流: $FROM → $TO,开始时间: $(date)"

# 1. 摘流量(LB / DNS / 网关)
echo "  1. 摘流量 30s"
# ./scripts/drain_traffic.sh --seconds=30
sleep 1

# 2. 等待 drain 完成
echo "  2. 等待 drain 完成"
sleep 1

# 3. 双写关闭(单写)
echo "  3. 关闭双写,切单写到 $TO"
# ./scripts/single_write.sh $TO
sleep 1

# 4. 流量切到目标
echo "  4. 流量切到 $TO"
# ./scripts/switch_traffic.sh $TO
sleep 1

echo "✅ 切流完成 from=$FROM to=$TO,时间: $(date)"
