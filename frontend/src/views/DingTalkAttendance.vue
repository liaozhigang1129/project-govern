<script setup lang="ts">
/**
 * V4.34: 时长格式化 (分钟 -> "Xh Ym")
 */
function formatDuration(min: number): string {
  if (min == null) return '-'
  const h = Math.floor(min / 60)
  const m = min % 60
  if (h === 0) return `${m}m`
  if (m === 0) return `${h}h`
  return `${h}h ${m}m`
}

/**
 * V4.33: 解析新表 rawRecordIds (JSON 数组字符串) → string[]
 * - 时间范围选择 + 手动同步 (异步)
 * - 查看同步状态 / 日志 / 考勤列表
 * - 轮询直到 RUNNING 结束
 * - 默认每周日凌晨跑最近 2 周 (system_config integration.dingtalk.attendance_cron)
 */
import { onMounted, onUnmounted, ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search, VideoPlay } from '@element-plus/icons-vue'
import {
  dingtalkAttendanceApi,
  type DingTalkAttendanceDaily,
  type DingTalkAttendanceSyncState,
  type DingTalkAttendanceSyncLog,
} from '@/api/dingtalkAttendance'

// ============================================================
// 列表
// ============================================================
const records = ref<DingTalkAttendanceDaily[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

const keyword = ref('')
const filteredRecords = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return records.value
  return records.value.filter(
    (r: any) =>
      (r.userName || '').toLowerCase().includes(kw) ||
      (r.userid || '').toLowerCase().includes(kw) ||
      (r.checkType || '').toLowerCase().includes(kw) ||
      (r.timeResult || '').toLowerCase().includes(kw) ||
      (r.locationResult || '').toLowerCase().includes(kw) ||
      (r.workDate || '').toLowerCase().includes(kw),
  )
})

// V4.33+ 筛选
const filterDateFrom = ref('')
const filterDateTo = ref('')
const filterUseridKeyword = ref('')
const filterIsAbnormal = ref<boolean | null>(null)

function buildFilters() {
  return {
    dateFrom: filterDateFrom.value || undefined,
    dateTo: filterDateTo.value || undefined,
    useridKeyword: filterUseridKeyword.value || undefined,
    isAbnormal: filterIsAbnormal.value === null ? undefined : filterIsAbnormal.value,
  }
}

function resetFilters() {
  filterDateFrom.value = ''
  filterDateTo.value = ''
  filterUseridKeyword.value = ''
  filterIsAbnormal.value = null
  page.value = 1
  loadList()
}

async function loadList() {
  loading.value = true
  try {
    const data = await dingtalkAttendanceApi.list(page.value - 1, size.value, buildFilters())
    records.value = data.content
    total.value = data.totalElements
  } catch (e: any) {
    ElMessage.error(e?.message ?? '加载考勤列表失败')
  } finally {
    loading.value = false
  }
}

// ============================================================
// 同步状态 + 触发
// ============================================================
const state = ref<DingTalkAttendanceSyncState | null>(null)
// 时间范围选择 — 默认近 7 天 (钉钉 listRecord 单次上限 7 天)
//   V4.36: 后端自动按 7 天分片, 前端不限 7 天, 但建议 ≤ 30 天避免过慢
const ATTENDANCE_RECOMMEND_DAYS = 7
function today() {
  return new Date()
}
function daysAgo(n: number) {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return d
}
const dateRange = ref<[Date, Date]>([daysAgo(ATTENDANCE_RECOMMEND_DAYS), today()])

// O1+O2: 同步控制 (顶部卡片)
const syncingButton = ref(false)
// V4.36: 实时同步进度 (当前日志 id + 累计 fetched/created/updated/days)
const currentSyncLogId = ref<number | null>(null)
const currentSyncProgress = ref<{ fetched: number; created: number; updated: number; days: number }>({
  fetched: 0,
  created: 0,
  updated: 0,
  days: 0,
})
const canTrigger = computed(() => !syncingButton.value && !(state.value as any)?.running)
const runningCount = computed(() => logs.value.filter((l) => l.status === 'RUNNING').length)

async function loadState() {
  try {
    state.value = await dingtalkAttendanceApi.getState()
  } catch (e: any) {
    console.warn('[attendance] loadState failed:', e?.message)
  }
}

const stats = ref<{ total: number; thisMonth: number } | null>(null)
async function loadStats() {
  try {
    const r = await dingtalkAttendanceApi.getStats()
    stats.value = r.data
  } catch (e: any) {
    console.warn('[attendance] loadStats failed:', e?.message)
  }
}

async function pollLog(logId: number, maxAttempts = 60): Promise<DingTalkAttendanceSyncLog | null> {
  for (let i = 0; i < maxAttempts; i++) {
    await new Promise((r) => setTimeout(r, 1500))
    try {
      const data = await dingtalkAttendanceApi.listLogs(0, 5)
      const log = data.content.find((x) => x.id === logId)
      if (log) {
        // V4.36: 实时刷新进度 (同步进行中拉取最新 fetched/created)
        if (currentSyncLogId.value === logId) {
          currentSyncProgress.value = {
            fetched: log.fetched,
            created: log.createdCount,
            updated: log.updatedCount,
            days: currentSyncProgress.value.days,
          }
        }
        if (log.status !== 'RUNNING') {
          return log
        }
      }
    } catch {
      // 忽略单次失败
    }
  }
  return null
}

async function trigger() {
  const [fromD, toD] = dateRange.value
  if (!fromD || !toD) {
    ElMessage.warning('请选择时间范围')
    return
  }
  if (fromD > toD) {
    ElMessage.warning('开始日期不能大于结束日期')
    return
  }
  const fmt = (d: Date) => d.toISOString().slice(0, 10)
  const days = Math.ceil((toD.getTime() - fromD.getTime()) / 86400000) + 1
  try {
    await ElMessageBox.confirm(
      `即将同步 ${fmt(fromD)} 至 ${fmt(toD)} (共 ${days} 天) 的考勤数据。\n` +
        `后端会自动按 7 天分片拉取,请耐心等待。`,
      '确认同步',
      { type: 'warning' },
    )
  } catch {
    return
  }

  syncingButton.value = true
  currentSyncLogId.value = null
  currentSyncProgress.value = { fetched: 0, created: 0, updated: 0, days }
  try {
    const log = await dingtalkAttendanceApi.triggerSync('admin', fmt(fromD), fmt(toD))
    ElMessage.info(`同步任务已启动 (日志 #${log.id}),等待完成...`)
    currentSyncLogId.value = log.id
    const finalLog = await pollLog(log.id)
    syncingButton.value = false
    currentSyncLogId.value = null

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
    syncingButton.value = false
    currentSyncLogId.value = null
    ElMessage.error(`同步失败: ${e?.message ?? '未知错误'}`)
  }
}

// ============================================================
// 同步日志
// ============================================================
const logs = ref<DingTalkAttendanceSyncLog[]>([])
const logsLoading = ref(false)

async function loadLogs() {
  logsLoading.value = true
  try {
    const data = await dingtalkAttendanceApi.listLogs(0, 10)
    logs.value = data.content
  } catch (e: any) {
    console.warn('[attendance] loadLogs failed:', e?.message)
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
function fmtDate(t: string | null | undefined): string {
  if (!t) return '-'
  return t
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

function resultTag(r: string | null | undefined): 'success' | 'warning' | 'danger' | 'info' {
  switch (r) {
    case 'Normal':
      return 'success'
    case 'Late':
    case 'Early':
      return 'warning'
    case 'SeriousLate':
      return 'danger'
    case 'NotSigned':
      return 'info'
    default:
      return 'info'
  }
}
function resultText(r: string | null | undefined): string {
  return (
    (
      {
        Normal: '正常',
        Late: '迟到',
        Early: '早退',
        SeriousLate: '严重迟到',
        NotSigned: '缺卡',
      } as Record<string, string>
    )[r ?? ''] ??
    r ??
    '-'
  )
}

// ============...[truncated]
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

watch([page, size], () => {
  loadList()
})
</script>

<template>
  <div style="padding: 16px">
    <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px">
      <h2 style="margin: 0">📅 钉钉考勤同步</h2>
      <div style="display: flex; gap: 8px; align-items: center">
        <span style="color: #909399; font-size: 12px">时间范围:</span>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="width: 280px"
        />
        <el-button :icon="Refresh" @click="loadList" :loading="loading">刷新列表</el-button>
        <el-button
          type="primary"
          :icon="VideoPlay"
          @click="trigger"
          :loading="syncingButton"
          :disabled="!canTrigger"
        >
          同步考勤
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
          <div style="color: #909399; font-size: 12px">考勤记录数 (本月 / 总)</div>
          <div style="font-size: 20px; margin-top: 4px">
            <span style="color: #e6a23c">{{ stats?.thisMonth ?? 0 }}</span>
            <span style="color: #909399; margin: 0 6px">/</span>
            <span>{{ stats?.total ?? 0 }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- V4.36: 同步进行中进度条 (替代原 '请耐心等待' 静态文案) -->
    <el-alert v-if="syncingButton" type="warning" :closable="false" style="margin-bottom: 12px" show-icon>
      <template #title>
        同步进行中 (日志 #{{ currentSyncLogId }}) — 已拉取 {{ currentSyncProgress.fetched }} 条, 新增
        {{ currentSyncProgress.created }} / 更新 {{ currentSyncProgress.updated }}, 共
        {{ currentSyncProgress.days }} 天 (后端自动按 7 天分片)
      </template>
    </el-alert>

    <el-alert type="info" :closable="false" style="margin-bottom: 12px">
      定时任务: 每周日 03:00 自动同步最近 14 天 (2 周) 的考勤数据,来自 system_config
      integration.dingtalk.attendance_cron。 手动同步默认近 7 天 (符合钉钉 listRecord 单次 ≤ 7
      天的限制,后端已自动分片)。后台异步执行,完成前页面保持轮询。
    </el-alert>

    <el-tabs>
      <!-- 考勤列表 -->
      <el-tab-pane label="考勤记录">
        <div style="margin-bottom: 12px; display: flex; gap: 8px; flex-wrap: wrap; align-items: center">
          <el-input
            v-model="keyword"
            placeholder="搜索姓名 / userid / 日期 / 异常 / 项目 / 结果"
            :prefix-icon="Search"
            clearable
            style="width: 320px"
          />
          <el-date-picker
            v-model="filterDateFrom"
            type="date"
            placeholder="起始日期"
            value-format="YYYY-MM-DD"
            style="width: 150px"
            @change="page = 1; loadList()"
          />
          <el-date-picker
            v-model="filterDateTo"
            type="date"
            placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 150px"
            @change="page = 1; loadList()"
          />
          <el-input
            v-model="filterUseridKeyword"
            placeholder="userid/姓名"
            clearable
            style="width: 180px"
            @keyup.enter="page = 1; loadList()"
          />
          <el-select
            v-model="filterIsAbnormal"
            placeholder="异常"
            clearable
            style="width: 110px"
            @change="page = 1; loadList()"
          >
            <el-option label="仅异常" :value="true" />
            <el-option label="仅正常" :value="false" />
          </el-select>
          <el-button
            type="primary"
            @click="page = 1; loadList()"
          >
            查询
          </el-button>
          <el-button @click="resetFilters">重置</el-button>
        </div>
        <el-table
          v-loading="loading"
          :data="filteredRecords"
          border
          stripe
          style="width: 100%"
          empty-text="暂无考勤数据,请先触发同步"
        >
          <el-table-column type="index" label="#" width="50" />
          <el-table-column prop="workDate" label="日期" min-width="110" />
          <el-table-column prop="userName" label="姓名" min-width="100" />
          <el-table-column prop="userid" label="钉钉 userid" min-width="140" />
          <el-table-column label="上班" min-width="170">
            <template #default="{ row }">
              <el-tag :type="resultTag(row.onDutyResult)" size="small">
                {{ resultText(row.onDutyResult) }}
              </el-tag>
              <span style="margin-left: 6px">{{ fmtTime(row.onDutyActual) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="下班" min-width="170">
            <template #default="{ row }">
              <el-tag :type="resultTag(row.offDutyResult)" size="small">
                {{ resultText(row.offDutyResult) }}
              </el-tag>
              <span style="margin-left: 6px">{{ fmtTime(row.offDutyActual) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="时长" min-width="80" align="center">
            <template #default="{ row }">
              <span
                v-if="row.workDuration != null"
                :style="{
                  color:
                    row.workDuration > 16 * 60 ? '#f56c6c' : row.workDuration < 60 ? '#e6a23c' : '#67c23a',
                }"
              >
                {{ formatDuration(row.workDuration) }}
              </span>
              <span v-else style="color: #c0c4cc">-</span>
            </template>
          </el-table-column>
          <el-table-column label="异常" min-width="130">
            <template #default="{ row }">
              <template v-if="row.isAbnormal">
                <el-tag type="danger" size="small">异常</el-tag>
                <span style="margin-left: 6px; color: #909399; font-size: 12px">
                  {{ row.abnormalTypes || '-' }}
                </span>
              </template>
              <span v-else style="color: #67c23a">正常</span>
            </template>
          </el-table-column>
          <el-table-column label="补卡" width="60" align="center">
            <template #default="{ row }">
              <span v-if="row.isMakeup" style="color: #409eff">✓</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="项目" min-width="140">
            <template #default="{ row }">
              <span v-if="row.projectNames">{{ row.projectNames }}</span>
              <span v-else style="color: #c0c4cc">未填工时</span>
            </template>
          </el-table-column>
          <el-table-column prop="checkCount" label="打卡数" width="70" align="center" />
          <el-table-column label="同步时间" min-width="150">
            <template #default="{ row }">{{ fmtTime(row.syncedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default>
              <el-button link type="primary" size="small">详情</el-button>
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
      <el-tab-pane>
        <template #label>
          同步日志
          <el-badge
            v-if="runningCount > 0"
            :value="runningCount"
            type="warning"
            :max="9"
            style="margin-left: 6px"
          />
        </template>
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
          <el-table-column label="同步范围" min-width="220">
            <template #default="{ row }">
              <span v-if="row.rangeFrom && row.rangeTo">
                {{ fmtDate(row.rangeFrom) }} ~ {{ fmtDate(row.rangeTo) }}
              </span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="triggerType" label="触发" width="100" />
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
  </div>
</template>
