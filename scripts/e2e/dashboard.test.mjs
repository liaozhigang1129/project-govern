// Dashboard 数据流 E2E
// 模拟浏览器拿数据,确保 4 个 API 全通,数据结构符合前端期望
// (Dashboard 真实渲染必须在浏览器里跑,这里是数据契约层)
import { Suite, login, apiGet } from './helpers.mjs'

const suite = new Suite('Dashboard: 4 个核心 API 数据契约')

await login()

let kpis
suite.test('GET /dashboard/kpis 返回 4 个数字字段', async () => {
  kpis = await apiGet('/dashboard/kpis')
  const need = ['activeCount', 'overdueProjects', 'closedThisMonth', 'newInitiationsThisMonth']
  for (const k of need) {
    if (typeof kpis[k] !== 'number') throw new Error(`缺字段 ${k} (类型 ${typeof kpis[k]})`)
  }
})

suite.test('GET /projects 返回数组', async () => {
  const projects = await apiGet('/projects')
  if (!Array.isArray(projects)) throw new Error('不是数组')
  if (projects.length === 0) throw new Error('项目数为 0,种子数据丢失?')
  const p = projects[0]
  for (const k of ['id', 'code', 'name']) {
    if (p[k] == null) throw new Error(`项目缺字段 ${k}`)
  }
})

suite.test('GET /dashboard/status-distribution 返回 object', async () => {
  const dist = await apiGet('/dashboard/status-distribution')
  if (typeof dist !== 'object' || Array.isArray(dist)) throw new Error('不是 object')
  if (Object.keys(dist).length === 0) throw new Error('分布数据为空')
})

suite.test('GET /dashboard/health-distribution 返回 object', async () => {
  const dist = await apiGet('/dashboard/health-distribution')
  if (typeof dist !== 'object' || Array.isArray(dist)) throw new Error('不是 object')
})

// --- 验证前端 ProjectCard 类型契约 ---
suite.test('ProjectCard 关键字段在前端类型里都被后端满足', async () => {
  const projects = await apiGet('/projects')
  const card = projects[0]
  // 前端 ProjectCard 类型需要的字段
  const expected = ['id', 'code', 'name', 'progressPct']
  for (const k of expected) {
    if (!(k in card)) throw new Error(`后端项目缺前端类型字段 ${k}`)
  }
})

// --- 健康度字典中文 name ---
suite.test('健康度字典 name 必须是中文(前端颜色映射依赖)', async () => {
  const levels = await apiGet('/dict/health-levels')
  const names = levels.map(l => l.name)
  if (!names.includes('正常') || !names.includes('关注') || !names.includes('严重')) {
    throw new Error(`健康度字典 name 异常: ${JSON.stringify(names)}`)
  }
  // 颜色字段
  for (const l of levels) {
    if (!l.colorHex) throw new Error(`健康度 ${l.code} 缺 colorHex`)
  }
})

// --- 项目详情页聚合端点 ---
suite.test('GET /projects/1/overview 一次返回详情+里程碑+进度', async () => {
  const o = await apiGet('/projects/1/overview')
  if (!o.project) throw new Error('缺 project')
  if (!Array.isArray(o.milestones)) throw new Error('milestones 不是数组')
  if (typeof o.progressPct !== 'number') throw new Error('progressPct 不是数字')
  // 字典嵌套
  if (!o.project.type?.code) throw new Error('project.type 缺 code 字段(嵌套 DictRef)')
  if (!o.project.status?.code) throw new Error('project.status 缺 code 字段')
  if (!o.project.health?.code) throw new Error('project.health 缺 code 字段')
})

const result = await suite.run()
process.exit(result.fail > 0 ? 1 : 0)
