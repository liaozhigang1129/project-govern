---
status: accepted
supersedes: none
superseded_by: none
created: 2026-08-07
updated: 2026-08-07
summary: 文档采用 sift 风格 STATUS + WBS 双轨
---

# 003 · 文档规范采用 SIFT 风格 STATUS + WBS 双轨

> 📌 决策快照:[STATUS §5](../STATUS.md) 决策快照表
> 📂 引用关系:见 [decisions/README](README.md) 索引表


## 背景

老仓库 (`/Users/lzg/Documents/pmo-pms`) 的 docs 目录里:
- 顶层 4 个 doc(`README.md` / `CHANGELOG.md` / `RELEASE-NOTES-v4.0.0.md` / `docs/*.md`)把"项目现状 / 任务进度 / 实现细节 / 版本历史"全混在一起;
- `pmo-pms-requirements/` 31 个子目录 + `A5-上线计划/` + `项目经营台账/` 等过程文件散落;
- 没有任何"全局状态快照"机制,新代理 / 新评审读老仓库时只能从 `README.md` 一路猜。

参考 sift (`/Users/lzg/github/sift/docs/`) 的规范:AGENTS.md 只放指针、docs/README.md 做文档地图、PRD/DESIGN/WBS/CHANGELOG 各管一摊。sift 的 AGENTS.md 内嵌了"项目现状"段落,但该段落实质是"全局状态"信息,与 AGENTS.md 的"指针与规则"定位不符。

## 决定

1. **新增** `docs/STATUS.md` —— 全局项目计划执行情况的**单一事实来源**:
   - 回答"项目现在到哪了 / 在做什么 / 卡在哪";
   - 包含里程碑进度表、在制品、风险登记、关键决策指针、下一步门禁;
   - **不重复** WBS.md 的任务清单;
   - 更新节奏:每次里程碑 / 门禁评审后更新一次。
2. **精简** `docs/WBS.md` —— **只登记任务分解**:
   - 里程碑 → 工作包 → 任务 三级结构;
   - 工作包字段:`WP-ID / 名称 / 所属里程碑 / 前置依赖 / 验收标准 / Spec 指针 / Plan 指针`;
   - **不写**进度 / 状态 / 风险字段(去 STATUS.md);
   - **不写**实现步骤(去 plans/)。
3. **STATUS 与 CHANGELOG 边界**:
   - STATUS = 当前快照(像内存);
   - CHANGELOG = 版本间历史(像 log);
   - 二者不重复。
4. **AGENTS.md 同步精简** —— "项目现状"段下沉到 STATUS.md,AGENTS.md 只保留指针。

## 不采用的方案

- **方案 B:WBS 仍带 status 字段,STATUS 与 WBS 双重登记**
  缺点:同一事实两个地方写,易漂移,违反 sift 的"引用不复制"纪律。
- **方案 C:STATUS 每任务状态变化就更新**
  缺点:写入量爆炸,与代码 commit 频率绑定,失去"快照"语义。
- **方案 D:沿用 sift 写法,STATUS 段塞回 AGENTS.md**
  缺点:AGENTS.md 越来越长,违背"只放指针"原则。

## 影响

- 代理默认上下文集(见 docs/README.md §"上下文预算")不变:`status: active | draft` 才加载。
- 老 `docs/*.md`(`pmo-pms-proposal.md` 等)按 sift 规范下沉到 `specs/legacy/`,作为历史规格指针保留,不再被默认上下文加载。
- 本次会话产出:本 ADR(003) + `docs/STATUS.md` + `docs/WBS.md` 重写 + `AGENTS.md` 精简。
