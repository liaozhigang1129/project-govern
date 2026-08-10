<script setup lang="ts">
/**
 * 钉钉请休假同步 - 管理员视图
 * - 触发增量/全量同步 (异步)
 * - 查看同步状态 / 日志 / 请休假列表
 * - 轮询直到 RUNNING 结束
 */
import { onMounted, onUnmounted, ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search, VideoPlay, Document } from '@element-plus/icons-vue'
import {
  dingtalkLeaveApi,
  type DingTalkLeave,
  type DingTalkLeaveSyncState,
  type DingTalkLeaveSyncLog,
} from '@/api/dingtalkLeave'

// ============================================================
// 列表
// ============================================================
const leaves = ref<DingTalkLeave[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

const keyword = ref('')
const filteredLeaves = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return leaves.value
  return leaves.value.filter(
    (l) =>
      (l.userName || '').toLowerCase().includes(kw) ||
      (l.userid || '').toLowerCase().includes(kw) ||
      (l.leaveType || '').toLowerCase().includes(kw) ||
      (l.reason || '').toLowerCase().includes(kw),
  )
})

async function loadList() {
  loading.value = true
  try {
    const data = await dingtalkLeaveApi.list(page.value - 1, size.value)
    leaves.value = data.content
    total.value = data.totalElements
  } catch (e: any) {
    ElMessage.error(e?.message ?? '加载请休假列表失败')
  } finally {
    loading.value = false
  }
}

// ============================================================
// 同步状态 + 触发
// ============================================================
const state = ref<DingTalkLeaveSyncState | null>(null)
const syncing = ref(false)

async function loadState() {
  try {
    state.value = await dingtalkLeaveApi.getState()
  } catch (e: any) {
    console.warn('[dingtalk-leave] loadState failed:', e?.message)
  }
}

const stats = ref<{ total: number; thisMonth: number } | null>(null)
async function loadStats() {
  try {
    const r = await dingtalkLeaveApi.getStats()
    stats.value = r.data
  } catch (e: any) {
    console.warn('[dingtalk-leave] loadStats failed:', e?.message)
  }
}

async function pollLog(logId: number, maxAttempts = 60): Promise<DingTalkLeaveSyncLog | null> {
  for (let i = 0; i < maxAttempts; i++) {
    await new Promise((r) => setTimeout(r, 1500))
    try {
      const data = await dingtalkLeaveApi.listLogs(0, 1)
      const log = data.content.find((x) => x.id === logId)
      if (log && log.status !== 'RUNNING') {
        return log
      }
    } catch {
      // 忽略单次失败
    }
  }
  return null
}

async function trigger(fullSync: boolean) {
  const mode = fullSync ? '全量' : '增量'
  try {
    await ElMessageBox.confirm(
      `即将触发${mode}同步,请休假数据将从钉钉拉取并入库。${fullSync ? '\n(全量会重拉近 365 天数据)' : ''}`,
      '确认同步',
      { type: 'warning' },
    )
  } catch {
    return
  }

  syncing.value = true
  try {
    const log = await dingtalkLeaveApi.triggerSync('admin', fullSync)
    ElMessage.info(`同步任务已启动 (日志 #${log.id}),等待完成...`)
    const finalLog = await pollLog(log.id)
    syncing.value = false

    if (!finalLog) {
      ElMessage.warning('同步超时未完成,稍后查看日志')
    } else if (finalLog.status === 'SUCCESS') {
      ElMessage.success(
        `同步成功: 拉取 ${finalLog.fetched} / 新增 ${finalLog.createdCount} / 更新 ${finalLog.updatedCount} / 失效 ${finalLog.deletedCount}`,
      )
    } else {
      ElMessage.error(`同步失败: ${finalLog.errorMessage ?? '未知错误'}`)
    }
    await Promise.all([loadState(), loadStats(), loadList(), loadLogs()])
  } catch (e: any) {
    syncing.value = false
    ElMessage.error(`同步失败: ${e?.message ?? '未知错误'}`)
  }
}

// ============================================================
// 同步日志
// ============================================================
const logs = ref<DingTalkLeaveSyncLog[]>([])
const logsLoading = ref(false)

async function loadLogs() {
  logsLoading.value = true
  try {
    const data = await dingtalkLeaveApi.listLogs(0, 10)
    logs.value = data.content
  } catch (e: any) {
    console.warn('[dingtalk-leave] loadLogs failed:', e?.message)
  } finally {
    logsLoading.value = false
  }
}

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

function statusTag(s: string | null | undefined): 'success' | 'warning' | 'danger' | 'info' {
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

function leaveStatusTag(s: string | null | undefined): 'success' | 'warning' | 'danger' | 'info' {
  if (!s) return 'info'
  // 钉钉请休假状态: 1=审批中, 2=已通过, 3=已驳回, 4=已撤销
  switch (s) {
    case '2':
      return 'success'
    case '3':
      return 'danger'
    case '4':
      return 'info'
    default:
      return 'warning'
  }
}

function leaveStatusText(s: string | null | undefined): string {
  if (!s) return '-'
  return ({ '1': '审批中', '2': '已通过', '3': '已驳回', '4': '已撤销' } as Record<string, string>)[s] ?? s
}

// ============================================================
// 详情对话框
// ============================================================
const detail = ref<{ visible: boolean; leave: DingTalkLeave | null }>({ visible: false, leave: null })

async function showDetail(row: DingTalkLeave) {
  try {
    const full = await dingtalkLeaveApi.get(row.id)
    detail.value = { visible: true, leave: full }
  } catch (e: any) {
    ElMessage.error(e?.message ?? '加载详情失败')
  }
}

// ============================================================
// 自动刷新
// ============================================================
let timer: ReturnType<typeof setInterval> | null = null

function startAutoRefresh() {
  stopAutoRefresh()
  timer = setInterval(() => {
    loadState()
    loadLogs()
  }, 10000)
}

function stopAutoRefresh() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

onMounted(async () => {
  await Promise.all([loadState(), loadStats(), loadList(), loadLogs()])
  startAutoRefresh()
})

onUnmounted(stopAutoRefresh)

// 监听分页变化
watch([page, size], () => {
  loadList()
})
</script>

<template>
  <div style="padding: 16px">
    <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px">
      <h2 style="margin: 0">📅 钉钉请休假同步</h2>
      <div style="display: flex; gap: 8px">
        <el-button :icon="Refresh" @click="loadList" :loading="loading">刷新列表</el-button>
        <el-button type="primary" :icon="VideoPlay" @click="trigger(false)" :loading="syncing">
          增量同步
        </el-button>
        <el-button type="warning" :icon="Document" @click="trigger(true)" :loading="syncing">
          全量同步
        </el-button>
      </div>
    </div>

    <!-- 同步状态卡片 -->
    <el-row :gutter="12" style="margin-bottom: 12px">
      <el-col :span="6">
        <el-card shadow="hover">
          <div style="color: #909399; font-size: 12px">上次同步时间</div>
          <div style="font-size: 20px; margin-top: 4px">
            {{ fmtTime(state?.lastSyncTime) }}
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div style="color: #909399; font-size: 12px">累计拉取</div>
          <div style="font-size: 20px; margin-top: 4px">{{ state?.lastTotal ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div style="color: #909399; font-size: 12px">本次新增 / 更新</div>
          <div style="font-size: 20px; margin-top: 4px">
            <span style="color: #67c23a">{{ state?.lastCreated ?? 0 }}</span>
            <span style="color: #909399; margin: 0 6px">/</span>
            <span style="color: #409eff">{{ state?.lastUpdated ?? 0 }}</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div style="color: #909399; font-size: 12px">请休假记录数 (本月 / 总)</div>
          <div style="font-size: 20px; margin-top: 4px">
            <span style="color: #e6a23c">{{ stats?.thisMonth ?? 0 }}</span>
            <span style="color: #909399; margin: 0 6px">/</span>
            <span>{{ stats?.total ?? 0 }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-alert type="info" :closable="false" style="margin-bottom: 12px">
      增量同步只会拉取上次同步时间之后的变更;全量同步会拉取近 365
      天数据并刷新全部记录。后台异步执行,完成前页面会保持轮询。
    </el-alert>

    <el-tabs>
      <!-- 请休假列表 -->
      <el-tab-pane label="请休假记录">
        <div style="margin-bottom: 12px">
          <el-input
            v-model="keyword"
            placeholder="搜索姓名 / userid / 类型 / 事由"
            :prefix-icon="Search"
            clearable
            style="width: 320px"
          />
        </div>
        <el-table
          v-loading="loading"
          :data="filteredLeaves"
          border
          stripe
          style="width: 100%"
          empty-text="暂无请休假数据,请先触发同步"
        >
          <el-table-column type="index" label="#" width="50" />
          <el-table-column prop="userName" label="姓名" min-width="100" />
          <el-table-column prop="userid" label="钉钉 userid" min-width="140" />
          <el-table-column prop="leaveType" label="类型" min-width="100" />
          <el-table-column label="开始时间" min-width="160">
            <template #default="{ row }">{{ fmtTime(row.startTime) }}</template>
          </el-table-column>
          <el-table-column label="结束时间" min-width="160">
            <template #default="{ row }">{{ fmtTime(row.endTime) }}</template>
          </el-table-column>
          <el-table-column label="时长" min-width="100">
            <template #default="{ row }">
              <span v-if="row.duration">
                {{ row.duration }}
                {{ row.durationUnit === 'percentDay' ? '%天' : row.durationUnit || '小时' }}
              </span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="leaveStatusTag(row.status)" size="small">
                {{ leaveStatusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="���步时间" min-width="160">
            <template #default="{ row }">{{ fmtTime(row.syncedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="showDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          style="margin-top: 12px; justify-content: flex-end"
        />
      </el-tab-pane>

      <!-- 同步日志 -->
      <el-tab-pane label="同步日志">
        <el-table
          v-loading="logsLoading"
          :data="logs"
          border
          stripe
          style="width: 100%"
          empty-text="暂无同步记录"
        >
          <el-table-column prop="id" label="#" width="60" />
          <el-table-column label="开始时间" min-width="160">
            <template #default="{ row }">{{ fmtTime(row.startedAt) }}</template>
          </el-table-column>
          <el-table-column label="结束时间" min-width="160">
            <template #default="{ row }">{{ fmtTime(row.finishedAt) }}</template>
          </el-table-column>
          <el-table-column prop="triggerType" label="触发" width="80" />
          <el-table-column prop="triggeredBy" label="操作人" width="100" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status)" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="syncMode" label="模式" width="100" />
          <el-table-column label="拉取/新增/更新/失效" min-width="220">
            <template #default="{ row }">
              {{ row.fetched }} / {{ row.createdCount }} / {{ row.updatedCount }} / {{ row.deletedCount }}
            </template>
          </el-table-column>
          <el-table-column label="错误" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.errorMessage || '-' }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 详情对话框 -->
    <el-dialog v-model="detail.visible" title="请休假详情" width="640px">
      <el-descriptions v-if="detail.leave" :column="2" border>
        <el-descriptions-item label="姓名">{{ detail.leave.userName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="钉钉 userid">{{ detail.leave.userid }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ detail.leave.leaveType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="leaveStatusTag(detail.leave.status)" size="small">
            {{ leaveStatusText(detail.leave.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ fmtTime(detail.leave.startTime) }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ fmtTime(detail.leave.endTime) }}</el-descriptions-item>
        <el-descriptions-item label="时长" :span="2">
          {{ detail.leave.duration ?? '-' }} {{ detail.leave.durationUnit || '' }}
        </el-descriptions-item>
        <el-descriptions-item label="事由" :span="2">{{ detail.leave.reason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审批人">{{ detail.leave.approverUserid || '-' }}</el-descriptions-item>
        <el-descriptions-item label="钉钉更新时间">
          {{ fmtTime(detail.leave.dingtalkUpdatedAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="同步时间">{{ fmtTime(detail.leave.syncedAt) }}</el-descriptions-item>
        <el-descriptions-item label="leaveId">{{ detail.leave.leaveId }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>
