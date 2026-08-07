// 用户登录 API 烟测 — 抓"token 拿不到"这种核心问题
import { Suite, login, token, apiGet } from './helpers.mjs'

const suite = new Suite('Auth: 登录 / 当前用户')

suite.test('admin/pmo123 登录返回 token', async () => {
  const data = await login('admin', 'pmo123')
  // P1.5-c: data 里有 accessToken + refreshToken(cookie)
  if (!data.accessToken && !data.token) throw new Error('accessToken/token 都为空')
  if (!data.user?.fullName) throw new Error('user.fullName 为空')
})

suite.test('错误密码应被拒', async () => {
  const r = await fetch('http://localhost:8088/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'wrong' }),
  })
  if (r.status < 400) throw new Error(`期望 4xx,实得 ${r.status}`)
})

suite.test('GET /auth/me 带 token 返回当前用户', async () => {
  // 先确保已登录(可能上一个 case 改了 token)
  await login()
  const me = await apiGet('/auth/me')
  if (me.username !== 'admin') throw new Error(`期望 admin,实得 ${me.username}`)
})

const result = await suite.run()
process.exit(result.fail > 0 ? 1 : 0)
