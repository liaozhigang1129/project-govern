#!/bin/bash
# ml_service 健康检查 + 自动重启
LOG="/Users/lzg/Documents/pmo-pms/logs/healthcheck.log"
PMO="/Users/lzg/Documents/pmo-pms"
PY="/opt/homebrew/opt/python@3.12/bin/python3.12"
TS=$(date '+%Y-%m-%d %H:%M:%S')
if ! curl -sf http://localhost:8000/health > /dev/null 2>&1; then
    pkill -f "ml_service.py" 2>/dev/null
    sleep 2
    /usr/bin/caffeinate -i $PY $PMO/scripts/ml/ml_service.py --model $PMO/models/milestone_lgbm_latest.pkl --port 8000 >> $PMO/logs/ml_service.log 2>&1 &
    echo "$TS: restarted ml_service" >> $LOG
else
    echo "$TS: healthy" >> $LOG
fi
