// 跑全部 E2E: auth + pages + dashboard + 甘特图进度
import { spawn } from 'node:child_process'
import { setTimeout as wait } from 'node:timers/promises'

const files = [
  '../scripts/e2e/auth.test.mjs',
  '../scripts/e2e/pages.test.mjs',
  '../scripts/e2e/dashboard.test.mjs',
  '../scripts/e2e/initiations.test.mjs',
  '../scripts/e2e/gantt-progress.mjs',  // P1.5 收尾:progressPct 自动重算
]

let totalPass = 0, totalFail = 0
for (const f of files) {
  const r = spawn('node', [f], { stdio: 'inherit' })
  await new Promise(resolve => r.on('close', resolve))
  if (r.exitCode !== 0) totalFail++
  else totalPass++
}

console.log('\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━')
console.log(`Suites 通过 ${totalPass} / 失败 ${totalFail}`)
process.exit(totalFail > 0 ? 1 : 0)
