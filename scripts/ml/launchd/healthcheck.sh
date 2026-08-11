#!/bin/bash
# ml_service 健康检查 + 自动重启(macOS launchd 调用)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
LOGS="$PROJECT_ROOT/logs"
LOG="$LOGS/healthcheck.log"
PY="/opt/homebrew/opt/python@3.12/bin/python3.12"
TS=$(date '+%Y-%m-%d %H:%M:%S')
mkdir -p "$LOGS"

if ! curl -sf http://localhost:8000/health > /dev/null 2>&1; then
    pkill -f "ml_service.py" 2>/dev/null
    sleep 2
    /usr/bin/caffeinate -i $PY "$PROJECT_ROOT/scripts/ml/ml_service.py" \
        --model "$PROJECT_ROOT/models/milestone_lgbm_latest.pkl" \
        --port 8000 >> "$LOGS/ml_service.log" 2>&1 &
    echo "$TS: restarted ml_service" >> "$LOG"
else
    echo "$TS: healthy" >> "$LOG"
fi
