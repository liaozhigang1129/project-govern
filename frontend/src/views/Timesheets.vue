<script setup lang="ts">
/**
 * P2.C 工时周报录入页
 */
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Calendar, Check, Plus, Refresh, Document, MagicStick } from '@element-plus/icons-vue'
import { timesheetApi, type Entry, type TimesheetDetail, type TimesheetSummary, type AutoFillResult, type DayFillResult } from '@/api/timesheet'
import api, { type ProjectCard } from '@/api/client'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

// ---------- 当前用户(PM 录自己 / PMO/EXEC 录他人) ----------
// P3 修复:role 形状兼容 — login 响应是扁平字符串 role,
// 但 /auth/me 与 storage 恢复后会是 primaryRole.code
function readRole(): string {
  const u: any = auth.user
  if (!u) return ''
  return (u.role ?? u.primaryRole?.code ?? '') as string
}
const isApprover = computed(() => {
  const r = readRole()
  return r === 'PMO_ADMIN' || r === 'ADMIN' || r === 'EXEC'
})
const editUserId = ref<number | null>(null)
const targetUserId = computed(() => isApprover.value && editUserId.value ? editUserId.value : (auth.user?.id ?? 0))

// P2.A 工时审批:Tab 切换 + 待审批列表
// P3 修复:非审批者默认进"我的工时" Tab,避免看到空 pane
const activeTab = ref<'mine' | 'approval'>(isApprover.value ? 'approval' : 'mine')
const pendingList = ref<TimesheetSummary[]>([])
const pendingCount = computed(() => pendingList.value.length)

// ---------- 选周 ----------
function mondayOf(d: Date): Date {
  const r = new Date(d)
  const day = r.getDay()  // 0=Sun
  const diff = day === 0 ? -6 : 1 - day
  r.setDate(r.getDate() + diff)
  r.setHours(0, 0, 0, 0)
  return r
}
function fmt(d: Date): string {
  return d.toISOString().slice(0, 10)
}
const thisMonday = mondayOf(new Date())
const weekStart = ref<string>(fmt(thisMonday))
const weekEnd = computed(() => {
  const d = new Date(weekStart.value)
  d.setDate(d.getDate() + 6)
  return fmt(d)
})
const weekDays = computed<Date[]>(() => {
  const out: Date[] = []
  for (let i = 0; i < 7; i++) {
    const d = new Date(weekStart.value)
    d.setDate(d.getDate() + i)
    out.push(d)
  }
  return out
})
function shiftWeek(delta: number) {
  const d = new Date(weekStart.value)
  d.setDate(d.getDate() + delta * 7)
  weekStart.value = fmt(d)
  void loadCurrent()
}

// ---------- 数据加载 ----------
const current = ref<TimesheetDetail | null>(null)
const history = ref<TimesheetSummary[]>([])
const historyTotal = ref(0)
const histPage = ref(1)
const histSize = ref(10)
const loading = ref(false)
const projects = ref<ProjectCard[]>([])

async function loadProjects() {
  try {
    projects.value = (await api.get('/projects') as ProjectCard[]) ?? []
  } catch { /* ignore */ }
}

async function ensureCurrentWeek() {
  loading.value = true
  try {
    const detail = await timesheetApi.create(targetUserId.value, weekStart.value)
    current.value = detail
  } catch (e: any) {
    ElMessage.error(e.message ?? '加载周报失败')
    current.value = null
  } finally {
    loading.value = false
  }
}

async function loadCurrent() {
  // 试 GET /timesheets/{id} — 已有就拉,没有就建
  // 简化:用 create 的"幂等"行为(后端已实现),它会返回已存在或新建
  await ensureCurrentWeek()
}

async function loadHistory() {
  loading.value = true
  try {
    const res = await timesheetApi.list({
      userId: targetUserId.value,
      page: histPage.value,
      size: histSize.value
    })
    history.value = res.content
    historyTotal.value = res.totalElements
  } catch (e: any) {
    ElMessage.error(e.message ?? '加载历史失败')
  } finally {
    loading.value = false
  }
}

/** P2.A: 加载 SUBMITTED 状态的全员周报(待我审批) */
async function loadPending() {
  if (!isApprover.value) return
  loading.value = true
  try {
    const res = await timesheetApi.list({ status: 'SUBMITTED', page: 0, size: 100 })
    pendingList.value = res.content
  } catch (e: any) {
    ElMessage.error(e.message ?? '加载待审批失败')
  } finally {
    loading.value = false
  }
}

/** P2.A: 查看待审批周报详情(切到"我的工时" tab + 加载该周) */
async function viewPending(row: TimesheetSummary) {
  activeTab.value = 'mine'
  weekStart.value = row.weekStart
  editUserId.value = row.userId
  await loadCurrent()
}

/** 监听 activeTab:切到 approval 自动加载 */
watch(activeTab, (t) => {
  if (t === 'approval') void loadPending()
})

onMounted(async () => {
  await loadProjects()
  // P3: 如果是审批者,默认进 approval Tab → 先把 pending 拉起来
  if (isApprover.value) {
    await loadPending()
  } else {
    await loadCurrent()
  }
  await loadHistory()
})

// ---------- 编辑行 ----------
function emptyRow(date: string): Entry {
  return { workDate: date, projectId: 0, milestoneId: 0, hours: 0, description: '' }
}
function addRow(date: string) {
  if (!current.value) return
  if (!current.value.entries) current.value.entries = []
  current.value.entries.push(emptyRow(date))
}
function addAllDays() {
  if (!current.value) return
  current.value.entries = weekDays.value.map(d => emptyRow(fmt(d)))
  ElMessage.info('已填充 7 天空白行')
}
function removeRow(idx: number) {
  current.value?.entries?.splice(idx, 1)
}
function dateLabel(d: Date) {
  const wd = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${fmt(d)} 周${wd}`
}

// ---------- 总工时 ----------
const totalHours = computed(() => {
  if (!current.value?.entries) return 0
  return current.value.entries.reduce((s, e) => s + (Number(e.hours) || 0), 0)
})
const byDay = computed(() => {
  const m = new Map<string, number>()
  for (const e of current.value?.entries ?? []) {
    m.set(e.workDate, (m.get(e.workDate) ?? 0) + (Number(e.hours) || 0))
  }
  return m
})

// ---------- 保存 ----------
const saving = ref(false)
async function save() {
  if (!current.value) return
  saving.value = true
  try {
    // 校验
    const valid = current.value.entries.filter(e => e.projectId && e.hours > 0)
    if (valid.length === 0) {
      ElMessage.warning('请至少录入一行')
      saving.value = false
      return
    }
    const cleaned = valid.map(e => ({
      id: e.id,
      workDate: e.workDate,
      projectId: e.projectId,
      milestoneId: e.milestoneId || undefined,
      hours: Number(e.hours),
      description: e.description ?? ''
    }))
    const updated = await timesheetApi.upsertEntries(current.value.id, cleaned)
    current.value = updated
    ElMessage.success(`已保存 ${cleaned.length} 行,合计 ${updated.totalHours}h`)
    await loadHistory()
  } catch (e: any) {
    ElMessage.error(e.message ?? '保存失败')
  } finally {
    saving.value = false
  }
}

async function submit() {
  if (!current.value) return
  if (current.value.status !== 'DRAFT') {
    ElMessage.warning(`当前状态 ${current.value.status} 不能提交`)
    return
  }
  try {
    await ElMessageBox.confirm('提交后 PMO/EXEC 即可审批,确定?', '提交周报', { type: 'warning' })
  } catch { return }
  saving.value = true
  try {
    const updated = await timesheetApi.submit(current.value.id, '')
    current.value = updated
    ElMessage.success('已提交,等待审批')
    await loadHistory()
  } catch (e: any) {
    ElMessage.error(e.message ?? '提交失败')
  } finally {
    saving.value = false
  }
}

async function approve(id: number) {
  try {
    await ElMessageBox.confirm('确认批准该周报?', '审批', { type: 'success' })
  } catch { return }
  try {
    await timesheetApi.approve(id)
    ElMessage.success('已批准')
    await loadHistory()
    await loadPending()   // P2.A: 同步刷新待审批 tab
  } catch (e: any) {
    ElMessage.error(e.message ?? '审批失败')
  }
}

function statusTag(s: string) {
  return { DRAFT: 'info', SUBMITTED: 'warning', APPROVED: 'success' }[s] ?? 'info'
}

// ============================================================
//  V4.34 工时自动填报
// ============================================================
const autoFillDialog = ref(false)
const autoFillResult = ref<AutoFillResult | null>(null)
const autoFillOverwrite = ref(false)
const autoFillDryRun = ref(false)
const autoFillLoading = ref(false)
const autoFillBatchLoading = ref(false)
const autoFillBatchDialog = ref(false)
const autoFillBatchResult = ref<any>(null)

const reasonLabel: Record<string, string> = {
  PM: '我是 PM',
  BU: '我 BU 的项目',
  PL: '我 PL 的项目',
  DEPT_GROUP: '我部门项目组',
  WBS: '我分配的 WBS 任务',
  PLACEHOLDER: '无候选项目(占位)'
}

const reasonTagType: Record<string, string> = {
  PM: 'danger', BU: 'warning', PL: 'success', DEPT_GROUP: 'info', WBS: '', PLACEHOLDER: 'info'
}

async function doAutoFill() {
  if (!targetUserId.value) {
    ElMessage.warning('请选择目标用户')
    return
  }
  autoFillLoading.value = true
  try {
    const r = await timesheetApi.autoFill({
      userId: targetUserId.value,
      weekStart: weekStart.value,
      dryRun: autoFillDryRun.value,
      overwrite: autoFillOverwrite.value
    })
    autoFillResult.value = r
    autoFillDialog.value = true
    if (!r.dryRun) {
      // 写库成功 — 刷新当前周报
      await loadCurrent()
      await loadHistory()
    }
  } catch (e: any) {
    ElMessage.error(e.message ?? '自动填报失败')
  } finally {
    autoFillLoading.value = false
  }
}

async function doAutoFillBatch() {
  try {
    await ElMessageBox.confirm(
      `批量自动填报 ${weekStart.value} 全员, 需 ${autoFillOverwrite.value ? '覆盖' : '跳过'}已存在 entry. 继续?`,
      '批量自动填报', { type: 'warning' }
    )
  } catch { return }
  autoFillBatchLoading.value = true
  try {
    const r = await timesheetApi.autoFillBatch({
      weekStart: weekStart.value,
      dryRun: autoFillDryRun.value,
      overwrite: autoFillOverwrite.value
    })
    autoFillBatchResult.value = r
    autoFillBatchDialog.value = true
    if (!autoFillDryRun.value) {
      await loadHistory()
    }
  } catch (e: any) {
    ElMessage.error(e.message ?? '批量自动填报失败')
  } finally {
    autoFillBatchLoading.value = false
  }
}
</script>

<template>
  <div class="page">
    <el-tabs v-model="activeTab" class="ts-tabs">
      <el-tab-pane label="我的工时" name="mine">
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px">
          <div>
            <el-icon style="vertical-align: middle"><Calendar /></el-icon>
            <span style="font-size: 16px; font-weight: 600">工时周报</span>
            <span style="color: #909399; margin-left: 8px">每周维度,PM 录入,PMO/EXEC 审批</span>
          </div>
          <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
            <el-date-picker
              v-model="weekStart"
              type="date"
              placeholder="选择周一"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              style="width: 160px"
              @change="loadCurrent"
            />
            <el-button-group>
              <el-button @click="shiftWeek(-1)">← 上周</el-button>
              <el-button @click="weekStart = fmt(thisMonday); loadCurrent()">本周</el-button>
              <el-button @click="shiftWeek(1)">下周 →</el-button>
            </el-button-group>
            <el-select
              v-if="isApprover"
              v-model="editUserId"
              placeholder="切换为他人"
              clearable
              style="width: 200px"
              @change="loadCurrent(); loadHistory()"
            >
              <el-option :value="auth.user?.id ?? 0" :label="`${auth.user?.fullName ?? '我'} (自己)`" />
              <el-option v-for="p in (projects as any[])" :key="p.id" :value="p.pmUserId ?? p.id" :label="`${p.name}`" />
            </el-select>
          </div>
        </div>
      </template>

      <div v-if="current" style="margin-bottom: 12px; display: flex; gap: 16px; flex-wrap: wrap; align-items: center">
        <el-tag :type="statusTag(current.status) as any" size="large">{{ current.status }}</el-tag>
        <span>用户 <b>{{ current.userName }}</b></span>
        <span>周 {{ current.weekStart }} ~ {{ current.weekEnd }}</span>
        <span>合计 <b :style="{ color: totalHours > 60 ? '#f56c6c' : totalHours > 40 ? '#e6a23c' : '#67c23a' }">{{ totalHours }}h</b></span>
        <el-tag v-for="(d, i) in weekDays" :key="i" :type="(byDay.get(fmt(d)) ?? 0) > 8 ? 'danger' : 'info'" effect="plain">
          周{{ ['日','一','二','三','四','五','六'][d.getDay()] }} {{ byDay.get(fmt(d)) ?? 0 }}h
        </el-tag>
      </div>

      <el-table
        v-if="current"
        :data="current.entries ?? []"
        border
        style="width: 100%"
        empty-text="本周无明细,点击下方按钮添加"
      >
        <el-table-column label="日期" width="160">
          <template #default="{ row }">
            <el-date-picker v-model="row.workDate" type="date" value-format="YYYY-MM-DD" format="YYYY-MM-DD" style="width: 150px" :disabled="current.status !== 'DRAFT'" />
          </template>
        </el-table-column>
        <el-table-column label="项目" min-width="200">
          <template #default="{ row }">
            <el-select v-model="row.projectId" filterable placeholder="选项目" style="width: 100%" :disabled="current.status !== 'DRAFT'">
              <el-option v-for="p in projects" :key="p.id" :value="p.id" :label="`${p.code ?? ''} ${p.name}`" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="工时" width="120">
          <template #default="{ row }">
            <el-input-number v-model="row.hours" :min="0" :max="24" :step="0.5" :disabled="current.status !== 'DRAFT'" style="width: 110px" />
          </template>
        </el-table-column>
        <el-table-column label="描述" min-width="220">
          <template #default="{ row }">
            <el-input v-model="row.description" placeholder="做了什么" :disabled="current.status !== 'DRAFT'" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" v-if="current.status === 'DRAFT'">
          <template #default="{ $index }">
            <el-button type="danger" link @click="removeRow($index)">删</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!--
        V4.34 自动填报按钮区
        - 试算(dryRun=true)对所有 current 状态开放, 仅在前端预演结果, 不写库
        - 真实写库(非试算)由后端 TimesheetAutoFillService 校验: 仅 DRAFT/REJECTED 接受写库, 其他状态返回 409
        - 批量按钮仅审批者(PMO_ADMIN/ADMIN/EXEC)可见
      -->
      <div style="margin-top: 12px; display: flex; gap: 8px; flex-wrap: wrap; align-items: center">
        <el-checkbox v-model="autoFillOverwrite">覆盖已存在</el-checkbox>
        <el-checkbox v-model="autoFillDryRun">仅试算 (不写)</el-checkbox>
        <el-button type="warning" :icon="MagicStick" :loading="autoFillLoading" @click="doAutoFill">⚡ 自动填报</el-button>
        <el-button v-if="isApprover" type="danger" :icon="MagicStick" :loading="autoFillBatchLoading" @click="doAutoFillBatch">⚡ 批量自动填报 (全员)</el-button>
      </div>

      <div v-if="current?.status === 'DRAFT'" style="margin-top: 8px; display: flex; gap: 8px; flex-wrap: wrap; align-items: center">
        <el-button @click="addAllDays" :icon="Plus">填充 7 天空白行</el-button>
        <el-button @click="addRow(fmt(weekDays[0]))">+ 周一</el-button>
        <el-button @click="addRow(fmt(weekDays[1]))">+ 周二</el-button>
        <el-button @click="addRow(fmt(weekDays[2]))">+ 周三</el-button>
        <el-button @click="addRow(fmt(weekDays[3]))">+ 周四</el-button>
        <el-button @click="addRow(fmt(weekDays[4]))">+ 周五</el-button>
        <el-button @click="addRow(fmt(weekDays[5]))">+ 周六</el-button>
        <el-button @click="addRow(fmt(weekDays[6]))">+ 周日</el-button>
        <div style="flex: 1"></div>
        <el-button type="primary" :icon="Check" :loading="saving" @click="save">保存草稿</el-button>
        <el-button type="success" :icon="Check" :loading="saving" @click="submit">提交审批</el-button>
      </div>
    </el-card>

    <el-card style="margin-top: 16px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span><el-icon><Document /></el-icon> 历史周报 ({{ historyTotal }})</span>
          <el-button :icon="Refresh" link @click="loadHistory">刷新</el-button>
        </div>
      </template>
      <el-table :data="history" v-loading="loading" border>
        <el-table-column prop="weekStart" label="周" width="180">
          <template #default="{ row }">{{ row.weekStart }} ~ {{ row.weekEnd }}</template>
        </el-table-column>
        <el-table-column prop="userName" label="用户" width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status) as any" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalHours" label="工时" width="80" />
        <el-table-column prop="projectCount" label="项目" width="70" />
        <el-table-column prop="entryCount" label="行数" width="70" />
        <el-table-column prop="submittedAt" label="提交时间" width="180">
          <template #default="{ row }">{{ row.submittedAt ? row.submittedAt.replace('T',' ').slice(0,19) : '-' }}</template>
        </el-table-column>
        <el-table-column prop="approverName" label="审批人" width="120" />
        <el-table-column label="操作" width="180" v-if="isApprover">
          <template #default="{ row }">
            <el-button v-if="row.status === 'SUBMITTED'" type="success" size="small" @click="approve(row.id)">批准</el-button>
            <el-button size="small" link @click="current = { ...row, entries: [] } as any; weekStart = row.weekStart">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="histPage"
        v-model:page-size="histSize"
        :total="historyTotal"
        layout="total, prev, pager, next, sizes"
        :page-sizes="[5, 10, 20]"
        style="margin-top: 12px; justify-content: flex-end"
        @current-change="loadHistory"
        @size-change="loadHistory"
      />
    </el-card>
      </el-tab-pane>

      <el-tab-pane v-if="isApprover" :label="`待我审批 (${pendingCount})`" name="approval">
        <el-card>
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center; gap: 8px">
              <span><el-icon><Check /></el-icon> 待我审批 — SUBMITTED 状态(全员)</span>
              <div style="display: flex; gap: 8px">
                <el-button size="small" @click="$router.push('/timesheets/approvals')">打开独立审批中心 →</el-button>
                <el-button :icon="Refresh" link @click="loadPending">刷新</el-button>
              </div>
            </div>
          </template>
          <el-table :data="pendingList" v-loading="loading" border empty-text="当前没有待审批的周报">
            <el-table-column prop="weekStart" label="周" width="180">
              <template #default="{ row }">{{ row.weekStart }} ~ {{ row.weekEnd }}</template>
            </el-table-column>
            <el-table-column prop="userName" label="提交人" width="120" />
            <el-table-column prop="totalHours" label="工时" width="80" />
            <el-table-column prop="projectCount" label="项目" width="70" />
            <el-table-column prop="entryCount" label="行数" width="70" />
            <el-table-column prop="submittedAt" label="提交时间" width="180">
              <template #default="{ row }">{{ row.submittedAt ? row.submittedAt.replace('T',' ').slice(0,19) : '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" size="small" @click="viewPending(row)">查看</el-button>
                <el-button type="success" size="small" @click="approve(row.id)">批准</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- V4.34: 自动填报结果弹窗 -->
    <el-dialog v-model="autoFillDialog" title="⚡ 自动填报结果" width="800">
      <div v-if="autoFillResult">
        <el-alert
          :type="autoFillResult.placeholderDays > 3 ? 'warning' : 'success'"
          :closable="false"
          style="margin-bottom: 12px"
        >
          <template #title>
            <span>{{ autoFillResult.summary }}</span>
          </template>
        </el-alert>
        <div style="display: flex; gap: 16px; margin-bottom: 12px; flex-wrap: wrap">
          <el-statistic title="填充天数" :value="autoFillResult.filledDays" />
          <el-statistic title="占位天数" :value="autoFillResult.placeholderDays" />
          <el-statistic title="跳过天数" :value="autoFillResult.skippedDays" />
          <el-statistic title="合计工时" :value="autoFillResult.totalHours" :precision="2" suffix="h" />
          <el-tag v-if="autoFillResult.dryRun" type="info" size="large">DRY RUN</el-tag>
          <el-tag v-else-if="autoFillResult.overwrite" type="warning" size="large">已覆盖</el-tag>
          <el-tag v-else type="success" size="large">已写入</el-tag>
        </div>
        <el-table :data="autoFillResult.days" border size="small" max-height="400">
          <el-table-column prop="workDate" label="日期" width="110" />
          <el-table-column label="考勤" width="80">
            <template #default="{ row }">{{ row.workDurationMinutes ?? 0 }}min</template>
          </el-table-column>
          <el-table-column label="请假" width="70">
            <template #default="{ row }">{{ row.leaveHours }}h</template>
          </el-table-column>
          <el-table-column label="命中规则" width="180">
            <template #default="{ row }">
              <el-tag :type="(reasonTagType[row.matchReason] || 'info') as any" size="small">
                {{ reasonLabel[row.matchReason] || row.matchReason }} (P{{ row.priority }})
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="工时" width="70">
            <template #default="{ row }">
              <span :style="{ color: row.skipped ? '#909399' : '#67c23a' }">
                {{ row.hours }}h
              </span>
              <el-tag v-if="row.skipped" type="info" size="small" effect="plain">跳过</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="description" label="说明" />
        </el-table>
      </div>
    </el-dialog>

    <!-- V4.34: 批量自动填报结果弹窗 -->
    <el-dialog v-model="autoFillBatchDialog" title="⚡ 批量自动填报结果" width="700">
      <div v-if="autoFillBatchResult">
        <div style="display: flex; gap: 16px; margin-bottom: 12px; flex-wrap: wrap">
          <el-statistic title="请求人数" :value="autoFillBatchResult.requested" />
          <el-statistic title="成功" :value="autoFillBatchResult.successCount" />
          <el-statistic title="跳过" :value="autoFillBatchResult.skippedCount" />
          <el-statistic title="失败" :value="autoFillBatchResult.errorCount" />
        </div>
        <el-table :data="autoFillBatchResult.results" border size="small" max-height="400">
          <el-table-column prop="userName" label="用户" width="120" />
          <el-table-column prop="userId" label="ID" width="60" />
          <el-table-column label="填充" width="70">
            <template #default="{ row }">{{ row.filledDays }}</template>
          </el-table-column>
          <el-table-column label="占位" width="70">
            <template #default="{ row }">{{ row.placeholderDays }}</template>
          </el-table-column>
          <el-table-column label="跳过" width="70">
            <template #default="{ row }">{{ row.skippedDays }}</template>
          </el-table-column>
          <el-table-column label="工时" width="80">
            <template #default="{ row }">{{ row.totalHours }}h</template>
          </el-table-column>
          <el-table-column prop="summary" label="摘要" />
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>
