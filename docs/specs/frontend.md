---
status: active
created: 2026-08-07
updated: 2026-08-07
summary: 前端架构(目录结构 + 路由守卫 + Pinia + Axios + ECharts)
---

# 前端架构(Frontend)

> 单一事实来源:Vue 3 + Pinia + Vite 工程的目录、路由、状态管理、HTTP 拦截器、ECharts 注册。
> 对应来源:[`legacy/pmo-pms-mvp-design.md` §8](legacy/pmo-pms-mvp-design.md)

---

## 1. 目录结构

```
frontend/src/
├── main.ts                 # createApp + Pinia + Router + ElementPlus(zh-cn)
├── App.vue                 # 整体布局:侧边栏 + 顶栏 + <RouterView>
├── router/index.ts         # 路由 + meta.roles 守卫
├── stores/                 # Pinia(auth / project / notification)
│   ├── auth.ts             # token / user,持久化到 localStorage
│   └── ...
├── api/                    # 17 个 API 客户端(client / users / ...)
│   ├── client.ts           # Axios 实例 + 拦截器 + TS 类型
│   └── ...
├── views/                  # 18 个视图(Login / Dashboard / Projects / ...)
├── components/             # 13+ 复用组件(GanttView / WbsTreeView / ...)
├── utils/                  # axios / 错误处理
├── styles/                 # 主题 + Design Token
└── assets/
```

---

## 2. 路由 + 守卫

```ts
router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!auth.token && to.path !== '/login') return { path: '/login' }
  if (auth.token && to.path === '/login')  return { path: '/' }
})
```

- history 模式(nginx 用 `try_files $uri $uri/ /index.html` 兜底)
- **细粒度 role 控制**:每个路由 `meta.roles`,与后端 `@RequireRoles` 双重门控
- **菜单权限**:`visibleMenuItems` 根据 user.role 过滤

---

## 3. Pinia auth store

```ts
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const user  = ref<UserInfo | null>(null)

  async function login(username, password) {
    const res = await api.post<LoginResponse>('/auth/login', { username, password })
    token.value = res.token
    user.value  = res.user
    localStorage.setItem('token', res.token)
    localStorage.setItem('user', JSON.stringify(res.user))
  }
  function logout() { token.value = null; user.value = null; localStorage.clear() }
  function restore() { const raw = localStorage.getItem('user'); if (raw) user.value = JSON.parse(raw) }
  return { token, user, login, logout, restore }
})
```

- **Token 持久化**:`localStorage`(MVP 简化),生产前应改 `httpOnly cookie`(XSS 风险)
- `App.vue` 的 `onMounted` 调 `restore()` 防止刷新掉登录态

---

## 4. Axios 拦截器

```ts
api.interceptors.response.use(
  (r) => {
    const body = r.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code !== 0) return Promise.reject(new Error(body.message))
      return body.data     // ← 业务层 await 拿到的是 data,不用再 .data.data
    }
    return body
  },
  (err) => {
    if (err.response?.status === 401) { localStorage.removeItem('token'); window.location.href = '/login' }
    return Promise.reject(new Error(err.response?.data?.message ?? err.message))
  }
)
```

请求拦截器自动 `Bearer <token>`,**业务代码零感知**。

---

## 5. ECharts 按需注册

```ts
// main.ts
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, BarChart, LineChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'

use([CanvasRenderer, PieChart, BarChart, LineChart,
     TitleComponent, TooltipComponent, LegendComponent, GridComponent])
```

- vue-echarts + echarts 强制要求显式注册,否则 canvas 不渲染
- Dashboard 用 PieChart × 2(状态分布 + 健康度分布)
- 以后再加新图只需 `use([...])` 多注册一个组件

---

## 6. Element Plus 按需

`vite.config.ts` 用 `unplugin-auto-import` + `unplugin-vue-components`,`ElButton` / `ElMenu` / `ElCard` 等直接 `<el-button>` 写,不用 import。

---

## 7. Vite dev proxy

```ts
server: {
  port: 5173,
  proxy: { '/api': { target: 'http://localhost:8088', changeOrigin: true } }
}
```

**前端开发**:`/api/auth/login` 走 Vite proxy → 后端 8088,**没有跨域问题**。
**生产**:走 nginx 反代(同 /api 路径),无需改前端代码。
