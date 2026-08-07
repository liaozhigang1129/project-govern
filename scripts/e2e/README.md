# E2E 测试

> **双形态**:Node 18+ 零依赖(默认) + Cypress 13(可选 GUI)

## Node 18+ 零依赖(推荐,CI 用)

```bash
node scripts/e2e/run-all.mjs
```

跑 5 个 suite。**核心抓的就是上一轮"3 页面不能访问"那类回归**。

| Suite | 抓什么 |
|---|---|
| `auth.test.mjs` | 登录 / 错误密码拒绝 / /auth/me |
| `pages.test.mjs` | 5 个 SPA 路由 200 + Vite 关键依赖 + ECharts 显式注册(回归上次 bug) |
| `dashboard.test.mjs` | 4 个 KPI 字段 + ProjectCard 类型契约 + 健康度字典中文名 |
| `initiations.test.mjs` | 立项流程:草稿→提交→审批 |
| `gantt-progress.mjs` | P1.5 收尾:创建临时项目 + 5 ms,断言 progress 0→7→40→100→67→58,验证 updateStatus/weight/overview/gantt 全部一致 |

## Cypress(本地 GUI 用)

```bash
pnpm add -D cypress
pnpm cypress run           # headless
pnpm cypress open          # GUI
```

`cypress/e2e/pages.cy.js` 跑 6 个 GUI case(实际渲染 KPI 卡、canvas、表格行)。

## 写新 case

```js
// scripts/e2e/my.test.mjs
import { Suite, login, apiGet } from './helpers.mjs'

const suite = new Suite('My feature')
await login()                            // 自动拿 token
suite.test('xxx', async () => { ... })
process.exit((await suite.run()).fail > 0 ? 1 : 0)
```

加到 `run-all.mjs` 的 files 数组即可。
