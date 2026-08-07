#!/bin/bash
# 安装/卸载 PMO AI 定时任务 (macOS launchd)
# 用法: ./install.sh {install|uninstall|status}
set -e
LAUNCH_DIR="$HOME/Library/LaunchAgents"
PMO="/Users/lzg/Documents/pmo-pms"
SRC="$PMO/scripts/ml/launchd"
mkdir -p "$LAUNCH_DIR" "$PMO/logs"

case "$1" in
    install)
        echo "[install] loading 4 LaunchAgents..."
        cp "$SRC/outcome_daily.plist" "$LAUNCH_DIR/com.pmo.ai.outcome-daily.plist"
        cp "$SRC/train_weekly.plist"  "$LAUNCH_DIR/com.pmo.ai.train-weekly.plist"
        cp "$SRC/model_switch.plist"  "$LAUNCH_DIR/com.pmo.ai.model-switch.plist"
        cp "$SRC/healthcheck.plist"  "$LAUNCH_DIR/com.pmo.ai.healthcheck.plist"
        for f in outcome-daily train-weekly model-switch healthcheck; do
            launchctl unload "$LAUNCH_DIR/com.pmo.ai.${f}.plist" 2>/dev/null || true
        done
        launchctl load "$LAUNCH_DIR/com.pmo.ai.outcome-daily.plist"
        launchctl load "$LAUNCH_DIR/com.pmo.ai.train-weekly.plist"
        launchctl load "$LAUNCH_DIR/com.pmo.ai.model-switch.plist"
        launchctl load "$LAUNCH_DIR/com.pmo.ai.healthcheck.plist"
        echo "[install] DONE"
        echo "verify with: ./install.sh status"
        ;;
    uninstall)
        echo "[uninstall] unloading..."
        for f in outcome-daily train-weekly model-switch healthcheck; do
            launchctl unload "$LAUNCH_DIR/com.pmo.ai.${f}.plist" 2>/dev/null || true
        done
        rm -f "$LAUNCH_DIR/com.pmo.ai."*.plist
        echo "[uninstall] DONE"
        ;;
    status)
        echo "[status] PMO AI jobs:"
        launchctl list 2>/dev/null | grep "com.pmo.ai" | awk "{printf \"  %-40s pid=%s status=%s\\n\", \$3, \$1, \$2}"
        echo ""
        echo "[status] recent logs:"
        ls -lt "$PMO/logs/"*.log 2>/dev/null | head -10 | awk "{printf \"  %s %s %s\\n\", \$6, \$7, \$9}"
        ;;
    *)
        echo "Usage: $0 {install|uninstall|status}"
        exit 1
        ;;
esac
