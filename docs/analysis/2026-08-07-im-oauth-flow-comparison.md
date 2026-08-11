---
status: final
created: 2026-08-07
updated: 2026-08-07
summary: 三平台(企业微信/钉钉/飞书)OAuth 2.0 接入流程与卡片回调可行性对比
---

# 三平台 IM OAuth 2.0 接入流程对比

> **spike 输出 / WP-M6-03 / T-01+T-02**
> 仅做调研,不动代码。
> 决策建议见 [`2026-08-07-im-oauth-decision-proposal.md`](2026-08-07-im-oauth-decision-proposal.md)

---

## 1. 文档目标

对比企业微信 / 钉钉 / 飞书三平台在以下维度的差异,为 M6 IM 回调接入评估提供事实基础:

1. **OAuth 2.0 接入流程**(grant_type / token 生命周期 / refresh / 回调机制)
2. **卡片回调能力**(审批按钮触发 / 状态更新)
3. **回调服务接入要求**(签名算法 / IP 白名单 / 响应超时)

---

## 2. 三平台 OAuth 2.0 流程对比

### 2.1 总览表

| 维度 | 企业微信 | 钉钉 | 飞书 |
|---|---|---|---|
| **官方文档** | [work.weixin](https://developer.work.weixin.qq.com/document/path/91039) | [open.dingtalk](https://open.dingtalk.com/document/orgapp/obtain-identity-credentials) | [open.feishu](https://open.feishu.cn/document/server-docs/authentication-management/access-token) |
| **自建应用类型** | 自建应用-企业内部 | 企业内部应用-H5 微应用 | 自建应用 |
| **grant_type** | `authorization_code` (网页) / `client_credentials` (内部) | `authorization_code` (用户) / `client_credentials` (应用) | `authorization_code` (用户) / `client_credentials` (应用) / `refresh_token` |
| **access_token 有效期** | **2 小时** | **2 小时** | **2 小时** |
| **refresh_token 有效期** | 无(只能重走授权) | 无 | **30 天**(可续) |
| **应用 access_token** | corp_id + corp_secret → `access_token` | appkey + appsecret → `access_token` | app_id + app_secret → `tenant_access_token` |
| **用户 access_token** | code 换 user_access_token | code 换 user access_token | code 换 user_access_token (配合 refresh_token) |
| **通讯录用户身份** | userid (企业内唯一) | userid (unionid/unionEmpId 跨企业) | open_id (应用内唯一) / union_id (跨应用) |
| **回调 IP 白名单** | 必须(应用设置页配置公网 IP) | 必须(开发平台-IP 白名单) | 可选(开放平台校验,可关闭) |
| **签名算法** | SHA1 / SHA256(回调消息) | HMAC-SHA256(回调) | HMAC-SHA256(回调) |
| **回调地址要求** | 公网 HTTPS | 公网 HTTPS | 公网 HTTPS |
| **事件订阅** | 应用回调(密文 AES-256-CBC) | 事件订阅(Stream 模式 / 回调模式) | 事件订阅(2.0 协议) |

### 2.2 获取 access_token 流程(以应用级 token 为例)

**企业微信**
```
GET https://qyapi.weixin.qq.com/cgi-bin/gettoken
    ?corpid=ID&corpsecret=SECRET
→ {"errcode":0, "access_token":"...", "expires_in":7200}
```
- 限频:500 次/分钟/应用
- **强制缓存**:7200s 有效期,必须本地缓存,不能每次都拉

**钉钉**
```
POST https://oapi.dingtalk.com/gettoken
{"appkey":"...", "appsecret":"..."}
→ {"access_token":"...", "expires_in":7200}
```
- 限频:600 次/分钟
- **新旧版差异**:旧版 `oapi.dingtalk.com`,新版 `api.dingtalk.com`(推荐)

**飞书**
```
POST https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal
{"app_id":"...", "app_secret":"..."}
→ {"code":0, "tenant_access_token":"...", "expire":7200}
```
- 限频:1000 次/分钟
- **tenant_access_token** 是应用级,飞书还有 user_access_token(用户级)

### 2.3 OAuth 授权码流程(网页登录绑定场景)

三平台流程高度一致:
```
1. 用户点击"绑定企业微信" → 前端跳转到企业微信授权页
   URL: https://open.weixin.qq.com/connect/oauth2/authorize
        ?appid=corp_id&redirect_uri=...&response_type=code&scope=snsapi_base&state=xxx
2. 用户同意 → 回调到 redirect_uri 带 code
3. 后端用 code + corp_secret 换 user_access_token
4. 后端用 user_access_token 拉 userid + 头像/手机号
5. 写入 user_im_binding 表
```

差异点:
- **state 参数**:三平台都支持,用于防 CSRF
- **scope**:企业微信有 `snsapi_base`(静默)/ `snsapi_userinfo`(详情);钉钉类似 `snsapi_login`/`snsapi_auth`;飞书 `contact:user.id:readonly` 等
- **绑定用户身份**:企业微信 → userid;钉钉 → unionid(推荐)或 userid;飞书 → open_id(应用内)

---

## 3. 卡片回调能力对比

### 3.1 卡片类型与回调支持

| 平台 | 卡片类型 | 按钮回调 | 状态更新 | 公网回调 |
|---|---|---|---|---|
| **企业微信** | 文本卡片 / 图文卡片 / 模板卡片 | ✅ 模板卡片按钮回调(`template_card`) | ✅ 模板卡片"更新"事件 | 必需 |
| **钉钉** | 互动卡片(原"机器人卡片") | ✅ 回调 URL / Stream 推送 | ✅ 卡片整体更新 | 必需(Stream 模式可不需公网) |
| **飞书** | 消息卡片 v2 | ✅ `card.action.trigger` 回调 | ✅ `card.action.update` / `update_message` | 必需 |

### 3.2 关键限制对比

| 维度 | 企业微信 | 钉钉 | 飞书 |
|---|---|---|---|
| **回调响应超时** | 5 秒 | 5 秒(Stream 模式不限) | 3 秒 |
| **签名算法** | SHA1(默认) | HMAC-SHA256 + timestamp + nonce | HMAC-SHA256 + timestamp + nonce |
| **重放保护** | timestamp + nonce + 加密随机串 | timestamp + nonce(可选 Redis 去重) | 同钉钉 |
| **卡片"被动更新"** | ✅ 单独 API `update_template_card` | ✅ `card.update` OAPI | ✅ `card.instance.update` |
| **回调加密** | AES-256-CBC(可选) | 可选 | 可选 |

### 3.3 典型回调报文(以企业微信审批卡片为例)

```json
POST /api/im/callback/wechat-work
Headers: {
  "Content-Type": "application/json",
  "MsgSignature": "abc123...",
  "Timestamp": "1700000000",
  "Nonce": "xyz789"
}
Body: {
  "FromUserName": "UserID",
  "MsgType": "event",
  "Event": "template_card_event",
  "EventKey": "approval_approve_123"
}
```

响应必须 5s 内返回(否则平台重试),且格式:
```json
{"errcode": 0, "errmsg": "ok"}
```

---

## 4. 自建回调服务最低接入要求

### 4.1 基础设施

- **公网 HTTPS 域名**(必须):证书 + 域名 + 备案(国内平台)
- **IP 白名单**:企业微信/钉钉需配置;飞书可选
- **5s 响应窗口**:回调入口必须快进快出,业务逻辑异步化
- **签名校验**:三平台算法不同,需各自实现

### 4.2 三平台签名差异

```java
// 企业微信: SHA1(token + timestamp + nonce + msg_encrypt)
String signature = sha1(sort(token, timestamp, nonce, encrypt)).toUpperCase();

// 钉钉: HMAC-SHA256(appSecret, timestamp)  → base64
String signature = base64(hmacSha256(appSecret, timestamp));

// 飞书: HMAC-SHA256(encryptKey, timestamp + nonce + bodyJson)  → base64
String signature = base64(hmacSha256(encryptKey, timestamp + "\n" + nonce + "\n" + bodyJson));
```

### 4.3 卡片"主动更新"流程

```java
// 1. 用户点卡片按钮 → 平台回调我们
// 2. 我们 5s 内响应"已收到"
// 3. 异步执行业务逻辑 (审批 → 更新数据库)
// 4. 业务完成后主动调用平台 API 更新卡片状态
String accessToken = wechatAccessTokenCache.get();
wechatApi.updateTemplateCard(accessToken, userid, cardId, newCardJson);
```

---

## 5. 工程复杂度评估

### 5.1 代码量估算

| 模块 | 行数估计 |
|---|---|
| OAuth 2.0 客户端(3 平台 × 用户/应用 token) | ~600 行 |
| 卡片发送(3 平台 SDK 适配) | ~900 行 |
| 回调入口 + 签名校验(3 平台) | ~500 行 |
| 卡片回调业务路由(审批 / 状态) | ~400 行 |
| 卡片主动更新 + 重试 | ~300 行 |
| 配置 + Properties(3 平台) | ~200 行 |
| **总计** | **~2,900 行** |

### 5.2 依赖引入

- `weixin-java-cp` 或自实现企业微信 API 包装 (~200KB)
- `dingtalk-sdk-java` 或自实现 (~150KB)
- `oapi-sdk-java` 飞书 SDK (~180KB)
- `bcprov-jdk15on`(AES-256 加密,企业微信回调加密用)

---

## 6. 关键风险点

| ID | 风险 | 影响 | 缓解 |
|---|---|---|---|
| R-010 | 公网 HTTPS 域名 + SSL 证书申请流程(国内备案 7-15 天) | 阻塞开发 | 提前 1 周申请,测试用 ngrok/钉钉 Stream 模式 |
| R-011 | 三平台签名算法不同,易混淆导致回调校验失败 | 安全漏洞 / 误触发 | 单元测试覆盖各平台签名,生产环境加密随机串轮换 |
| R-012 | 5s 响应窗口过紧,业务慢查询会超时 | 卡片状态错乱 | 必须异步化,回调只 ack + 入队;业务在 worker 跑 |
| R-013 | 平台限频(企业微信 500/min 等) | 卡片发送失败 | 本地 Redis 漏桶限流 |
| R-014 | 平台政策变更(接口废弃 / 收费) | 中长期维护成本 | 锁定版本,变更走 ADR |
| R-015 | IM 平台 SSL 证书过期 | 回调 5xx | 监控 cert expiry,提前 30 天告警 |

---

## 7. 结论汇总

| 结论 | 说明 |
|---|---|
| **三平台 OAuth 流程差异较小** | 都是标准 OAuth 2.0,代码复用度高(预计 60%) |
| **卡片回调差异较大** | 签名算法 / 卡片类型 / 更新 API 都不同,需各做适配 |
| **公网 HTTPS 是硬性要求** | 无 SSL 域名 = 无法做回调,这是最大的前置依赖 |
| **当前未评估"是否值得做"** | 见 [`2026-08-07-im-oauth-decision-proposal.md`](2026-08-07-im-oauth-decision-proposal.md) 决策建议 |

---

## 8. 参考链接

- 企业微信: [OAuth 文档](https://developer.work.weixin.qq.com/document/path/91039) / [模板卡片回调](https://developer.work.weixin.qq.com/document/path/90240)
- 钉钉: [获取凭证](https://open.dingtalk.com/document/orgapp/obtain-identity-credentials) / [互动卡片](https://open.dingtalk.com/document/orgapp/robot-interactive-cards)
- 飞书: [tenant_access_token](https://open.feishu.cn/document/server-docs/authentication-management/access-token) / [卡片交互回调](https://open.feishu.cn/document/uAjLw4CM/ukzMukzMukzM/feishu-cards/card-interactive-callback)
