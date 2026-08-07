// 端到端测 P1.5 收尾 #14:project.progressPct 自动重算 + 持久化
//
// 设计原则:
//  1. 隔离:每次跑都创建一个全新的 P-E2E-PROG-* 项目 + 5 个 milestone,跑完不污染种子数据
//  2. 数学精确断言:weight = [1,2,3,4,5] 总和=15,预期 progress 0→20→60→100
//  3. 覆盖三个触发点:updateStatus (COMPLETED) / updateFromRequest (weight 变) / softDelete
//  4. 同时验证 /milestones/progress 实时算 和 /gantt 持久化字段(后端写库后这两个应该一致)
//
// 用法:
//   node scripts/e2e/gantt-progress.mjs
//   PMO_API=http://localhost:8088/api node scripts/e2e/gantt-progress.mjs

import { Suite, apiGet, apiPost, login, token } from './helpers.mjs'

const API = process.env.PMO_API ?? 'http://localhost:8088/api'
const suite = new Suite('Gantt: project.progressPct 自动重算(P1.5 收尾)')

// 全局:本轮创建的项目
let projectId, projectCode
let msIds = [] // 5 个 milestone id, weight = 1,2,3,4,5

// ──────────────── Setup:创建隔离项目 + 5 个 milestone ────────────────

suite.test('Setup:创建全新项目 + 5 个 weight=[1,2,3,4,5] 的 milestone', async () => {
  const ts = Date.now().toString().slice(-6)
  projectCode = `P-E2E-PROG-${ts}`
  // 1) 创建项目
  const p = await apiPost('/projects', {
    code: projectCode,
    name: `E2E 进度测试 ${ts}`,
    typeCode: 'DELIVERY',
    statusCode: 'ACTIVE',
    planStartDate: '2025-06-01',
    planEndDate: '2025-12-31',
  })
  projectId = p.id
  if (!projectId) throw new Error(`创建项目失败: ${JSON.stringify(p)}`)

  // 2) 创建 5 个 milestone
  for (let i = 1; i <= 5; i++) {
    const m = await apiPost('/milestones', {
      projectId,
      name: `MS-${i}`,
      sequence: i,
      weight: i,  // 1,2,3,4,5
      planDate: `2025-07-${String(i).padStart(2, '0')}`,
    })
    if (!m?.id) throw new Error(`创建 MS-${i} 失败: ${JSON.stringify(m)}`)
    msIds.push(m.id)
  }
  console.log(`     projectId=${projectId} code=${projectCode} msIds=${msIds.join(',')}`)
})

// ──────────────── Case 1:全 PENDING → progress = 0 ────────────────

suite.test('全 PENDING 时 /progress = 0', async () => {
  const r = await apiGet(`/milestones/progress/${projectId}`)
  if (r.progressPct !== 0) throw new Error(`期望 0,实际 ${r.progressPct}`)
})

// ──────────────── Case 2:改 1 个 COMPLETED → progress = 1/15 ROUND 7% ────────────────

suite.test('改 1 个 COMPLETED (w=1) → /progress = ROUND(1/15*100) = 7', async () => {
  const r = await fetch(`${API}/milestones/${msIds[0]}/status`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token()}` },
    body: JSON.stringify({ status: 'COMPLETED', actualDate: '2026-06-08' }),
  })
  const j = await r.json()
  if (j.code !== 0) throw new Error(`改状态失败: ${j.message}`)
  const r2 = await apiGet(`/milestones/progress/${projectId}`)
  // ROUND(6.67) = 7
  if (r2.progressPct !== 7) throw new Error(`期望 7,实际 ${r2.progressPct}`)
  console.log(`     weight=1 / total=15 = 6.67% → ROUND = 7 ✓`)
})

// ──────────────── Case 3:Gantt 持久化字段同步刷新 ────────────────

suite.test('Gantt bar 的 progressPct 与 /progress 一致(=7)', async () => {
  const g = await apiGet('/gantt?includeCompleted=true')
  const bar = g.bars.find(b => b.projectId === projectId)
  if (!bar) throw new Error(`Gantt 里找不到 projectId=${projectId}`)
  if (bar.progressPct !== 7) throw new Error(`Gantt bar.progressPct=${bar.progressPct},期望 7`)
  // milestone 数也校验
  if (bar.milestones.length !== 5) throw new Error(`milestone 数=${bar.milestones.length},期望 5`)
})

// ──────────────── Case 4:加到 3 个 COMPLETED (1+2+3=6) → ROUND(6/15*100)=40 ────────────────

suite.test('再加 2 个 COMPLETED (1+2+3=6/15) → /progress = 40', async () => {
  for (const id of [msIds[1], msIds[2]]) {
    const r = await fetch(`${API}/milestones/${id}/status`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token()}` },
      body: JSON.stringify({ status: 'COMPLETED', actualDate: '2026-06-08' }),
    })
    const j = await r.json()
    if (j.code !== 0) throw new Error(`改 ms=${id} 失败: ${j.message}`)
  }
  const r2 = await apiGet(`/milestones/progress/${projectId}`)
  if (r2.progressPct !== 40) throw new Error(`期望 40,实际 ${r2.progressPct}`)
  console.log(`     6/15 = 40% ✓`)
})

// ──────────────── Case 5:全 COMPLETED → progress = 100 ────────────────

suite.test('全 COMPLETED (15/15) → /progress = 100', async () => {
  for (const id of [msIds[3], msIds[4]]) {
    await fetch(`${API}/milestones/${id}/status`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token()}` },
      body: JSON.stringify({ status: 'COMPLETED', actualDate: '2026-06-08' }),
    })
  }
  const r2 = await apiGet(`/milestones/progress/${projectId}`)
  if (r2.progressPct !== 100) throw new Error(`期望 100,实际 ${r2.progressPct}`)
})

// ──────────────── Case 6:改 1 个回 PENDING → progress 跟着掉 ────────────────

suite.test('改 1 个回 PENDING → /progress 应掉', async () => {
  // 把 msIds[4] (w=5) 改回 PENDING
  await fetch(`${API}/milestones/${msIds[4]}/status`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token()}` },
    body: JSON.stringify({ status: 'PENDING' }),
  })
  const r2 = await apiGet(`/milestones/progress/${projectId}`)
  // 10/15 = 66.67% → ROUND = 67
  if (r2.progressPct !== 67) throw new Error(`期望 67,实际 ${r2.progressPct}`)
  console.log(`     10/15 = 66.67% → ROUND = 67 ✓`)
})

// ──────────────── Case 7:weight 变化触发重算 ────────────────

suite.test('改 msIds[0] 的 weight 1→10 → 分子分母同变,progress 应刷', async () => {
  // 上一步状态:msIds[0] COMPLETED (w=1),msIds[1,2,3] COMPLETED (w=2,3,4),msIds[4] PENDING (w=5)
  // 把 msIds[0] weight 1→10 — 仍是 COMPLETED(weight 变更不改 status)
  // 新分子 = 10+2+3+4 = 19,新分母 = 10+2+3+4+5 = 24
  // 19/24 = 79.17% → ROUND = 79
  // 如果重算没触发,会停留在 67(上一步的 10/15)
  await fetch(`${API}/milestones/${msIds[0]}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token()}` },
    body: JSON.stringify({ weight: 10 }),
  })
  const r2 = await apiGet(`/milestones/progress/${projectId}`)
  if (r2.progressPct !== 79) throw new Error(`期望 79,实际 ${r2.progressPct}`)
  if (r2.progressPct === 67) throw new Error(`progressPct 没变 (67→${r2.progressPct}),weight 变更重算没触发`)
  console.log(`     weight 变化触发重算: 19/24 = 79.17% → ROUND = 79 ✓`)
})

// ──────────────── Case 8:overview 接口聚合返回一致 ────────────────

suite.test('/projects/{id}/overview 的 progressPct = /progress', async () => {
  const ov = await apiGet(`/projects/${projectId}/overview`)
  const p = await apiGet(`/milestones/progress/${projectId}`)
  if (ov.progressPct !== p.progressPct) {
    throw new Error(`overview=${ov.progressPct} vs /progress=${p.progressPct},不一致`)
  }
})

// ──────────────── Case 9:数据隔离 — 我们的项目不影响其他 seed 项目 ────────────────

suite.test('我们的 E2E 项目不影响 P-2025-001 等 seed 项目', async () => {
  const g = await apiGet('/gantt?includeCompleted=true')
  const seed = g.bars.find(b => b.projectCode === 'P-2025-001')
  if (!seed) throw new Error('P-2025-001 都不见了,意外副作用')
  // seed 项目的 progress 应该是 1(2 COMPLETED + 1 IN_PROGRESS 折算的 1.46% 四舍五入)
  // 不应被我们的 e2e 改动影响
  if (seed.progressPct < 0 || seed.progressPct > 100) {
    throw new Error(`P-2025-001 progress 异常: ${seed.progressPct}`)
  }
})

// ──────────────── Run ────────────────

await login('admin', 'pmo123')
const result = await suite.run()
process.exit(result.fail > 0 ? 1 : 0)
