---
status: proposed
created: 2026-08-07
updated: 2026-08-07
summary: 中文精简版 ADR 模板(快速填写,5 字段 5 分钟落地)
---

# _mini · 中文精简 ADR 模板

> 用于快速决策(小型变更 / 流程调整),不走完整评审。
> 大型决策(技术栈变更 / 架构推翻)请用 [`_template.md`](_template.md)。

---

## 5 字段填写指南

```yaml
---
status: proposed  # proposed → accepted/rejected 流程评审后改
created: YYYY-MM-DD
updated: YYYY-MM-DD
summary: 一句话说明(30 字内)
supersedes: NNN|none  # 被推翻的旧 ADR 编号,none 表示无
superseded_by: NNN|none  # 未来推翻本 ADR 的编号,none 表示暂无
---
```

## 正文最小骨架

```markdown
# NNN · 决策标题(动名词短语)

> 📌 决策快照:[STATUS §5](../STATUS.md) 决策快照表
> 📂 引用关系:见 [decisions/README](README.md)

## 1. 背景(2-3 句,为什么)

## 2. 决策(1-2 段,我们决定什么)

## 3. 候选对比(至少 2 个 option,简表)

| 维度 | 选项 A | 选项 B ⭐ |
|---|---|---|
| 工作量 | x 人天 | y 人天 |
| 风险 | 低 | 中 |
| 长期维护 | 高 | 低 |

## 4. 后果(正面 ✅ + 负面 ❌)

## 5. 关联(WBS / Plan / analysis 链接)

## 评审记录

| 日期 | 评审人 | 意见 |
|---|---|---|
| YYYY-MM-DD | PMO | 通过 |
```

---

## 与 `_template.md` 的差异

| 维度 | `_template.md` (完整) | `_mini.md` (精简) |
|---|---|---|
| 字数 | ~200 行 | ~50 行 |
| 候选方案数 | 3 个 (A/B/C) | 2 个 (A/B) |
| 适用场景 | 技术栈 / 架构推翻 / 流程变更 | 小决策 / 局部调整 |
| 评审门槛 | PMO + 架构组双签 | PMO 单签 |
| 提交前必填 | 全部 7 节 | 5 节 (简略背景/决策/对比/后果/关联) |

---

## 填写示例(虚构 ADR-005)

```markdown
---
status: proposed
created: 2026-08-15
updated: 2026-08-15
summary: 前端构建从 Vite 5 升级到 Vite 6
supersedes: none
superseded_by: none
---

# 005 · 前端构建升级 Vite 5 → Vite 6

> 📌 ...

## 1. 背景
Vite 5 已发布 18 个月,EOL 在 2026-Q4,需提前升级到 6.x。

## 2. 决策
前端构建工具升级到 Vite 6.x,保持 Vue 3.5 + Element Plus 2.8 不变。

## 3. 候选对比
| 维度 | A. 维持 Vite 5 | B. 升级 Vite 6 ⭐ |
|---|---|---|
| 工作量 | 0 | 2 人天 |
| 风险 | 高(无安全补丁) | 低(官方升级指南) |
| 长期 | 不可持续 | 与生态同步 |

## 4. 后果
- ✅ Vite 6 性能提升 ~15%
- ❌ 需回归测试现有 33 个前端页面

## 5. 关联
- WBS: WP-INFRA-02 (CI 4 jobs)
- ADR-001 (基线约束 Vue 3.5 不变)
```
