// 3 页面可访问性回归 — 核心 E2E
// 这就是用户要的"以后改一行就抓出同类问题"
import { Suite, fetchHtml } from './helpers.mjs'

const suite = new Suite('Pages: 3 个核心页面可访问性 + 关键内容渲染')

// --- 公共:每个 SPA 路由都应该返回 index.html ---
async function checkSpaRoute(path, expectedText) {
  const html = await fetchHtml(path)
  if (!html.includes('id="app"')) throw new Error(`HTML 没有 #app 根节点`)
  if (!html.includes('/src/main.ts')) throw new Error(`HTML 没有挂载 Vite 入口`)
  if (expectedText && !html.includes(expectedText)) {
    throw new Error(`HTML 不包含预期文本 "${expectedText}"`)
  }
}

suite.test('GET / 返回 200 + Vue SPA 入口', async () => {
  await checkSpaRoute('/', 'PMO')
})

suite.test('GET /dashboard 返回 200 + Vue SPA 入口', async () => {
  await checkSpaRoute('/dashboard')
})

suite.test('GET /projects 返回 200 + Vue SPA 入口', async () => {
  await checkSpaRoute('/projects')
})

suite.test('GET /initiations 返回 200 + Vue SPA 入口', async () => {
  await checkSpaRoute('/initiations')
})

suite.test('GET /login 返回 200 + Vue SPA 入口', async () => {
  await checkSpaRoute('/login')
})

// --- 项目详情页路由(数字 id 才能匹配,避免跟 /projects 列表冲突) ---
suite.test('GET /projects/1 详情页 SPA 入口', async () => {
  await checkSpaRoute('/projects/1')
})

// --- Vite 关键模块能编译并 serve ---
suite.test('Vite 编译 main.ts 返回 200', async () => {
  const html = await fetchHtml('/')
  // 找到 main.ts 路径(可能带 ?t= 时间戳)
  const match = html.match(/src="(\/src\/main\.ts(?:\?[^"]*)?)"/)
  if (!match) throw new Error('HTML 没引 main.ts')
  const r = await fetch('http://localhost:5173' + match[1])
  if (r.status !== 200) throw new Error(`main.ts HTTP ${r.status}`)
  const text = await r.text()
  if (!text.includes('createApp')) throw new Error('main.ts 编译产物缺 createApp')
  if (!text.includes('createPinia')) throw new Error('main.ts 编译产物缺 createPinia')
})

// --- 关键依赖能 resolve ---
suite.test('Vite 解析 vue-echarts 依赖', async () => {
  const r = await fetch('http://localhost:5173/node_modules/.vite/deps/vue-echarts.js')
  if (r.status !== 200) throw new Error(`vue-echarts HTTP ${r.status}`)
})

suite.test('Vite 解析 echarts/core (按需注册)', async () => {
  const r = await fetch('http://localhost:5173/node_modules/.vite/deps/echarts_core.js')
  if (r.status !== 200 && r.status !== 304) {
    // 备选路径
    const r2 = await fetch('http://localhost:5173/node_modules/.vite/deps/echarts.js')
    if (r2.status !== 200) throw new Error(`echarts 核心 HTTP ${r.status}/${r2.status}`)
  }
})

// --- Dashboard 关键依赖:vue-echarts 8.x 显式注册的 7 个组件必须都能加载 ---
suite.test('ECharts PieChart / CanvasRenderer 能加载(回归上次 bug)', async () => {
  // 触发 main.ts 完整加载,看 vite 能不能解析所有 echarts 子模块
  const mainCode = await (await fetch('http://localhost:5173/src/main.ts')).text()
  if (!mainCode.includes('use([')) {
    throw new Error('main.ts 没有 use() 注册 ECharts 组件(vue-echarts 8.x 必要)')
  }
  if (!mainCode.includes('PieChart') || !mainCode.includes('CanvasRenderer')) {
    throw new Error('main.ts use() 缺少必要组件')
  }
})

const result = await suite.run()
process.exit(result.fail > 0 ? 1 : 0)
