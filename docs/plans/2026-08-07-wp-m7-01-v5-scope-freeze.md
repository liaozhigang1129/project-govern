---
status: active
created: 2026-08-07
updated: 2026-08-07
summary: WP-M7-01 v5 立项评审 — AI/移动/治理三轴范围冻结与关键决策
---

# Plan · WP-M7-01 v5 立项评审

> 对应 WBS 工作包:[`WP-M7-01`](../WBS.md#wp-m7-01-v5-立项评审)
> 对应里程碑:**M7**(v5 立项:AI·移动·治理)
> 对应 ADR:本评审将产出 **ADR-005 v5 范围与关键决策**
> 当前状态:**active**(2026-08-07 启动评审)
> 阻塞项:无

---

## 1. 目标与范围

### 1.1 一句话

召开 v5 立项评审会,**冻结 v5 范围**,对 AI / 移动 / 治理三轴的 **8 项关键决策** 拍板,产出 ADR-005,启动 M7 实施。

### 1.2 范围内

- 评审草案来源 [`drafts/扩展文档/P3plus-v2-立项/P3plus-PR-0-项目索引与总览.md`](../drafts/扩展文档/P3plus-v2-立项/P3plus-PR-0-项目索引与总览.md)(8 篇 / 2640 行)
- 评审激活后的 active spec:
  - [`reporting.md`](../specs/reporting.md) — 报表 / BI / 导出
  - [`reporting-api.md`](../specs/reporting-api.md) — 报表 API 契约
  - [`mobile-h5.md`](../specs/mobile-h5.md) — 移动端 H5
- 8 项关键决策拍板(详见 §4)
- 10 周里程碑细化(详见 §5)
- 风险登记与缓解(详见 §6)

### 1.3 出范围

- M7 实施(Sprint 0 准备):本 plan 只到范围冻结
- 数据模型增量(WP-M7-02):本 plan 只评审,不实施
- AI 模型自研(已决策:集成第三方):不在本评审展开

---

## 2. 评审会议安排

### 2.1 时间与形式

- **形式**:异步评审(评审期内收集意见)+ 1 次 60 min 整合会议
- **建议时长**:D+1 ~ D+7 共 7 个工作日(对应 P3+ v2 草案 §4.2 节奏)
- **整合会议**:D+7 下午,所有评审角色必到

### 2.2 评审分工

| 角色 | 必评 | 可选 |
|---|---|---|
| **PMO** | 本 plan / [reporting.md](../specs/reporting.md) / [mobile-h5.md](../specs/mobile-h5.md) | — |
| **Sponsor** | 本 plan(决策项) | — |
| **架构师** | 本 plan / [reporting-api.md](../specs/reporting-api.md) | — |
| **后端** | [reporting-api.md](../specs/reporting-api.md) | 本 plan |
| **前端** | [mobile-h5.md](../specs/mobile-h5.md) | [reporting.md](../specs/reporting.md) |
| **QA** | 本 plan(门禁/度量) | — |
| **AI 工程师** | [reporting.md](../specs/reporting.md) §3 §4(导出/自助 BI) | — |
| **SRE** | 本 plan(部署/SLA) | — |
| **安全/合规** | [mobile-h5.md](../specs/mobile-h5.md) §10(隐私安全) | — |

### 2.3 评审产出

| 产出 | 形式 | 负责人 |
|---|---|---|
| 评审意见汇总 | `docs/reviews/2026-08-07-wp-m7-01-review.md` | PMO |
| ADR-005 v5 范围与关键决策 | `docs/decisions/005-m7-v5-scope.md` | 架构师 |
| 更新 WBS.md M7 状态 | edit | PMO |
| 更新 STATUS.md M7 → active | edit | PMO |

---

## 3. v5 范围(三轴全景)

### 3.1 AI 轴(WP-M7-03)

- ✅ **里程碑 AI 预测增强**(ML 模型已就位,见 [STATUS.md §1 M7 当前快照](../STATUS.md))
- 🆕 **自助 BI / 报表 / 导出**(对应 [reporting.md](../specs/reporting.md))
- � **NLP 智能报告生成**(本期**不实现**,预留接口)

### 3.2 移动轴(WP-M7-03 子模块)

- ✅ **Web H5 移动端**(对应 [mobile-h5.md](../specs/mobile-h5.md))
- 🆕 **离线 IndexedDB 缓存**(本期实现)
- 🆕 **扫码 @任务**(本期实现)
- ❌ **原生 App**(本期**不做**,价值验证后 v5.1 再评估)

### 3.3 治理轴(WP-M7-04)

- ✅ **8 角色化仪表盘**(对应 [reporting.md §1](../specs/reporting.md#1-角色化仪表盘8-角色--9-内容域))
- ✅ **9 类报表**(对应 [reporting.md §2](../specs/reporting.md#2-报表类型9-大类))
- ✅ **导出服务**(PDF / Excel / CSV / PNG,异步任务)
- 🆕 **数据质量治理看板**(对应 [reporting.md §5](../specs/reporting.md#5-数据质量治理))

### 3.4 不进 v5 范围(Out of Scope)

| 项 | 原因 | 何时评估 |
|---|---|---|
| 多租户 | P3+ v2 草案 §5.3 决策"共享库 + 行级隔离"涉及 schema 重大改造;成本与价值不匹配 | v6 |
| ERP 集成 | 需另立项评估(API + Kafka 事件 vs ETL) | v5.1 |
| NLP 智能报告 | LLM 集成依赖未稳定 | v5.2 |
| 数据出境合规 | 草案 §5.5 决策"不出境",无需本期动作 | — |
| 原生 App | H5 验证后再定 | v5.1 |

---

## 4. 关键决策(8 项,Sponsor 拍板)

### D1:AI 模型形态 — **集成第三方(已确认)**

- 选项 A:**集成第三方**(阿里通义/百度文心) — ✅ 采纳
- 选项 B:自研 LLM
- 决策依据:投入产出比、数据合规、迭代速度

### D2:AI 预测上线策略 — **影子模式 4 周 → 灰度 → 全量**(已确认)

- 选项 A:影子模式 4 周(ML 模型后台运行,不直接影响用户决策),对比基线准确率 ≥ 85% 才灰度 — ✅ 采纳
- 选项 B:直接全量上线
- 决策依据:风险可控、可回滚、可解释

### D3:移动 App 形态 — **Hybrid + PWA(本期调整为 H5)**

- 选项 A:**Web H5 + 响应式**(本期)— ✅ 采纳
  - 理由:无需 App Store 上架,迭代最快,跨平台
- 选项 B:Hybrid(Ionic / Cordova)封装
- 选项 C:Native(iOS / Android)双端
- 决策依据:H5 在 iPhone / Android 性能已满足工时填报场景;App 商店审核 + 原生开发成本不匹配 v5 阶段价值验证

### D4:报表/导出实现路径 — **后端聚合 + ECharts + 4 格式导出**(本期)

- 选项 A:**后端聚合 API + 前端 ECharts + 后端导出(4 格式)** — ✅ 采纳
  - 导出 PDF:OpenPDF + Thymeleaf 模板
  - 导出 Excel:Apache POI 流式(sxssf)
  - 导出 CSV:UTF-8 BOM
  - 导出 PNG:ECharts server-side render
- 选项 B:嵌入第三方 BI(Tableau / Power BI / 帆软)
- 决策依据:成本可控、技术栈一致、无第三方依赖

### D5:数据集查询策略 — **预聚合 + 物化,禁止实时跨表 JOIN**

- 选项 A:**预聚合 + 物化视图**(本期)— ✅ 采纳
- 选项 B:实时跨表 JOIN
- 决策依据:数据量增长后性能可控

### D6:订阅分发策略 — **定时 + IM + Email,3 通道并行**

- 选项 A:**Email + IM + 链接分享**(本期)— ✅ 采纳
- 选项 B:仅 Email
- 选项 C:仅 IM
- 决策依据:覆盖 PMO / PM / 财务 / 部门负责人 不同角色

### D7:数据安全策略 — **导出加密 + 用户水印 + TTL 24h**

- ✅ 已与 [reporting.md §6 业务规则](../specs/reporting.md#6-业务规则强制)对齐

### D8:阶段验收门禁 — **新增 6 项 v5 专项门禁**

| 门禁 | 阈值 | 评审 |
|---|---|---|
| 角色仪表盘覆盖率 | 8 角色 × 默认 dashboard | PMO |
| 报表模板落地数 | ≥ 5 类(状态/EVM/资源/风险/预算) | PMO |
| 导出 SLA | 异步任务 < 60s 完成 | SRE |
| H5 性能 | LCP < 2.5s / FID < 100ms | QA |
| H5 A11y | axe-core 通过 | QA |
| 数据质量看板 3 指标 | 完整率/准确率/时效性 可视化 | PMO |

---

## 5. 10 周里程碑(细化)

| 周 | 节点 | 交付 | 评审 | 状态 |
|:---:|---|---|---|---|
| **W1** | **WP-M7-01 立项评审(本周)** | ADR-005 + 范围冻结 | Sponsor+PMO+架构+DBA+QA+SRE+AI+安全 | 🟡 active |
| W2 | WP-M7-02 数据模型增量 | 8 新表 + 6 表扩展 + 5 状态机 | DBA + 后端 | ⏸ draft |
| W3 | WP-M7-03 报表+导出(后端) | Dashboard/Report/Export API + 导出服务 | 后端 + QA | ⏸ draft |
| W4 | WP-M7-03 报表(前端 ECharts) | 角色仪表盘 + 9 类报表前端 | 前端 + QA | � draft |
| W5 | WP-M7-03 移动端 H5 | 4 Tab + 周视图 + 扫码 + 离线 | 前端 + QA | ⏸ draft |
| W6 | WP-M7-04 数据质量看板 | 3 指标 + 异常检测 + Alert 集成 | 后端 + PMO | ⏸ draft |
| W7 | 自测 + 集成测试 | 95 用例 + 集成测试 | QA | ⏸ draft |
| W8 | 性能压测 + 安全 | JMeter + axe-core + ZAP | SRE + QA + 安全 | ⏸ draft |
| W9 | Staging 演练 + 灰度 | AI 影子模式 + 10% 流量灰度 | SRE + AI | � draft |
| W10 | 全量发布 + 收官 | 100% 流量 + 收官评审 | 全体 | ⏸ draft |

---

## 6. 风险登记(Top 5)

| # | 风险 | 概率 | 影响 | 缓解 |
|:---:|---|:---:|:---:|---|
| R-M7-01 | AI 预测上线后误判导致错误决策 | 中 | 高 | 影子模式 4 周 + 准确率 ≥ 85% 才灰度 |
| R-M7-02 | 报表 SQL 性能瓶颈 | 中 | 高 | 预聚合 + 物化;禁止实时跨表 JOIN |
| R-M7-03 | 导出大数据量内存溢出 | 中 | 高 | 强制流式 + 异步任务,产物存对象存储 |
| R-M7-04 | H5 离线数据冲突 | 高 | 中 | CRDT 协议 + 冲突日志 + 人工复核 |
| R-M7-05 | 第三方 LLM 数据出境合规 | 中 | 高 | 本期不引入第三方 LLM(用自研 ML);预留合规审计 |

---

## 7. 北极星指标(v5 上线 90 天后)

- **PM 月报产出耗时** 从 30min → **≤ 5min**(智能报告)
- **预算执行偏差** 从 ±5% → **≤ ±3%**(AI 预测预警)
- **风险事件提前发现** 从 0 → **≥ 30%**(智能根因)
- **移动端工时填报率** 从 70% → **≥ 95%**(H5 + 离线 + 提醒)
- **报表导出任务 SLA** ≥ 99.5%(异步任务 < 60s 完成)

---

## 8. 任务清单(本 plan 内)

### T-01 召集评审角色,分发本 plan 与 3 份 spec
- 输出:`docs/reviews/2026-08-07-wp-m7-01-review.md`(模板 + 评审意见汇总)
- 负责:PMO
- 完成时间:D+1

### T-02 评审 [reporting.md](../specs/reporting.md)
- 重点:§3 自助 BI、§4 导出格式、§5 数据质量
- 负责:PMO + 前端 + AI 工程师
- 完成时间:D+2

### T-03 评审 [reporting-api.md](../specs/reporting-api.md)
- 重点:§6 业务规则、§7 与现有契约关系
- 负责:后端 + 架构师
- 完成时间:D+2

### T-04 评审 [mobile-h5.md](../specs/mobile-h5.md)
- 重点:§9 业务规则、§10 隐私与安全、§13 验收标准
- 负责:前端 + QA + 安全/合规
- 完成时间:D+3

### T-05 评审本 plan §4 8 项决策
- 重点:D3 移动形态(关键)、D4 报表实现路径、D8 验收门禁
- 负责:Sponsor + PMO
- 完成时间:D+4

### T-06 起草 ADR-005
- 模板见 [`docs/decisions/README.md`](../decisions/README.md)
- 负责:架构师 + PMO
- 完成时间:D+5

### T-07 整合会议 + 拍板
- 所有评审角色必到
- 负责:Sponsor
- 完成时间:**D+7**

### T-08 更新 WBS / STATUS / CHANGELOG
- WBS.md:WP-M7-01 状态 → ✅ done
- STATUS.md:M7 → active 100%
- CHANGELOG.md:追加 M7 立项条目
- 负责:PMO
- 完成时间:**D+7 整合会议后**

---

## 9. 关联文档

- 上游:[`drafts/扩展文档/P3plus-v2-立项/P3plus-PR-0-项目索引与总览.md`](../drafts/扩展文档/P3plus-v2-立项/P3plus-PR-0-项目索引与总览.md)(8 篇草案)
- 下游:
  - WP-M7-02 数据模型(待启动)
  - WP-M7-03 v5 核心功能(待启动)
  - WP-M7-04 可视化与 AI 看板(待启动)
- ADR:**ADR-005 v5 范围与关键决策**(待产出)
