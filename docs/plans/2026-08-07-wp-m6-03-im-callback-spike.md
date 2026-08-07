---
status: active
created: 2026-08-07
updated: 2026-08-07
summary: WP-M6-03 IM 平台 OAuth + 卡片回调接入技术 spike 评估
---

# Plan · WP-M6-03 IM 平台回调接入评估

> 对应 WBS 工作包:[`WP-M6-03`](../WBS.md#wp-m6-03-im-平台回调接入评估)
> 对应里程碑:**M6**(IM 通知多通道)
> 对应 ADR:—
> 当前状态:**paused**(2026-08-07 启动 spike)
> 阻塞项:R-002 IM 平台 OAuth 工作量未评估

---

## 1. 目标与范围

### 1.1 一句话

对 IM 平台(企业微信 / 钉钉 / 飞书)的 **OAuth 授权 + 卡片回调**接入做一次技术 spike,产出"是否进 M6 范围 / 工作量估算 / 风险评估"的报告,供 PMO 决策。

### 1.2 范围内

- 三平台 OAuth 2.0 接入流程对比
- 卡片回调(审批按钮 / 状态回写)的实现路径
- 自建回调服务的最小可用架构(Spring Boot Webhook + 签名校验 + 重放保护)
- 失败处理(平台限流 / 证书轮换 / 灰度回滚)
- 工作量估算(人天)
- 决策建议:**进 M6 范围 / 推迟到 v5 / 砍掉**

### 1.3 出范围

- 真做 IM OAuth 接入(这是 spike,不是实现)
- IM 平台商业方案对比(假设三平台自建应用都可行)
- 移动端 IM SDK 集成(留 v5)

---

## 2. spike 步骤

### T-01 三平台 OAuth 流程对比(半天)

- 写 `docs/analysis/2026-08-07-im-oauth-flow-comparison.md`:
  - 企业微信:`https://developer.work.weixin.qq.com/document/path/91039`
  - 钉钉:`https://open.dingtalk.com/document/orgapp/obtain-identity-credentials`
  - 飞书:`https://open.feishu.cn/document/server-docs/authentication-management/access-token`
- 对比维度:`grant_type` 支持 / token 有效期 / refresh 机制 / 回调 IP 白名单 / 签名算法
- 表格化输出

### T-02 卡片回调可行性(半天)

- 调研三平台"卡片交互回调":
  - 企业微信:`https://developer.work.weixin.qq.com/document/path/90240`
  - 钉钉:`https://open.dingtalk.com/document/orgapp/robot-interactive-cards`
  - 飞书:`https://open.feishu.cn/document/uAjLw4CM/ukzMukzMukzM/feishu-cards/card-interactive-callback`
- 关键问题:
  - 回调 URL 必须公网 HTTPS(自建服务要域名 + SSL 证书)
  - 签名校验算法各自不同(企业微信 SHA1 / 钉钉 HMAC-SHA256 / 飞书 HMAC-SHA256)
  - 卡片按钮触发后回调响应必须 5s 内返回
  - 卡片"被动更新"能力(用于状态变更)

### T-03 最小可用架构设计(半天)

- 画一张图:Spring Boot Webhook 端点 → 签名校验 → 幂等键(Redis)→ 业务回调 → 卡片更新
- 关键组件:
  - `ImCallbackController` 3 平台统一入口(`/api/im/callback/{platform}`)
  - `ImSignatureVerifier` SPI,3 平台各自实现
  - `ImCallbackProcessor` 业务路由(approval_card → ApprovalService / status_card → ...)
  - `ImCardUpdater` 异步回调更新卡片
- 失败处理:5s 超时降级 / 重试 3 次 / 死信队列

### T-04 工作量估算(半天)

| 模块 | 工作量(人天) | 备注 |
|---|---|---|
| 三平台 OAuth 接入 | 5 | 各 1.5 天 + 公用 0.5 |
| 卡片回调签名校验 | 2 | 三平台签名算法不同 |
| 业务回调处理 | 3 | 审批 / 状态两类卡片 |
| 卡片异步更新 | 2 | |
| 灰度开关 + 回滚 | 1 | |
| 单测 + 集成测试 | 3 | |
| 文档 + ADR | 1 | |
| **合计** | **17 人天** | ≈ 3.5 周(1 人全职) |

### T-05 决策建议(半天)

- 写 `docs/analysis/2026-08-07-im-oauth-decision-proposal.md`:
  - **选项 A(进 M6)**:本期做,17 人天,M6 门禁延期 4 周
  - **选项 B(推迟 v5)**:本期不动,v5 立项时一并评估(AI / 移动 / IM OAuth)
  - **选项 C(砍掉)**:M6 维持现状(单向通知,无卡片交互),所有审批仍在 web 内完成
- 推荐:**选项 B**(推迟 v5)
  - 理由:当前 IM 单向通知 + Web 审批已可用;M6 范围里增加 17 人天换"卡片一键审批"ROI 不高;v5 立项时一并评估,可能 AI 一起做(智能提醒 / 自动审批)更有价值
  - 风险:M6 范围缩水,需 PMO 接受

---

## 3. 验收标准

### 3.1 spike 完成(必过)

| 项 | 标准 |
|---|---|
| 三平台 OAuth 对比文档 | `docs/analysis/2026-08-07-im-oauth-flow-comparison.md` 落地 |
| 卡片回调可行性文档 | 包含在三平台对比文档里 |
| 最小架构图 | Mermaid / PlantUML 形式,代码评审通过 |
| 工作量估算 | 按模块细化到人天 |
| 决策建议文档 | `docs/analysis/2026-08-07-im-oauth-decision-proposal.md` 落地,三选项分析 |
| docs-lint | 通过 |

### 3.2 不实现

- spike 不写代码,只写文档
- 不创建分支 / 不动代码

---

## 4. 风险

| ID | 风险 | 缓解 |
|---|---|---|
| R-002 | IM 平台 OAuth 工作量未评估 | 本 spike 关闭 R-002(产出决策建议) |
| R-009 | spike 报告被 PMO 否决,推翻 B 选项 | 在 ADR 里写明推翻路径 |

---

## 5. 进度节点(预估)

| 节点 | 日期 |
|---|---|
| T-01 OAuth 流程对比 | 2026-08-08 |
| T-02 卡片回调调研 | 2026-08-08 |
| T-03 最小架构设计 | 2026-08-09 |
| T-04 工作量估算 | 2026-08-09 |
| T-05 决策建议 | 2026-08-10 |
| **PMO 评审** | **2026-08-12** |

---

## 6. 完成后处置

- WBS.md:WP-M6-03 `Plan:` 填本文件名
- STATUS.md:R-002 关闭;根据 PMO 决定更新 M6 范围(可能延期或砍掉)
- CHANGELOG.md:不追加 spike(只是评估,无功能变更)
- ADR:若 spike 决策落地为"砍掉"或"推迟",追加 `004-im-callback-deferred.md`
- `docs/analysis/` 下两份分析文档永久保留,作为后续 v5 立项的输入
