# PMO 治理系统 - 前端 (Vue 3 + Element Plus)

## 启动

```bash
# 安装依赖
pnpm install

# 开发模式 (默认代理到 http://localhost:8088/api)
pnpm dev

# 生产构建
pnpm build
```

开发服务器跑在 <http://localhost:5173>。
API 走 Vite proxy: `/api/*` → `http://localhost:8088/api/*`(后端需先启动)。

## 目录结构

```
frontend/
├── index.html
├── vite.config.ts              # Vite + Element Plus 自动按需 + /api proxy
├── tsconfig.app.json           # @/* → src/* 别名
├── src/
│   ├── main.ts                 # 入口:Pinia + ElementPlus + Router
│   ├── App.vue                 # 布局 (侧栏 + header + RouterView)
│   ├── styles/main.scss        # KPI 卡片 / 全局变量
│   ├── api/
│   │   └── client.ts           # axios 封装 + JWT 拦截 + 类型定义
│   ├── stores/
│   │   └── auth.ts             # Pinia auth (token/user/login/logout)
│   ├── router/
│   │   └── index.ts            # 4 个路由 + 守卫
│   └── views/
│       ├── Login.vue           # 登录页
│       ├── Dashboard.vue       # 4 KPI + 2 ECharts + 活跃项目
│       ├── Projects.vue        # 项目列表
│       └── Initiations.vue     # 立项 + 审批
└── .env                        # VITE_API_BASE=/api
```

## 已实现页面

| 页面           | 功能                                                 |
| -------------- | ---------------------------------------------------- |
| `/login`       | 登录(默认填 `admin/pmo123`)                          |
| `/`            | Dashboard: 4 KPI 卡片 + 状态/健康度饼图 + 活跃项目表 |
| `/projects`    | 项目列表 + 进度条 + 状态标签                         |
| `/initiations` | 立项列表 + 新建 + 通过/驳回(单步审批)                |

## 演示账号

| 用户     | 密码   | 角色                      |
| -------- | ------ | ------------------------- |
| admin    | pmo123 | PMO_ADMIN(全权限)         |
| pm_zhang | pmo123 | PM                        |
| lead_wu  | pmo123 | DEPT_LEAD(可审 DEPT_LEAD) |
| vp_chen  | pmo123 | EXEC(终审)                |

## 集成细节

- **TypeScript 严格模式** + 路径别名 `@/* → src/*`
- **Element Plus 自动按需** 通过 `unplugin-vue-components` (不需手动 import)
- **JWT 拦截器**: 401 自动跳登录
- **响应拦截器**: 拆 `ApiResponse<T>` 包装,`code !== 0` 抛错

## 后续可补

- 立项详情页(查看 3 级审批流水 records)
- 项目详情页(里程碑 + 进度图表)
- 用 `openapi-typescript` 从 `../docs/openapi/openapi.json` 生成类型
- 角色权限路由守卫
- 国际化 (i18n)
- ECharts 按需引入(当前 296 KB,优化后能压到 ~100 KB)
