// E2E 通用工具:登录、拿 token、API 调用
// 零依赖,用 node 18+ 内置 fetch

const API = process.env.PMO_API ?? 'http://localhost:8088/api'
const WEB = process.env.PMO_WEB ?? 'http://localhost:5173'

let _token = null

export async function login(username = 'admin', password = 'pmo123') {
  const r = await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  if (!r.ok) throw new Error(`login HTTP ${r.status}`)
  const j = await r.json()
  if (j.code !== 0) throw new Error(`login 业务失败: ${j.message}`)
  // P1.5-c: token 字段改名为 accessToken;refreshToken 来自 Set-Cookie
  _token = j.data.accessToken || j.data.token
  return j.data
}

export function token() {
  if (!_token) throw new Error('未登录,先 await login()')
  return _token
}

export async function apiGet(path) {
  const r = await fetch(`${API}${path}`, {
    headers: { Authorization: `Bearer ${token()}` },
  })
  const j = await r.json()
  if (j.code !== 0) throw new Error(`${path} 失败: ${j.message}`)
  return j.data
}

export async function apiPost(path, body) {
  const r = await fetch(`${API}${path}`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token()}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  })
  const j = await r.json()
  if (j.code !== 0) throw new Error(`${path} 失败: ${j.message}`)
  return j.data
}

// HTML 抓取 + 关键 token 提取
export async function fetchHtml(path) {
  const r = await fetch(`${WEB}${path}`)
  if (!r.ok) throw new Error(`GET ${path} HTTP ${r.status}`)
  return await r.text()
}

// 跑一组:返回 { name, passed, error }
export class Suite {
  constructor(name) {
    this.name = name
    this.cases = []
  }
  test(name, fn) {
    this.cases.push({ name, fn })
  }
  async run() {
    console.log(`\n━━ ${this.name} ━━`)
    let pass = 0, fail = 0
    for (const c of this.cases) {
      try {
        await c.fn()
        console.log(`  ✅ ${c.name}`)
        pass++
      } catch (e) {
        console.log(`  ❌ ${c.name}`)
        console.log(`     ${e.message?.split('\n')[0] ?? e}`)
        fail++
      }
    }
    return { pass, fail, total: this.cases.length }
  }
}
