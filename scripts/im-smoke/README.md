# im-smoke — IM 通道端到端冒烟(企业微信 / 钉钉 / 飞书)

P2 #6 验收脚本。验证后端 → 三个 IM 通道 的端到端推送链路。

## 文件

- `im-smoke.sh` — 主脚本(可执行)
- `im_webhook_receiver.py` — Python `http.server` mock,接收三个平台请求并写到 `logs/*.jsonl`

## 前置条件

### 1. 后端启动时覆盖 env(指向本地 mock)

```bash
cd backend
export PMO_IM_ENABLED=true
export PMO_IM_WECHAT_WORK=true
export PMO_IM_DINGTALK=true
export PMO_IM_FEISHU=true
export PMO_WECOM_CORP_ID=wwmockcorp000
export PMO_WECOM_AGENT_ID=1000002
export PMO_WECOM_APP_SECRET=mocksecret_abc
export PMO_WECOM_GETTOKEN_URL=http://localhost:18080/wechat-work/gettoken
export PMO_WECOM_SEND_URL=http://localhost:18080/wechat-work/send
export PMO_DINGTALK_WEBHOOK=http://localhost:18080/dingtalk/webhook
export PMO_FEISHU_WEBHOOK=http://localhost:18080/feishu/webhook
mvn package -DskipTests
nohup java -jar target/project-govern-backend.jar > /tmp/backend-im-smoke.log 2>&1 &
```

> ⚠️ `PMO_WECOM_GETTOKEN_URL` / `PMO_WECOM_SEND_URL` / `PMO_DINGTALK_WEBHOOK` / `PMO_FEISHU_WEBHOOK` 走 `pmo.im.wechat-work.{gettoken-url,send-url}` 字段绑定,application.yml 已声明默认值。生产不设这些 env 时,自动走 `qyapi.weixin.qq.com` 官方地址。

### 2. 启动 mock 接收器

脚本会自动启动。手动启动:

```bash
cd /Users/lzg/Documents/pmo-pms
python3 scripts/im-smoke/im_webhook_receiver.py --port 18080 --logdir logs
```

mock 行为:
- 接收路径:`/wechat-work/{send,gettoken}`、`/dingtalk/{webhook,...}`、`/feishu/{webhook,...}`
- `GET /wechat-work/gettoken?corpsecret=WRONG` → 返 `errcode=40013`(用于错误凭证测试)
- 其它请求 → 返 `errcode=0` / 飞书 `code=0`
- 每个请求写一行到 `logs/<channel>.jsonl`

## 用法

```bash
# 1. 基础(只跑 baseline)
./im-smoke.sh

# 2. 含 DND 场景(创建 DND 窗口 → 验证 IM 被挡 → 禁用 DND → 验证恢复)
./im-smoke.sh --with-dnd

# 3. 错误凭证(需手动重启后端用 PMO_WECOM_APP_SECRET=WRONG)
./im-smoke.sh --with-bad-creds

# 4. 清理:删除 lead_wu 的所有 binding + 禁用 DND
./im-smoke.sh --cleanup
```

## 验证矩阵

| 用例 | 期望 |
|---|---|
| baseline | 3 通道 jsonl 各 +1 |
| DND on | 3 通道 jsonl 增量 +0(SSE 仍推) |
| DND off | 3 通道 jsonl 各 +1 |
| 错误凭证(WRONG secret) | wechat log 报 `errcode=40013`,其它通道仍 +1 |
| 缺 binding(已 cleanup) | route=`[email]`,jsonl 增量 +0 |

## 手工跑(参考)

```bash
# 1. login
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8088/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"pmo123"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")
PM_TOKEN=$(curl -s -X POST http://localhost:8088/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"pm_zhang","password":"pmo123"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")

# 2. 给 lead_wu(4) 加 3 通道 binding
for ch in wechat_work dingtalk feishu; do
  curl -s -X POST http://localhost:8088/api/user-im-bindings \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"userId\":4,\"channel\":\"$ch\",\"externalUserId\":\"lead_wu_$ch\"}"
done

# 3. 触发立项
TS=$(date +%s)
curl -s -X POST http://localhost:8088/api/initiations \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $PM_TOKEN" \
  -d "{\"code\":\"PRJ-$TS\",\"title\":\"manual $TS\",\"background\":\"x\",\"goals\":\"x\",\"scope\":\"x\",\"applicantId\":2,\"departmentId\":2}"

# 4. 等 3s 后看 jsonl
sleep 3
wc -l logs/{wechat_work,dingtalk,feishu}.jsonl
```

## 已知限制

- 脚本假设 lead_wu 的 userId=4、pm_zhang=2、deptId=2(对应 seed data V1.4)
- mock 接收器只 mock 三个 webhook + 企微 gettoken,**不 mock 钉钉/飞书加签** — 但生产 app secret 为空时 ImProperties.isConfigured() 返 false,该通道不会启用
- 错误凭证测试需要手动重启后端(脚本不自动操作)
