<script setup lang="ts">
/**
 * 钉钉通讯录同步日志 (仅 PMO_ADMIN / ADMIN 可见)
 *
 * - 拉最近 50 条同步日志 (DingTalkSyncLogRepository.findTop50...)
 * - 默认每 5s 轮询一次, 用于跟进 RUNNING 中的同步
 * - 支持 URL ?logId=xx 自动滚动并高亮该行
 * - 支持详情抽屉查看 errorDetail / errorMessage
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Document, Promotion } from '@element-plus/icons-vue'
import api from '@/api/client'

interface SyncLog {
  id: number
  startedAt: string
  finishedAt: string | null
  triggerType: string | null
  triggeredBy: string | null
  status: 'RUNNING' | 'SUCCESS' | 'FAILED' | string
  totalDepts: number
  totalUsers: number
  createdDeptCount: number
  disabledCount: number
  errorMessage: string | null
  errorDetail: string | null
}

const logs = ref<SyncLog[]>([])
const loading = ref(false)
let timer: number | null = null

const route = useRoute()
const router = useRouter()

const focusedId = computed<number | null>(() => {
  const v = route.query.logId
  const n = Array.isArray(v) ? Number(v[0]) : Number(v)
  return Number.isFinite(n) && n > 0 ? n : null
})

async function loadLogs() {
  loading.value = true
  try {
    // 后端返 List<DingTalkSyncLog>,非分页
    const res: any = await api.get('/admin/dingtalk/sync/logs')
    const data = Array.isArray(res?.data) ? res.data : Array.isArray(res) ? res : (res?.data?.content ?? [])
    logs.value = Array.isArray(data) ? data : []
  } catch (e: any) {
    ElMessage.error(e?.message ?? '加载同步日志失败')
  } finally {
    loading.value = false
  }
}

function startPolling() {
  stopPolling()
  timer = window.setInterval(loadLogs, 5000)
}

function stopPolling() {
  if (timer != null) {
    window.clearInterval(timer)
    timer = null
  }
}

onMounted(async () => {
  await loadLogs()
  startPolling()
})

onBeforeUnmount(stopPolling)

// 当有 RUNNING 时, 提示当前正在同步的那一条; 否则提示"无正在同步的任务"
const runningLog = computed(() => logs.value.find((l) => l.status === 'RUNNING') ?? null)

// 详情抽屉
const detailVisible = ref(false)
const detailLog = ref<SyncLog | null>(null)
function showDetail(row: SyncLog) {
  detailLog.value = row
  detailVisible.value = true
}

// 操作栏 — 触发新同步(只是便捷入口,实际产品里也已在 Users.vue)
async function triggerSync() {
  try {
    await api.post('/admin/dingtalk/sync/trigger')
    ElMessage.success('已触发, 稍候会在列表里看到 RUNNING 记录')
    router.replace({ query: {} })
    await loadLogs()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '触发失败')
  }
}

// 表格行样式 — 高亮 ?logId=
function rowClass({ row }: { row: SyncLog }) {
  return focusedId.value && row.id === focusedId.value ? 'sync-log-row-focused' : ''
}

// 监听 query 变化滚动
watch(
  focusedId,
  async (v) => {
    if (!v) return
    await loadLogs()
    // 等 DOM 更新
    setTimeout(() => {
      const el = document.querySelector(`tr[data-log-id="${v}"]`) as HTMLElement | null
      if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }, 50)
  },
  { immediate: true },
)

// ============================================================
// 格式化
// ============================================================
function fmtTime(t: string | null | undefined): string {
  if (!t) return '-'
  try {
    return new Date(t).toLocaleString('zh-CN')
  } catch {
    return t
  }
}

function durationSec(started: string | null, finished: string | null): string {
  if (!started) return '-'
  const s = new Date(started).getTime()
  const e = finished ? new Date(finished).getTime() : Date.now()
  if (Number.isNaN(s) || Number.isNaN(e)) return '-'
  const sec = Math.max(0, Math.round((e - s) / 1000))
  if (sec < 60) return `${sec}s`
  const m = Math.floor(sec / 60)
  const r = sec % 60
  return `${m}m ${r}s`
}

function statusTag(s: string): 'success' | 'warning' | 'danger' | 'info' {
  switch (s) {
    case 'SUCCESS':
      return 'success'
    case 'FAILED':
      return 'danger'
    case 'RUNNING':
      return 'warning'
    default:
      return 'info'
  }
}

function statusText(s: string): string {
  return ({ SUCCESS: '成功', FAILED: '失败', RUNNING: '进行中' } as Record<string, string>)[s] ?? s
}
</script>

<template>
  <div class="dingtalk-sync-log">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <div>
            <span class="title">钉钉通讯录同步日志</span>
            <span class="hint">— 仅 PMO_ADMIN / ADMIN 可见; 每 5s 自动刷新</span>
          </div>
          <div class="actions">
            <el-tag v-if="runningLog" type="warning" effect="dark">同步中 #{{ runningLog.id }}</el-tag>
            <el-tag v-else type="info" effect="plain">无正在运行的同步</el-tag>
            <el-button :icon="Promotion" type="primary" plain @click="triggerSync">触发同步</el-button>
            <el-button :icon="Refresh" @click="loadLogs">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="logs"
        :row-class-name="rowClass"
        border
        stripe
        style="width: 100%"
        empty-text="暂无同步记录"
        :row-attrs="(row: any) => ({ 'data-log-id': row.id })"
      >
        <el-table-column prop="id" label="#" width="60" />
        <el-table-column label="开始时间" min-width="160">
          <template #default="{ row }">{{ fmtTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" min-width="160">
          <template #default="{ row }">{{ fmtTime(row.finishedAt) }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="100">
          <template #default="{ row }">{{ durationSec(row.startedAt, row.finishedAt) }}</template>
        </el-table-column>
        <el-table-column prop="triggerType" label="触发" width="100" />
        <el-table-column prop="triggeredBy" label="操作人" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="部门" width="80">
          <template #default="{ row }">
            {{ row.totalDepts ?? 0 }}
            <span class="muted">/ 新建 {{ row.createdDeptCount ?? 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="用户" width="120">
          <template #default="{ row }">
            {{ row.totalUsers ?? 0 }}
            <span class="muted">/ 离职 {{ row.disabledCount ?? 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="错误" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="err">{{ row.errorMessage || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="detailVisible" title="同步日志详情" size="50%">
      <template v-if="detailLog">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="ID">{{ detailLog.id }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTag(detailLog.status)" size="small">
              {{ statusText(detailLog.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ fmtTime(detailLog.startedAt) }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ fmtTime(detailLog.finishedAt) }}</el-descriptions-item>
          <el-descriptions-item label="耗时">
            {{ durationSec(detailLog.startedAt, detailLog.finishedAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="触发类型">{{ detailLog.triggerType }}</el-descriptions-item>
          <el-descriptions-item label="操作人">{{ detailLog.triggeredBy }}</el-descriptions-item>
          <el-descriptions-item label="部门总数">{{ detailLog.totalDepts }}</el-descriptions-item>
          <el-descriptions-item label="新建部门">{{ detailLog.createdDeptCount }}</el-descriptions-item>
          <el-descriptions-item label="用户总数">{{ detailLog.totalUsers }}</el-descriptions-item>
          <el-descriptions-item label="离职禁用">{{ detailLog.disabledCount }}</el-descriptions-item>
        </el-descriptions>

        <h4 style="margin-top: 16px">错误信息</h4>
        <pre class="err-block">{{ detailLog.errorMessage || '(无)' }}</pre>
        <h4>堆栈详情</h4>
        <pre class="err-block">{{ detailLog.errorDetail || '(无)' }}</pre>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.dingtalk-sync-log .head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.dingtalk-sync-log .title {
  font-size: 16px;
  font-weight: 600;
}
.dingtalk-sync-log .hint {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
.dingtalk-sync-log .actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.dingtalk-sync-log .muted {
  color: #909399;
  font-size: 12px;
}
.dingtalk-sync-log .err {
  color: #f56c6c;
}
.dingtalk-sync-log .err-block {
  background: #f5f7fa;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 10px;
  max-height: 320px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
/* el-table 行类名要拼前缀 */
:deep(.sync-log-row-focused) {
  background: #fff7e6 !important;
}
:deep(.sync-log-row-focused td) {
  background: #fff7e6 !important;
}
</style>
