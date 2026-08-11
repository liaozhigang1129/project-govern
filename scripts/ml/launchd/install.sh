#!/bin/bash
# 安装/卸载 project-govern AI 定时任务 (macOS launchd)
# 用法: ./install.sh {install|uninstall|status}
set -e

# 自动推断项目根目录(从脚本位置向上两级)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
LAUNCH_DIR="$HOME/Library/LaunchAgents"
LABEL_PREFIX="com.projectgovern.ai"
SRC="$SCRIPT_DIR"
LOGS="$PROJECT_ROOT/logs"
mkdir -p "$LAUNCH_DIR" "$LOGS"

case "$1" in
    install)
        echo "[install] loading 4 LaunchAgents from $SRC ..."
        for f in outcome_daily train_weekly model_switch healthcheck; do
            launchctl unload "$LAUNCH_DIR/${LABEL_PREFIX}.${f%_*}.plist" 2>/dev/null || true
        done
        for f in outcome_daily train_weekly model_switch healthcheck; do
            cp "$SRC/${f}.plist" "$LAUNCH_DIR/${LABEL_PREFIX}.${f%_*}.plist"
            launchctl load "$LAUNCH_DIR/${LABEL_PREFIX}.${f%_*}.plist"
        done
        echo "[install] DONE"
        echo "verify with: $0 status"
        ;;
    uninstall)
        echo "[uninstall] unloading..."
        for f in outcome_daily train_weekly model_switch healthcheck; do
            launchctl unload "$LAUNCH_DIR/${LABEL_PREFIX}.${f%_*}.plist" 2>/dev/null || true
        done
        rm -f "$LAUNCH_DIR/${LABEL_PREFIX}."*.plist
        echo "[uninstall] DONE"
        ;;
    status)
        echo "[status] project-govern AI jobs:"
        launchctl list 2>/dev/null | grep "$LABEL_PREFIX" | awk "{printf \"  %-40s pid=%s status=%s\\n\", \$3, \$1, \$2}"
        echo ""
        echo "[status] recent logs (last 10):"
        ls -lt "$LOGS/"*.log 2>/dev/null | head -10 | awk "{printf \"  %s %s %s\\n\", \$6, \$7, \$9}"
        ;;
    *)
        echo "Usage: $0 {install|uninstall|status}"
        exit 1
        ;;
esac
