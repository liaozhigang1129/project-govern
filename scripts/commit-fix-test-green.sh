#!/usr/bin/env bash
# 用法: bash scripts/commit-fix-test-green.sh
# 前提:已经 git add 了 8 个修复文件 (见 .gitmessage-fix-test-green.txt)
set -euo pipefail
cd "$(dirname "$0")/.."

# 1. 检查 staged 文件清单
echo "=== Staged files ==="
git diff --cached --name-only
echo ""

EXPECTED_FILES=(
  "backend/src/main/java/com/company/pmo/common/exception/BusinessException.java"
  "backend/src/main/java/com/company/pmo/module/alert/AlertEvent.java"
  "backend/src/main/java/com/company/pmo/module/alert/AlertRule.java"
  "backend/src/main/resources/db/migration-pg/V4.4__alert_audit_columns.sql"
  "backend/src/test/java/com/company/pmo/common/security/RequireRolesTest.java"
  "backend/src/test/java/com/company/pmo/module/healthadvisor/HealthAdvisorServiceTest.java"
  "backend/src/test/java/com/company/pmo/module/milestone/MilestoneCreateRequestContractTest.java"
  "backend/src/test/java/com/company/pmo/module/milestone/MilestoneRepositoryProgressTest.java"
)

for f in "${EXPECTED_FILES[@]}"; do
  if ! git diff --cached --name-only | grep -qx "$f"; then
    echo "❌ MISSING in staged: $f"
    exit 1
  fi
done
echo "✅ All 8 expected files staged"
echo ""

# 2. 干跑 mvn test 确认全绿 (可选, 30 秒)
if [ "${SKIP_TEST:-0}" != "1" ]; then
  echo "=== Running mvn clean test (skippable via SKIP_TEST=1) ==="
  (cd backend && mvn clean test -q -Djacoco.skip=true) || {
    echo "❌ Tests still failing, abort commit"; exit 1;
  }
  echo ""
fi

# 3. Commit
git commit -F .gitmessage-fix-test-green.txt
echo ""
echo "✅ Committed: fix(test): mvn clean test 全绿"
echo ""
git log --oneline -3
