#!/usr/bin/env bash
# docs-lint.sh — PMO·PMS 文档规范检查
#
# 校验项:
#   1. 文件头(--- status / created / summary ---)完整性
#      - docs/{PRD,DESIGN,WBS,STATUS,README}.md 必填 status
#      - docs/decisions/*.md 必填 status
#      - docs/specs/*.md / docs/plans/*.md / docs/guides/*.md / docs/runbooks/*.md
#        / docs/dev/*.md / docs/analysis/*.md / docs/testing/*.md 必填 status
#      - docs/drafts/** 与 docs/reviews/** 不强求(归档)
#   2. status 取值合法性(draft|active|done|abandoned|superseded)
#   3. STATUS.md ↔ WBS.md 双轨边界
#      - WBS.md 不写 `status:` 字段在工作包上
#      - STATUS.md 不出现 WBS 工作包命名空间(M<n>-<nn>)的展开
#   4. 相对链接(.md)目标存在性(局部检查:仅 docs/ 内)
#   5. CHANGELOG.md 必须含 [Unreleased] 段
#   6. decisions/ 编号必须从 001 起、按数字顺序、连续(允许间断但提示 warning)
#
# 退出码:
#   0 = 全部通过
#   1 = 有 error(必填缺失 / status 非法 / 死链接)
#   2 = 仅 warning(顺序 / 重复内容提示)
#
# 用法: bash scripts/docs-lint.sh [DOCS_DIR] [--quiet]

set -u
DOCS_DIR="${1:-docs}"
QUIET=0
[[ "${2:-}" == "--quiet" ]] && QUIET=1

if [[ ! -d "$DOCS_DIR" ]]; then
  echo "✗ docs 目录不存在: $DOCS_DIR" >&2
  exit 1
fi

# macOS bash 3.2 兼容:关闭 -u 对数组的处理
set +u

ERRORS=0
WARNINGS=0

_red()    { printf "\033[31m%s\033[0m" "$1"; }
_yellow() { printf "\033[33m%s\033[0m" "$1"; }
_green()  { printf "\033[32m%s\033[0m" "$1"; }
_bold()   { printf "\033[1m%s\033[0m" "$1"; }

_err()  { ((ERRORS++));   _red "✗"; printf " %s\n" "$1"; }
_warn() { ((WARNINGS++)); _yellow "⚠"; printf " %s\n" "$1"; }
_ok()   { [[ $QUIET -eq 0 ]] && _green "✓" && printf " %s\n" "$1"; }

# ---------- 工具函数 ----------

# 提取 front matter(--- 块)
get_front_matter() {
  local f="$1"
  awk 'BEGIN{in_fm=0} /^---$/{
    if(in_fm==0){in_fm=1; next}
    if(in_fm==1){in_fm=2; exit}
  } in_fm==1 {print}' "$f"
}

# 从 front matter 取字段值
fm_field() {
  local fm="$1" key="$2"
  printf "%s\n" "$fm" | awk -F': *' -v k="$key" '$1==k {sub(/^[^:]*: */,""); sub(/^["'\'' ]+|["'\'' ]+$/,""); print; exit}'
}

# 判断文件是否必须带 status
requires_status() {
  local rel="$1"
  case "$rel" in
    PRD.md|DESIGN.md|WBS.md|STATUS.md|CHANGELOG.md|README.md) return 0;;
    decisions/*.md) return 0;;
    specs/*.md) return 0;;
    plans/*.md) return 0;;
    testing/*.md) return 0;;
    analysis/*.md) return 0;;
    guides/*.md) return 0;;
    runbooks/*.md) return 0;;
    dev/*.md) return 0;;
    # drafts/** 与 reviews/** 归档,不强求
  esac
  return 1
}

# 从 markdown 提取所有相对 .md 链接
extract_md_links() {
  local f="$1"
  # 形式:](xxx.md) 或 [xxx](xxx.md)
  grep -oE '\]\([^)]+\.md[^)]*\)' "$f" 2>/dev/null | sed -E 's/^\]\(//; s/\)$//; s/#.*$//' | sort -u
}

# ---------- 1. 文件头校验 ----------

_bold "→ 校验文件头(front matter)\n"

ALL_MD=()
while IFS= read -r line; do
  ALL_MD+=("$line")
done < <(find "$DOCS_DIR" -type f -name '*.md' -not -path '*/node_modules/*' | sort)

for f in "${ALL_MD[@]}"; do
  rel="${f#./}"
  rel="${rel#./}"

  if ! requires_status "$rel"; then
    continue
  fi

  fm=$(get_front_matter "$f")
  if [[ -z "$fm" ]]; then
    _err "$rel: 缺文件头(--- ... ---)"
    continue
  fi

  status=$(fm_field "$fm" status)
  if [[ -z "$status" ]]; then
    _err "$rel: front matter 缺 status 字段"
    continue
  fi

  case "$status" in
    draft|active|done|abandoned|superseded) ;;
    *) _err "$rel: status 取值非法 '$status'(须为 draft|active|done|abandoned|superseded)";;
  esac

  summary=$(fm_field "$fm" summary)
  if [[ -z "$summary" && "$rel" != "README.md" && "$rel" != "CHANGELOG.md" ]]; then
    _warn "$rel: front matter 缺 summary 字段(30 字内一句话说明)"
  fi
done

# ---------- 2. STATUS ↔ WBS 双轨边界 ----------

_bold "\n→ 校验 STATUS.md ↔ WBS.md 双轨边界\n"

STATUS_FILE="$DOCS_DIR/STATUS.md"
WBS_FILE="$DOCS_DIR/WBS.md"

if [[ -f "$WBS_FILE" ]]; then
  # 检查 WBS 工作包上是否写了 status 字段(只查工作包块,不查文件头)
  if awk '/^### WP-/{flag=1; next} /^### /{flag=0} flag && /^status:/{print FILENAME":"NR":"$0}' "$WBS_FILE" | grep -q .; then
    _err "WBS.md: 工作包(### WP-)上不应带 status 字段(去 STATUS.md,见 ADR 003)"
  else
    _ok "WBS.md: 工作包未带 status 字段 ✓"
  fi
fi

if [[ -f "$STATUS_FILE" ]]; then
  # STATUS.md 头部应声明"不重复 WBS 任务结构"
  if ! grep -q "WBS" "$STATUS_FILE"; then
    _warn "STATUS.md: 头部未引用 WBS.md(应明确边界)"
  else
    _ok "STATUS.md: 头部已声明 WBS 边界 ✓"
  fi
fi

# ---------- 3. 相对链接目标存在性 ----------

_bold "\n→ 校验相对 .md 链接目标存在性\n"

for f in "${ALL_MD[@]}"; do
  dir=$(dirname "$f")
  rel="${f#./}"

  # drafts/ 与 specs/legacy/ 为历史归档,不做链接校验
  case "$rel" in
    docs/drafts/*) continue ;;
    docs/specs/legacy/*) continue ;;
  esac

  while IFS= read -r link; do
    # 跳过绝对 URL / 锚 / 邮件 / 占位符 NNN-xxx.md(规范示例)
    [[ "$link" =~ ^https?:// ]] && continue
    [[ "$link" =~ ^# ]] && continue
    [[ "$link" =~ ^mailto: ]] && continue
    [[ "$link" =~ ^NNN- ]] && continue
    [[ -z "$link" ]] && continue

    target="$dir/$link"
    if [[ ! -f "$target" && ! -d "$target" ]]; then
      _err "${f#./}: 死链接 [$link] -> $target"
    fi
  done < <(extract_md_links "$f")
done

# ---------- 4. CHANGELOG.md 必有 [Unreleased] ----------

_bold "\n→ 校验 CHANGELOG.md 含 [Unreleased] 段\n"

CHANGELOG_FILE="$DOCS_DIR/CHANGELOG.md"
if [[ -f "$CHANGELOG_FILE" ]]; then
  if grep -q '^## \[Unreleased\]' "$CHANGELOG_FILE"; then
    _ok "CHANGELOG.md 含 [Unreleased] 段 ✓"
  else
    _warn "CHANGELOG.md 缺 ## [Unreleased] 段(规范要求)"
  fi
else
  _err "CHANGELOG.md 不存在"
fi

# ---------- 5. decisions/ 编号顺序 ----------

_bold "\n→ 校验 decisions/ 编号顺序\n"

if [[ -d "$DOCS_DIR/decisions" ]]; then
  ADR_FILES=()
  while IFS= read -r line; do
    ADR_FILES+=("$line")
  done < <(find "$DOCS_DIR/decisions" -maxdepth 1 -type f -name '*.md' | sort)
  prev_num=0
  for f in "${ADR_FILES[@]}"; do
    base=$(basename "$f")
    if [[ "$base" =~ ^([0-9]{3})- ]]; then
      num=$((10#${BASH_REMATCH[1]}))
      if (( num <= prev_num )); then
        _warn "decisions/$base: 编号 $num 不大于前一条 $prev_num"
      elif (( num != prev_num + 1 )); then
        _warn "decisions/$base: 编号 $num 与前一条 $prev_num 不连续(允许间断)"
      fi
      prev_num=$num
    else
      _warn "decisions/$base: 文件名不符合 NNN-xxx.md 规范"
    fi
  done
  _ok "decisions/ 已扫描 ${#ADR_FILES[@]} 份 ADR"
fi

# ---------- 汇总 ----------

_bold "\n→ 汇总\n"
if (( ERRORS == 0 && WARNINGS == 0 )); then
  _green "✓ docs-lint 全绿(无 error / 无 warning)\n"
  exit 0
elif (( ERRORS == 0 )); then
  _yellow "⚠ docs-lint 完成:$WARNINGS warning,0 error\n"
  exit 2
else
  _red "✗ docs-lint 失败:$ERRORS error,$WARNINGS warning\n"
  exit 1
fi
