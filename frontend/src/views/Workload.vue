<script setup lang="ts">
/**
 * P2.D 人员负载矩阵看板
 *
 * - 表格:行 = 人,列 = 周
 * - 单元格:总工时(色阶 0-50h:绿→黄→红)
 * - 顶部 KPI:总人数 / 总工时 / 满载人数(>40h/w)
 * - 下方:单项目工时分布(项目下拉)
 * - 顶部 ② 项目甘特图(P2.B / P1.5 收尾)
 *   - 范围模式:自动(后端自适应)/ 手动(选 from+to)
 *   - 字段映射 / 渲染:见 <GanttView>
 */
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { DataLine, Refresh, DataAnalysis } from '@element-plus/icons-vue'
import {
  workloadApi,
  fetchUserMilestones,
  type UserWeekRow,
  type UserMilestoneList,
  type ProjectLoad,
} from '@/api/workload'
import api, { type ProjectCard } from '@/api/client'
import GanttView, { type GanttResponse } from '@/components/GanttView.vue'
import MilestoneDrawer from '@/components/MilestoneDrawer.vue'

const matrix = ref<UserWeekRow[]>([])
const weekStarts = ref<string[]>([])
const weekEnds = ref<Record<string, string>>({})
const weekCount = ref(0)
const from = ref<string>('')
const to = ref<string>('')
const loading = ref(false)
const projects = ref<ProjectCard[]>([])
const selectedProjectId = ref<number | null>(null)
const projectLoad = ref<ProjectLoad | null>(null)

// ---------- 甘特图(P2.B) ----------
const ganttData = ref<GanttResponse | null>(null)
const ganttLoading = ref(false)
/** 范围模式:auto = 不传 from/to(后端自适应) | manual = 传 from/to */
const ganttMode = ref<'auto' | 'manual'>('auto')
const ganttFrom = ref<string>('')
const ganttTo = ref<string>('')

// 抽屉(里程碑详情)
const drawerVisible = ref(false)
const drawerMilestone = ref<{
  id: number
  projectId: number
  name: string
  planDate: string
  actualDate: string | null
  status: string
  weight: number
} | null>(null)

// 部门多选筛选
import { departmentApi, type Department } from '@/api/gantt'
const departments = ref<Department[]>([])
const selectedDeptIds = ref<number[]>([])

// ---------- 数据加载 ----------
function shiftWeeks(delta: number) {
  if (!from.value) return
  const d = new Date(from.value)
  d.setDate(d.getDate() + delta * 7)
  from.value = d.toISOString().slice(0, 10)
  const d2 = new Date(to.value)
  d2.setDate(d2.getDate() + delta * 7)
  to.value = d2.toISOString().slice(0, 10)
  void load()
}
function mondayOf(d: Date): Date {
  const r = new Date(d)
  const day = r.getDay()
  const diff = day === 0 ? -6 : 1 - day
  r.setDate(r.getDate() + diff)
  r.setHours(0, 0, 0, 0)
  return r
}
function setDefaultRange() {
  const mon = mondayOf(new Date())
  const start = new Date(mon)
  start.setDate(start.getDate() - 7 * 2) // 4 周:本周 + 上一周 + 未来 2 周
  from.value = start.toISOString().slice(0, 10)
  const end = new Date(mon)
  end.setDate(end.getDate() + 7 * 3 - 1)
  to.value = end.toISOString().slice(0, 10)
}

/** 恢复默认区间 + 加载 (从按钮 @click 调用) */
function resetToDefaultRange() {
  setDefaultRange()
  load()
}

async function load() {
  loading.value = true
  try {
    const m = await workloadApi.userMatrix({ from: from.value, to: to.value })
    matrix.value = m.rows
    weekCount.value = m.weekCount
    // 提取周列表
    const ws = new Set<string>()
    const we: Record<string, string> = {}
    for (const r of m.rows) {
      if (!ws.has(r.weekStart)) {
        ws.add(r.weekStart)
        we[r.weekStart] = r.weekEnd
      }
    }
    weekStarts.value = [...ws].sort()
    weekEnds.value = we
  } catch (e: any) {
    ElMessage.error(e.message ?? '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadProjects() {
  try {
    projects.value = ((await api.get('/projects')) as ProjectCard[]) ?? []
    if (projects.value.length) selectedProjectId.value = projects.value[0].id
  } catch {
    /* ignore */
  }
}

async function loadDepartments() {
  try {
    departments.value = await departmentApi.list()
  } catch {
    /* ignore */
  }
}

function onDeptChange() {
  void loadGantt()
}

function onMilestoneClick(m: {
  id: number
  projectId: number
  name: string
  planDate: string
  actualDate: string | null
  status: string
  weight: number
}) {
  drawerMilestone.value = m
  drawerVisible.value = true
}

function onMilestoneMoved() {
  // 改期后重新拉甘特图(让 bar / 进度同步)
  void loadGantt()
}

async function loadProject() {
  if (!selectedProjectId.value) return
  try {
    projectLoad.value = await workloadApi.projectLoad(selectedProjectId.value, {
      from: from.value,
      to: to.value,
    })
  } catch (e: any) {
    projectLoad.value = null
    ElMessage.warning(e.message ?? '项目无工时')
  }
}

// ---------- 甘特图加载 ----------
async function loadGantt() {
  ganttLoading.value = true
  try {
    const params: { from?: string; to?: string; departmentIds?: number[] } = {}
    if (ganttMode.value === 'manual') {
      if (ganttFrom.value) params.from = ganttFrom.value
      if (ganttTo.value) params.to = ganttTo.value
    }
    if (selectedDeptIds.value.length) {
      params.departmentIds = selectedDeptIds.value
    }
    ganttData.value = await workloadApi.gantt(params)
  } catch (e: any) {
    ganttData.value = null
    ElMessage.error(e.message ?? '甘特图加载失败')
  } finally {
    ganttLoading.value = false
  }
}

function ganttResetDefault() {
  ganttMode.value = 'auto'
  ganttFrom.value = ''
  ganttTo.value = ''
  void loadGantt()
}

/** 快捷预设(相对今天) */
function ganttQuickPreset(months: number) {
  const d = new Date()
  const f = new Date(d)
  f.setMonth(f.getMonth() - 1)
  const t = new Date(d)
  t.setMonth(t.getMonth() + months)
  ganttMode.value = 'manual'
  ganttFrom.value = f.toISOString().slice(0, 10)
  ganttTo.value = t.toISOString().slice(0, 10)
  void loadGantt()
}

onMounted(async () => {
  setDefaultRange()
  await load()
  await loadProjects()
  await loadProject()
  await loadDepartments()
  await loadGantt()
})

watch(selectedProjectId, loadProject)
watch([from, to], loadProject)

// ---------- 透视 ----------
interface UserRow {
  userId: number
  username: string
  fullName: string
  departmentName: string
  weeks: Record<string, UserWeekRow>
  totalHours: number
  totalMs: number
  totalUp: number
}
const pivoted = computed<UserRow[]>(() => {
  const map = new Map<number, UserRow>()
  for (const r of matrix.value) {
    let row = map.get(r.userId)
    if (!row) {
      row = {
        userId: r.userId,
        username: r.username,
        fullName: r.fullName,
        departmentName: r.departmentName,
        weeks: {},
        totalHours: 0,
        totalMs: 0,
        totalUp: 0,
      }
      map.set(r.userId, row)
    }
    row.weeks[r.weekStart] = r
    row.totalHours += r.totalHours
    row.totalMs += r.milestoneCount ?? 0
    row.totalUp += r.upcomingCount ?? 0
  }
  return [...map.values()].sort(
    (a, b) => a.departmentName.localeCompare(b.departmentName) || a.fullName.localeCompare(b.fullName),
  )
})

// ---------- P2.5: 里程碑关联 ----------
const msDrawer = ref({
  open: false,
  loading: false,
  user: null as null | { userId: number; fullName: string },
  weekStart: '',
  data: null as UserMilestoneList | null,
})
async function openMilestoneDrawer(row: UserRow, weekStart: string) {
  msDrawer.value = {
    open: true,
    loading: true,
    user: { userId: row.userId, fullName: row.fullName },
    weekStart,
    data: null,
  }
  try {
    msDrawer.value.data = await fetchUserMilestones(row.userId, weekStart)
  } catch (e: any) {
    ElMessage.error(e?.message ?? '加载里程碑失败')
  } finally {
    msDrawer.value.loading = false
  }
}
// 满载 + 临近同时变红
const isHighPressure = (h: number, up: number) => h >= 40 && up > 0
const cellShadow = (h: number, up: number) => (isHighPressure(h, up) ? 'inset 0 0 0 2px #f56c6c' : 'none')

// ---------- KPI ----------
const kpi = computed(() => {
  const total = pivoted.value.length
  const weekHours = new Set<number>()
  let totalHours = 0
  let overloaded = 0
  for (const r of pivoted.value) {
    for (const ws of weekStarts.value) {
      const h = r.weeks[ws]?.totalHours ?? 0
      if (h > 0) {
        totalHours += h
        weekHours.add(1)
      }
      if (h >= 40) overloaded++
    }
  }
  return { total, totalHours, overloaded }
})

// ---------- 颜色:0-50h 渐变 ----------
function cellColor(h: number): string {
  if (h === 0) return '#f4f4f5'
  if (h < 10) return '#e1f3d8' // 绿
  if (h < 20) return '#b7e5a3' // 浅绿
  if (h < 30) return '#ffe58f' // 黄
  if (h < 40) return '#ffbb6e' // 橙
  return '#f56c6c' // 红(满载)
}
function textColor(h: number): string {
  if (h < 10) return '#67c23a'
  if (h < 30) return '#a05a00'
  if (h < 40) return '#c25c1a'
  return '#fff'
}
function statusLabel(s: string) {
  return { NO_DATA: '—', DRAFT: '草', SUBMITTED: '待', APPROVED: '准' }[s] ?? '-'
}
function shortDate(s: string) {
  return s.slice(5) // MM-DD
}
</script>

<template>
  <div class="page">
    <!-- KPI 卡 -->
    <el-row :gutter="12" style="margin-bottom: 12px">
      <el-col :span="6">
        <el-card shadow="hover">
          <div style="color: #909399">在岗人数</div>
          <div style="font-size: 24px; font-weight: 600; color: #303133">{{ kpi.total }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div style="color: #909399">区间总工时</div>
          <div style="font-size: 24px; font-weight: 600; color: #67c23a">
            {{ kpi.totalHours.toFixed(1) }}h
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div style="color: #909399">满载人周 (≥40h)</div>
          <div
            style="font-size: 24px; font-weight: 600"
            :style="{ color: kpi.overloaded > 0 ? '#f56c6c' : '#67c23a' }"
          >
            {{ kpi.overloaded }}
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div style="color: #909399">区间周数</div>
          <div style="font-size: 24px; font-weight: 600; color: #909399">{{ weekCount }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ② 项目甘特图(P2.B / P1.5 收尾) -->
    <el-card style="margin-bottom: 12px">
      <template #header>
        <div
          style="
            display: flex;
            justify-content: space-between;
            align-items: center;
            flex-wrap: wrap;
            gap: 8px;
          "
        >
          <div>
            <span style="font-size: 16px; font-weight: 600">项目甘特图</span>
            <span style="color: #909399; margin-left: 8px; font-size: 12px">
              {{ ganttMode === 'auto' ? '自动模式:后端自适应时间范围' : '手动模式:用户选定' }}
            </span>
          </div>
          <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
            <!-- 部门多选 -->
            <el-select
              v-model="selectedDeptIds"
              multiple
              collapse-tags
              collapse-tags-tooltip
              placeholder="部门(全选)"
              style="width: 240px"
              clearable
              @change="onDeptChange"
            >
              <el-option v-for="d in departments" :key="d.id" :value="d.id" :label="d.name" />
            </el-select>

            <el-radio-group v-model="ganttMode" size="default" @change="loadGantt">
              <el-radio-button value="auto">自动适配</el-radio-button>
              <el-radio-button value="manual">手动范围</el-radio-button>
            </el-radio-group>

            <template v-if="ganttMode === 'manual'">
              <el-date-picker
                v-model="ganttFrom"
                type="date"
                placeholder="开始日期"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                style="width: 150px"
                @change="loadGantt"
              />
              <span>~</span>
              <el-date-picker
                v-model="ganttTo"
                type="date"
                placeholder="结束日期"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                style="width: 150px"
                @change="loadGantt"
              />
              <el-button-group>
                <el-button size="small" @click="ganttQuickPreset(3)">±3月</el-button>
                <el-button size="small" @click="ganttQuickPreset(6)">±6月</el-button>
                <el-button size="small" @click="ganttQuickPreset(12)">±1年</el-button>
              </el-button-group>
            </template>

            <el-button :icon="Refresh" size="default" @click="loadGantt">刷新</el-button>
            <el-button size="default" @click="ganttResetDefault">重置</el-button>
          </div>
        </div>
      </template>

      <GanttView
        :data="ganttData"
        :loading="ganttLoading"
        :mode="ganttMode"
        :department-ids="selectedDeptIds"
        @milestone-click="onMilestoneClick"
        @milestone-moved="onMilestoneMoved"
      />
    </el-card>

    <!-- 里程碑详情抽屉 -->
    <MilestoneDrawer v-model="drawerVisible" :milestone="drawerMilestone" @refresh="onMilestoneMoved" />

    <!-- 矩阵 -->
    <el-card>
      <template #header>
        <div
          style="
            display: flex;
            justify-content: space-between;
            align-items: center;
            flex-wrap: wrap;
            gap: 8px;
          "
        >
          <div>
            <el-icon style="vertical-align: middle"><DataLine /></el-icon>
            <span style="font-size: 16px; font-weight: 600">人员负载矩阵</span>
            <span style="color: #909399; margin-left: 8px">行 = 人,列 = 周,色阶 0~50h</span>
          </div>
          <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
            <el-date-picker
              v-model="from"
              type="date"
              placeholder="起始(周一)"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              style="width: 150px"
              @change="load"
            />
            <span>~</span>
            <el-date-picker
              v-model="to"
              type="date"
              placeholder="结束(周日)"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              style="width: 150px"
              @change="load"
            />
            <el-button-group>
              <el-button @click="shiftWeeks(-1)">← 上一区间</el-button>
              <el-button @click="resetToDefaultRange">默认 4 周</el-button>
              <el-button @click="shiftWeeks(1)">下一区间 →</el-button>
            </el-button-group>
            <el-button :icon="Refresh" @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table :data="pivoted" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="fullName" label="姓名" width="120" fixed="left">
          <template #default="{ row }">
            <div style="display: flex; flex-direction: column">
              <span style="font-weight: 600">{{ row.fullName }}</span>
              <span style="color: #909399; font-size: 12px">{{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="departmentName" label="部门" width="100" />
        <el-table-column
          v-for="ws in weekStarts"
          :key="ws"
          :label="`${shortDate(ws)} ~ ${shortDate(weekEnds[ws])}`"
          width="130"
          align="center"
        >
          <template #default="{ row }">
            <div
              class="cell-clickable"
              :class="{
                'is-pressure': isHighPressure(
                  row.weeks[ws]?.totalHours ?? 0,
                  row.weeks[ws]?.upcomingCount ?? 0,
                ),
              }"
              :style="{
                boxShadow: cellShadow(row.weeks[ws]?.totalHours ?? 0, row.weeks[ws]?.upcomingCount ?? 0),
                background: cellColor(row.weeks[ws]?.totalHours ?? 0),
                color: textColor(row.weeks[ws]?.totalHours ?? 0),
                padding: '4px 6px',
                borderRadius: '4px',
                fontWeight: 600,
              }"
              @click="openMilestoneDrawer(row, ws)"
            >
              {{ (row.weeks[ws]?.totalHours ?? 0).toFixed(1) }}h
            </div>
            <div style="font-size: 11px; color: #909399; margin-top: 2px">
              {{ statusLabel(row.weeks[ws]?.status ?? 'NO_DATA') }}
              <span v-if="(row.weeks[ws]?.projectCount ?? 0) > 0">· {{ row.weeks[ws]?.projectCount }}项</span>
            </div>
            <div v-if="(row.weeks[ws]?.milestoneCount ?? 0) > 0" style="font-size: 10px; margin-top: 1px">
              <el-tag
                size="small"
                :type="(row.weeks[ws]?.upcomingCount ?? 0) > 0 ? 'danger' : 'info'"
                effect="plain"
                style="height: 16px; padding: 0 4px; line-height: 16px"
              >
                🎯 {{ row.weeks[ws]?.milestoneCount }}
                <span v-if="(row.weeks[ws]?.upcomingCount ?? 0) > 0" style="color: #f56c6c; font-weight: 600">
                  🔥{{ row.weeks[ws]?.upcomingCount }}
                </span>
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="totalHours" label="区间总" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <span
              style="font-weight: 600; font-size: 16px"
              :style="{
                color: row.totalHours > 160 ? '#f56c6c' : row.totalHours > 80 ? '#e6a23c' : '#67c23a',
              }"
            >
              {{ row.totalHours.toFixed(1) }}
            </span>
          </template>
        </el-table-column>
      </el-table>
      <div
        style="
          margin-top: 8px;
          font-size: 12px;
          color: #909399;
          display: flex;
          gap: 12px;
          align-items: center;
        "
      >
        色阶:
        <span :style="{ background: cellColor(0), padding: '2px 8px', borderRadius: '3px' }">0h</span>
        <span :style="{ background: cellColor(8), padding: '2px 8px', borderRadius: '3px' }">&lt;10h</span>
        <span :style="{ background: cellColor(15), padding: '2px 8px', borderRadius: '3px' }">&lt;20h</span>
        <span :style="{ background: cellColor(25), padding: '2px 8px', borderRadius: '3px' }">&lt;30h</span>
        <span :style="{ background: cellColor(35), padding: '2px 8px', borderRadius: '3px' }">&lt;40h</span>
        <span :style="{ background: cellColor(45), padding: '2px 8px', borderRadius: '3px', color: '#fff' }">
          ≥40h 满载
        </span>
        状态简写:— 无 / 草 DRAFT / 待 SUBMITTED / 准 APPROVED
      </div>
    </el-card>

    <!-- 单项目工时分布 -->
    <el-card style="margin-top: 16px">
      <template #header>
        <div
          style="
            display: flex;
            justify-content: space-between;
            align-items: center;
            flex-wrap: wrap;
            gap: 8px;
          "
        >
          <div>
            <el-icon style="vertical-align: middle"><DataAnalysis /></el-icon>
            <span style="font-size: 16px; font-weight: 600">单项目工时分布</span>
          </div>
          <el-select v-model="selectedProjectId" placeholder="选项目" filterable style="width: 300px">
            <el-option v-for="p in projects" :key="p.id" :value="p.id" :label="`${p.code ?? ''} ${p.name}`" />
          </el-select>
        </div>
      </template>

      <div v-if="projectLoad">
        <el-row :gutter="12" style="margin-bottom: 12px">
          <el-col :span="6">
            <el-statistic title="项目" :value="`${projectLoad.projectId} ${projectLoad.projectName}`" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="总工时" :value="projectLoad.totalHours" :precision="1" suffix="h" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="参与人数" :value="projectLoad.memberCount" suffix="人" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="工日" :value="projectLoad.dayCount" suffix="天" />
          </el-col>
        </el-row>

        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px">
          <div>
            <h4 style="margin: 0 0 8px 0">按人分布</h4>
            <el-table :data="projectLoad.byMember" border size="small">
              <el-table-column prop="username" label="用户" width="120" />
              <el-table-column prop="totalHours" label="工时" width="80" align="right">
                <template #default="{ row }">
                  <b :style="{ color: row.totalHours > 40 ? '#f56c6c' : '#67c23a' }">
                    {{ row.totalHours.toFixed(1) }}h
                  </b>
                </template>
              </el-table-column>
              <el-table-column prop="dayCount" label="工日" width="60" align="right" />
            </el-table>
          </div>
          <div>
            <h4 style="margin: 0 0 8px 0">按日分布</h4>
            <el-table :data="projectLoad.byDay" border size="small">
              <el-table-column prop="workDate" label="日期" width="110" />
              <el-table-column prop="totalHours" label="工时" width="80" align="right">
                <template #default="{ row }">
                  <b>{{ row.totalHours.toFixed(1) }}h</b>
                </template>
              </el-table-column>
              <el-table-column prop="memberCount" label="人数" width="60" align="right" />
            </el-table>
            <!-- P2.5: 单元格点击下钻 — 单人单周里程碑弹窗 -->
            <el-drawer
              v-model="msDrawer.open"
              :title="
                msDrawer.user
                  ? msDrawer.user.fullName + ' · ' + msDrawer.weekStart + ' 所在周的里程碑'
                  : '里程碑列表'
              "
              size="640px"
              direction="rtl"
            >
              <div v-loading="msDrawer.loading" style="padding: 0 16px">
                <div
                  v-if="msDrawer.data && msDrawer.data.total === 0"
                  style="text-align: center; color: #909399; padding: 40px 0"
                >
                  📭 该人该周窗口内无里程碑
                </div>
                <el-table v-else-if="msDrawer.data" :data="msDrawer.data.items" border size="small" stripe>
                  <el-table-column prop="milestoneName" label="里程碑" min-width="120">
                    <template #default="{ row: m }">
                      <a
                        :href="`/projects/${m.projectId}`"
                        target="_blank"
                        style="color: #409eff; text-decoration: none; font-weight: 500"
                      >
                        {{ m.milestoneName }}
                      </a>
                    </template>
                  </el-table-column>
                  <el-table-column prop="projectCode" label="项目" width="160" show-overflow-tooltip>
                    <template #default="{ row: m }">
                      <el-tag size="small" type="info" effect="plain">{{ m.projectCode }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="phaseName" label="阶段" width="80" />
                  <el-table-column prop="statusName" label="状态" width="80" />
                  <el-table-column prop="planDate" label="计划日期" width="100" />
                  <el-table-column prop="weight" label="权重" width="60" align="center" />
                </el-table>
                <div v-if="msDrawer.data" style="margin-top: 12px; font-size: 12px; color: #909399">
                  命中:
                  <strong>{{ msDrawer.data.total }}</strong>
                  个里程碑
                </div>
              </div>
            </el-drawer>
          </div>
        </div>
      </div>
      <el-empty v-else description="请选择项目" />
    </el-card>
  </div>
</template>
