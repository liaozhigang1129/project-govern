# P2-A IM 通知中心设计文档

> 版本: v1.0
> 日期: 2026-06-07
> 范围: T2.1 通知中心 IM 卡片化
> 状态: ✅ 已完成代码实现 + 单测,待灰度开关

---

## 1. 一句话

通知中心新增 **企业微信 / 钉钉 / 飞书** 三大 IM 通道,实现"通道无关"的消息分发,
用户-IM 绑定独立表,通道总开关 + 路由策略配置化,实现 **零侵入灰度**(默认全关)。

---

## 2. 背景与目标

### 2.1 背景
P1.5 收尾时已实现:
- 通知持久化(`notification` 表,铃铛/分页/已读)
- 邮件通道(MailService)
- 3 个事件 + NotificationListener

但 PM/PMO 实际工作在 IM 内,邮件触达率与体验均不足。

### 2.2 目标
1. **多通道支持**:立项审批事件实时推送到 IM(企业微信/钉钉/飞书)
2. **零侵入**:IM 通道关闭时,系统行为与 P1.5 完全一致
3. **可灰度**:通道粒度独立开关(企业微信开,钉钉关也 OK)
4. **可观测**:发送成功/失败/未绑定均有 warn/info log
5. **失败隔离**:任一通道异常不影响其他通道,不影响主业务

### 2.3 非目标 (V2 再做)
- IM 卡片"一键审批"按钮(需要 IM 平台加密 + 回调服务,MVP 不做)
- 前端用户自助绑定 IM UI(本期只暴露 REST API)
- 勿扰时段 / 汇总策略(沿用邮件方案)
- IM 平台 OAuth 授权接入(本期用自建应用 / 群机器人,无需 OAuth)

---

## 3. 架构

```
                            ┌─────────────────────────────┐
   InitiationService        │   NotificationDispatcher-   │
   ──────────────── 事件 ──▶│   Listener (统一入口)        │
                            └──────────┬──────────────────┘
                                       │
                            ┌──────────▼──────────────────┐
                            │  MailService (旧,不变)      │
                            │  - 写 UNREAD 通知           │
                            │  - 发邮件                   │
                            └─────────────────────────────┘
                                       │
                            ┌──────────▼──────────────────┐
                            │  NotificationDispatcher     │
                            │  (新增, 路由+扇出)          │
                            └──────────┬──────────────────┘
                                       │
              ┌────────────────┬───────┴────────┬────────────────┐
              ▼                ▼                ▼                ▼
      ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
      │ WechatWork   │ │ DingTalk     │ │ Feishu       │ │ (V2 扩展)    │
      │ Channel      │ │ Channel      │ │ Channel      │ │              │
      └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘
              │                │                │
              ▼                ▼                ▼
      user_im_binding   群机器人 Webhook   群机器人 Webhook
      (external_user_id) (markdown)       (interactive 卡片)
```

**关键抽象**:
- `NotificationChannel` 接口:`type()` / `isEnabled()` / `send(NotificationMessage)` 必须不抛
- `NotificationMessage` record:通道无关的统一消息体
- `UserImBinding` 表:用户 ↔ IM 账号的 1:N 映射

---

## 4. 数据模型

### 4.1 user_im_binding (V1.9)

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `user_id` | BIGINT NOT NULL | app_user.id |
| `channel` | VARCHAR(32) NOT NULL | wechat_work / dingtalk / feishu |
| `external_user_id` | VARCHAR(128) NOT NULL | IM 平台内用户标识 |
| `enabled` | BOOLEAN NOT NULL DEFAULT TRUE | 暂停推送(离职/换号) |
| `created_at` | TIMESTAMP | 自动 |
| `updated_at` | TIMESTAMP | 自动 |

**唯一约束**: `(user_id, channel)` — 同一用户同一平台只允许一条 binding

**索引**:
- `ix_im_binding_user (user_id)`
- `ix_im_binding_channel_external (channel, external_user_id)` — 通道推送时反查

### 4.2 路由策略(无新表,纯配置)

每个用户走哪些通道由 `ImProperties.routing` 决定:
- **有 binding** 的用户: `bound-user-route` (默认 `[email, im]`)
- **无 binding** 的用户: `default-route` (默认 `[email]`)

> "im" 是个虚通道名,实际推送时按 `user_im_binding.channel` 选具体平台

---

## 5. 配置

### 5.1 application.yml

```yaml
pmo:
  im:
    enabled: ${PMO_IM_ENABLED:false}        # 总开关
    channels:                              # 通道独立开关
      wechat_work: ${PMO_IM_WECHAT_WORK:false}
      dingtalk: ${PMO_IM_DINGTALK:false}
      feishu: ${PMO_IM_FEISHU:false}
    wechat-work:                           # 企业微信自建应用
      corp-id: ${PMO_WECOM_CORP_ID:}
      agent-id: ${PMO_WECOM_AGENT_ID:}
      app-secret: ${PMO_WECOM_APP_SECRET:}
    dingtalk:                              # 钉钉群机器人
      webhook-url: ${PMO_DINGTALK_WEBHOOK:}
      secret: ${PMO_DINGTALK_SECRET:}
    feishu:                                # 飞书群机器人
      webhook-url: ${PMO_FEISHU_WEBHOOK:}
      secret: ${PMO_FEISHU_SECRET:}
    routing:
      default-route: [email]
      bound-user-route: [email, im]
```

### 5.2 通道启用判定

通道**真正可用**需同时满足:
1. `pmo.im.enabled = true`
2. `pmo.im.channels.<code> = true`
3. 通道自身 credentials 完整(如企业微信需 corpId + agentId + appSecret 全部非空)

任一不满足 → 通道 isEnabled() 返回 false,send() 立即返回 false,打 debug log。

---

## 6. API

### 6.1 UserIMBinding 管理

| Method | Path | 角色 | 说明 |
|---|---|---|---|
| GET | `/user-im-bindings` | Read | 列出自己 / 全部(admin 通过 `?userId=` 限定) |
| GET | `/user-im-bindings/{id}` | Read | 拿一条 |
| POST | `/user-im-bindings` | Admin | 创建(admin 替用户绑定) |
| PUT | `/user-im-bindings/{id}` | Read | 更新(自己 / admin) |
| DELETE | `/user-im-bindings/{id}` | Admin | 删除 |

### 6.2 事件触发(IM 推送)

业务事件由 `NotificationDispatcherListener` 统一处理:
- `InitiationSubmittedEvent` → 申请人部门 lead
- `InitiationDecidedEvent` → 申请人 + 下一审批人
- `InitiationResubmittedEvent` → 当前审批人

**幂等保证**:
- 邮件行为完全保留(MailService.onXxx)
- IM 通道独立,失败不影响邮件

---

## 7. 通道实现细节

### 7.1 企业微信 (WechatWorkChannel)

- **协议**: 自建应用消息推送 (`/cgi-bin/message/send`)
- **消息类型**: `markdown`
- **access_token**: 内存缓存 7000s(官方 7200s,留 200s 余量)
- **收件人**: 通过 `user_im_binding` 查 `external_user_id` (企业微信 userid)
- **touser 拼接**: `|` 分隔多个 userid
- **官方文档**: https://developer.work.weixin.qq.com/document/path/90236

### 7.2 钉钉 (DingTalkChannel)

- **协议**: 群机器人 Webhook (加签)
- **消息类型**: `markdown`
- **加签**: `timestamp + secret` → HMAC-SHA1 → base64 → URL-encode
- **@ 配置**: `isAtAll=false` (MVP 关闭,避免扰民)
- **官方文档**: https://open.dingtalk.com/document/orgapp/custom-robot-access

### 7.3 飞书 (FeishuChannel)

- **协议**: 群机器人 Webhook (加签)
- **消息类型**: `interactive` (卡片)
- **加签**: `timestamp(秒) + secret` → HMAC-SHA1 → base64 → URL-encode
- **卡片元素**: lark_md 正文 + button 跳转
- **官方文档**: https://open.feishu.cn/document/client-docs/bot-v3/add-custom-bot

### 7.4 通用容错

- HTTP 4xx/5xx → 立即返回 false,warn log
- RestClientException (网络超时/DNS 失败) → 立即返回 false,warn log
- 任何异常绝不上抛到 dispatcher (避免 IM 故障拖垮主业务)

---

## 8. 灰度上线步骤

### 8.1 准备
1. **PMO/IT 创建 IM 应用**:
   - 企业微信: 自建应用 → 拿 corpId/agentId/appSecret → 配可信 IP
   - 钉钉: 建群 → 群设置 → 智能群助手 → 添加机器人 → 选"加签" → 拿 webhook + secret
   - 飞书: 同上,加签模式
2. **环境变量**写入 `docker-compose.override.yml` 或 K8s ConfigMap

### 8.2 灰度阶梯(1 周)

| Day | 范围 | 监控 |
|---|---|---|
| D+0 | 仅开启 `pmo.im.channels.dingtalk` (沙箱环境) | log 监控、错误率 |
| D+1 | 钉钉全公司开启 | 通知到达率 |
| D+2 | 开启飞书(双通道对比) | 用户反馈 |
| D+3 | 开启企业微信(双通道对比) | 卡片渲染 |
| D+4 | 路由策略调优(如关掉 bound-user 的 email) | 邮件量下降 |
| D+5-7 | 全量观察 | bug 修复窗口 |

### 8.3 回退
任一通道异常 → 立即关闭对应开关:
```bash
PMO_IM_DINGTALK=false   # 关钉钉
PMO_IM_FEISHU=false     # 关飞书
PMO_IM_WECHAT_WORK=false  # 关企微
```
无需重启(下次启动生效)或动态刷新 (V2 支持 @RefreshScope)。

---

## 9. 测试

### 9.1 单元测试 (10/10 全绿)
`NotificationDispatcherTest` 覆盖:
- Type.fromCode 校验(大小写、非法、null)
- NotificationMessage 默认值
- resolveRouteFor 路由逻辑(有/无 binding)
- envelope 构造
- ImProperties 默认值 + 通道开关
- WechatWork.isConfigured 三字段
- HmacSha1Util 加密 + 幂等

### 9.2 集成测试 (灰度期间补)
- [ ] 钉钉沙箱 webhook 真实发送
- [ ] 飞书沙箱 webhook 真实发送
- [ ] 企业微信 access_token 缓存命中验证
- [ ] 失败注入(防火墙 / 错误 webhook)

### 9.3 smoke 脚本
P1.5 已有 `scripts/business-smoke.sh` (7 步业务冒烟)。
P2-A **不强制** smoke 验证(因为 IM 关闭时无意义)。
灰度开启后,加 1 个 `scripts/im-smoke.sh`:触发 1 个事件 → 验证 IM 通道 log 出现 "sent"。

---

## 10. 已知限制 / 后续

| # | 项 | 影响 | 后续 |
|---|---|---|---|
| 1 | 钉钉/飞书群消息,@不到具体人 | 收件人需主动看群 | V2: 改用应用模式(需 IT 配合) |
| 2 | 企业微信需要用户在企业微信内 | 通知被企业微信风控 | 监控发送失败率,失败率高时检查白名单 |
| 3 | 钉钉/飞书 secret 明文存在 env | 风险 | 后续接 Vault / KMS |
| 4 | UNREAD 写入仅走 MailService 旧路径 | IM 收到但不写入通知中心 | V2: 通道写入也用 dispatcher(避免重复) |
| 5 | 无前端自助绑定 UI | PMO/IT 需替用户后台建 binding | V2: 增 `frontend/src/views/Settings/IM.vue` |

---

## 11. ADR (架构决策记录)

| 决策 | 选项 | 选 | 理由 |
|---|---|---|---|
| IM 接入模式 | 自建应用 / 群机器人 | **自建(企微) + 群机器人(钉钉/飞书)** | 企微自建可点对点;钉钉/飞书建应用成本高,群机器人足够 |
| 消息类型 | text / markdown / 卡片 | **markdown / interactive** | 跨平台一致性强,支持加粗/链接/按钮 |
| 路由策略 | 广播 / 点对点 / 混合 | **点对点(按 binding)** | 用户体验最好 |
| 默认开关 | 全开 / 全关 | **全关** | 零侵入,符合灰度原则 |
| 失败处理 | 抛 / 吞 | **吞(warn log)** | 不影响主业务 |
| 卡片"一键审批" | 立即做 / 后续 | **后续(V2)** | 需加密/回调,工作量大,不影响 P2-A 价值 |

---

## 12. 联系窗口

- **架构 / 代码**: DEV Lead
- **企业微信应用创建 / 密钥管理**: IT
- **钉钉/飞书群机器人**: PMO 协调
- **灰度观察 / 用户反馈**: PMO Lead

---

> **TL;DR**: 通道无关抽象 + 3 个 IM 实现 + 用户绑定表 + 路由策略,
> 默认全关零侵入,灰度期按通道阶梯开启,失败隔离。
