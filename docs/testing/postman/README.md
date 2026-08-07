# PMO PMS API 测试集

> 一套**双重测试套件**:
> 1. **Postman Collection** (29 个请求,带断言 + JWT 自动注入)
> 2. **Shell 烟雾测试** (`smoke.sh`,16 个核心调用,可直接进 CI)

## 1. Postman 集

### 文件
- `pmo-pms.postman_collection.json` — 8 个文件夹 / 29 个请求 / 自动断言 `code == 0`
- `pmo-pms.postman_environment.json` — 环境变量(baseUrl / token)

### 导入步骤
1. Postman → **Import** → 拖入上面两个 JSON
2. 右上角环境选择器选 **"PMO PMS Local"**
3. 启动后端:
   ```bash
   cd backend && mvn spring-boot:run
   ```
4. 跑 **Auth → Login** 一次,响应里的 `data.token` 会**自动写入环境变量** `token`
5. **Collection Runner** 一次跑完 29 个请求

### 文件夹结构
| 文件夹 | 个数 | 说明 |
|--------|------|------|
| Auth | 2 | 登录 / 当前用户 |
| Dashboard | 4 | KPI / 卡片 / 健康度 / 状态分布 |
| Projects | 5 | 项目 CRUD |
| Initiations | 5 | 立项列表/详情/提交/审批/记录 |
| Milestones | 5 | 里程碑 CRUD + 进度 |
| Departments | 1 | 部门列表 |
| Users | 1 | 用户列表 |
| Dictionaries | 6 | 6 个业务字典 |

### 业务约定
- 业务成功 → `{"code":0, "data":{...}}`
- 业务失败 → `{"code":4xx, "message":"..."}`
- 立项审批 3 级:DEPT_LEAD → PMO → EXEC
- 软删除 = 设 `deletedAt`,字段 `deleted` **不改**

---

## 2. Shell 烟雾测试(`smoke.sh`)

适合 **CI / Docker 健康检查 / 手动快测**:

```bash
chmod +x smoke.sh
./smoke.sh
```

输出形如:
```
▶ 1. 登录
{"code":0,"data":{"token":"eyJhbGciOiJIUzUxMiJ9..."}}
▶ 2. 当前用户
...
✅ 16 个核心调用全部 2xx 通过
```

### 集成到 GitHub Actions
```yaml
- name: API smoke
  run: |
    java -jar backend/target/pmo-pms-backend.jar &
    sleep 20  # 等 Spring 启动
    chmod +x docs/api-testing/smoke.sh
    docs/api-testing/smoke.sh
```

---

## 3. Newman (命令行跑 Postman)

```bash
npm install -g newman newman-reporter-htmlextra
newman run pmo-pms.postman_collection.json \
  -e pmo-pms.postman_environment.json \
  --reporters cli,htmlextra \
  --reporter-htmlextra-export report.html
```

会生成 HTML 报告,适合 PR check 跑完贴到评论里。
