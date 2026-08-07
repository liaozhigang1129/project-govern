# V3.x + V4.x 累积改动 — 14-commit 拆分计划

> 由本次会话自动生成(2026-06-13),把 `git status` 里 195 个未提交文件
> 按模块语义拆分成 14 个原子 commit。每个 commit 文件清单见下表。

## 用法

```bash
# 1. 先 dry-run 看每个 commit 会包含哪些文件
bash scripts/commit-all-14.sh dry-run

# 2. 满意后正式执行(按 c1→c14 顺序)
bash scripts/commit-all-14.sh

# 3. 单个或子集(例如先做 c1 试试)
bash scripts/commit-all-14.sh dry-run 1
bash scripts/commit-all-14.sh 1

# 4. 跳过某些 commit(例如跳过 c14 docs)
bash scripts/commit-all-14.sh 1 2 3 4 5 6 7 8 9 10 11 12 13
```

## 14-commit 拆分表

| #  | 标题                                                          | 文件数 | 类型             |
|----|---------------------------------------------------------------|--------|------------------|
| c1 | feat(milestone): V3.1 七阶段字典 + 4 端点分析                 | 14     | feat             |
| c2 | feat(cost): V4.0 工时→成本引擎 + 角色档 + 视图 (P0-A)         | 6      | **feat** ⭐      |
| c3 | feat(finance): V4.2 合同/发票/付款/成本项                      | 2      | feat             |
| c4 | feat(alert): V4.3 预警实体 + 仓库 + 6 种子规则                 | 3      | feat (半成品)    |
| c5 | feat(wbs): P3 WBS 任务拆解 + EVM + 网络图 + 甘特              | 17     | feat             |
| c6 | feat(initiation): V3.0 立项全流程 + 5 子模块                  | 23     | feat             |
| c7 | feat(org): V2.8/V2.9 用户/部门/角色 三类 AdminController      | 19     | feat             |
| c8 | feat(notification): P2 多通道 IM + 4 事件                     | 10     | feat             |
| c9 | feat(timesheet+workload+project): V2.11-V2.13 增强            | 20     | feat             |
| c10| feat(risk): V2.6/V2.7 风险矩阵 + 历史快照                      | 4      | feat             |
| c11| feat(frontend): V3.x + V4.x 配套 UI                           | 45     | feat             |
| c12| chore(infra): pom + application*.yml + Jwt + test schema      | 10     | chore            |
| c13| feat(cross-module): MySQL 迁移 + admin/dingtalk/tools         | 12     | feat             |
| c14| chore(repo): Makefile + scripts + docs + PRD + uploads        | 11     | chore            |

**总计**: 196 个文件,14 个原子 commit(注:含目录路径,与 git status --short 行数 195 略有差异是因为部分以 `/` 结尾的目录)

## 顺序说明

按**依赖关系**排序:
- c12 (infra) 和 c7/c8 (org/notification) 是其他模块的前置
- c5/c9/c10 互相独立可任意顺序
- c11 (frontend) 依赖 c5/c6/c7/c9/c10 的 API,但作为整批可放最后
- c14 (chore) 放最后

## 文件清单

每个 commit 的精确文件列表见同目录 `c{1..14}.txt`(本目录自带)。

## ⚠️ 注意事项

1. **`3}` 文件**:误建的文件名,在 c14 里。建议手动 `rm -rf 3}` 后再 commit
2. **`uploads/`**:开发产物,建议加进 `.gitignore`,但目前未忽略
3. **`zhiyu-requirements/P3-*`**:文档型,可能二进制,确认后保留
4. **`P2.C-gantt-axis-fix.md`**:历史修复记录,可与 docs 合并
5. **MySQL 迁移滞后**:c13 里包含 V2.12/V2.13/V3.1/V4.1-V4.4,但实际上 MySQL 目录还没建这些 SQL;
   当前 c13.txt 只列了 V2.5-V4.0 的 MySQL SQL,后续补 MySQL 端 SQL 时单独再 commit

## 后续 commit 路径

如果你不想跑 14 个 commit,也可以:
- 跑 c1-c11 后停(c14 docs/uploads 后续手动处理)
- 或把 c1-c11 进一步合并为 4-5 个大 commit