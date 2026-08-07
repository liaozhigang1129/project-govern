// Initiations 立项审批流 E2E
// 覆盖:创建 → DEPT_LEAD 驳回 → 补料重提 → DEPT_LEAD 通过 → PMO_ADMIN 通过 → EXEC 通过 → 自动建项目
// 顺带覆盖 4 个原 e2e 漏掉的契约
import { Suite, apiGet, apiPost, login } from './helpers.mjs'

const suite = new Suite('Initiations: 立项三级审批 + 补料重提 + 记录可追溯')

// 用一个独立前缀避免重码
const tag = 'E2E-' + Date.now().toString(36).toUpperCase()

// --- 1. 创建立项 ---
let initiationId = -1
suite.test('POST /initiations 创建立项(状态 PENDING, currentStep=DEPT_LEAD)', async () => {
  const data = await apiPost('/initiations', {
    code: 'IR-' + tag,
    title: '审批流 E2E 测试项目',
    applicantId: 1,
    departmentId: 1,
    background: 'E2E 背景',
    goals: 'E2E 目标',
    scope: 'E2E 范围',
  })
  initiationId = data.id
  if (data.status?.code !== 'PENDING') throw new Error(`expected PENDING, got ${data.status?.code}`)
  if (data.currentStep !== 'DEPT_LEAD') throw new Error(`expected DEPT_LEAD, got ${data.currentStep}`)
})

// --- 2. DEPT_LEAD SUPPLEMENT → 状态 SUPPLEMENT ---
suite.test('DEPT_LEAD 打回补材料 → 状态 SUPPLEMENT, currentStep 保持', async () => {
  const data = await apiPost(`/initiations/${initiationId}/decide`, {
    decision: 'SUPPLEMENT',
    comment: '请补充预算明细',
  })
  if (data.status !== 'SUPPLEMENT') throw new Error(`expected SUPPLEMENT, got ${data.status}`)
  if (data.currentStep !== 'DEPT_LEAD') throw new Error(`expected DEPT_LEAD, got ${data.currentStep}`)
})

// --- 3. 申请人在 SUPPLEMENT 状态重提 → 状态 PENDING ---
suite.test('SUPPLEMENT → /resubmit → 状态 PENDING, currentStep 保持', async () => {
  const data = await apiPost(`/initiations/${initiationId}/resubmit`, {})
  if (data.status !== 'PENDING') throw new Error(`expected PENDING, got ${data.status}`)
  if (data.currentStep !== 'DEPT_LEAD') throw new Error(`expected DEPT_LEAD, got ${data.currentStep}`)
})

// --- 4. DEPT_LEAD 这次通过 → currentStep=PMO_ADMIN ---
suite.test('DEPT_LEAD 通过补料后的立项 → currentStep 跳到 PMO_ADMIN', async () => {
  const data = await apiPost(`/initiations/${initiationId}/decide`, {
    decision: 'APPROVED',
    comment: '材料补全,通过',
  })
  if (data.status !== 'PMO_APPROVED') throw new Error(`expected PMO_APPROVED, got ${data.status}`)
  if (data.currentStep !== 'PMO_ADMIN') throw new Error(`expected PMO_ADMIN, got ${data.currentStep}`)
})

// --- 5. PMO_ADMIN 通过 → EXEC ---
suite.test('PMO_ADMIN 通过 → currentStep 跳到 EXEC', async () => {
  const data = await apiPost(`/initiations/${initiationId}/decide`, {
    decision: 'APPROVED',
    comment: '治理复核通过',
  })
  if (data.currentStep !== 'EXEC') throw new Error(`expected EXEC, got ${data.currentStep}`)
})

// --- 6. EXEC 通过 → EXEC_APPROVED + 自动建项目 ---
suite.test('EXEC 通过 → EXEC_APPROVED + 自动建项目(projectId 回写)', async () => {
  const data = await apiPost(`/initiations/${initiationId}/decide`, {
    decision: 'APPROVED',
    comment: '终审通过',
  })
  if (data.status !== 'EXEC_APPROVED') throw new Error(`expected EXEC_APPROVED, got ${data.status}`)
  if (!data.projectId || data.projectId < 1) throw new Error(`expected projectId > 0, got ${data.projectId}`)
})

// --- 7. 终态后再 decide 应失败(防幂等崩溃) ---
suite.test('终态后再 decide → 业务失败', async () => {
  // 直接调 decide,应抛错
  let err = null
  try {
    await apiPost(`/initiations/${initiationId}/decide`, { decision: 'APPROVED', comment: '' })
  } catch (e) {
    err = e
  }
  if (!err) throw new Error('终态后 decide 应失败,但没报错')
  if (!err.message.includes('terminal')) throw new Error(`expected error message 含 terminal, got: ${err.message}`)
})

// --- 8. 审批记录有 4 条(1 补料 + 3 通过) ---
suite.test('审批记录流水:1 SUPPLEMENT + 3 APPROVED = 4 条,按时间升序', async () => {
  const list = await apiGet(`/initiations/${initiationId}/records`)
  if (list.length !== 4) throw new Error(`expected 4 records, got ${list.length}`)
  // 校验顺序:SUPPLEMENT → APPROVED → APPROVED → APPROVED
  const seq = list.map((r) => r.decision)
  if (seq[0] !== 'SUPPLEMENT') throw new Error(`records[0] 应是 SUPPLEMENT, got ${seq[0]}`)
  for (let i = 1; i < 4; i++) {
    if (seq[i] !== 'APPROVED') throw new Error(`records[${i}] 应是 APPROVED, got ${seq[i]}`)
  }
})

// --- 9. 重复 code 提交应失败(契约) ---
suite.test('POST /initiations 重复 code → 业务失败', async () => {
  let err = null
  try {
    await apiPost('/initiations', {
      code: 'IR-' + tag, // 同 #1
      title: '重码测试',
      applicantId: 1,
      departmentId: 1,
      background: 'x', goals: 'x', scope: 'x',
    })
  } catch (e) {
    err = e
  }
  if (!err) throw new Error('重码应失败')
  if (!err.message.includes('exists')) throw new Error(`expected error 含 exists, got: ${err.message}`)
})

// --- 10. 列表能查到这条 ---
suite.test('GET /initiations 列表包含本条', async () => {
  const list = await apiGet('/initiations')
  if (!list.find((i) => i.id === initiationId)) {
    throw new Error(`列表中找不到 id=${initiationId}`)
  }
})

// 跑前要登录一次拿 token
await login('admin', 'pmo123')
const result = await suite.run()
process.exit(result.fail > 0 ? 1 : 0)
