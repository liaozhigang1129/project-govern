<script setup lang="ts">
/**
 * 甘特图视图(全项目聚合)— P1.5 收尾
 *  - 横轴:时间线(默认后端给的范围;前端可平移)
 *  - 纵轴:每个项目一行
 *  - Bar:计划区间背景 + 实际区间前景 + 进度条
 *  - 里程碑:▼ 三角标记 + tooltip + 可拖动改期
 *  - 过滤:PM / 部门 / 包含已完成 / 时间区间
 *  - 拖拽改期:计划条整条 move / 左右 handle resize / 里程碑 ▼ move
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { workloadApi } from '@/api/workload'
import {
  type GanttBar,
  type GanttMilestone,
  type GanttResponse,
} from '@/components/GanttView.vue'

/** GanttQuery 仅供 loadGantt 内构造 params,放这里 */
type GanttQuery = {
  from?: string
  to?: string
  departmentId?: number
  pmUserId?: number
  includeCompleted?: boolean
}
import { useGanttDrag, type BarDragTarget, type MilestoneDragTarget } from '@/composables/useGanttDrag'
import api from '@/api/client'
import { useAuthStore } from '@/stores/auth'

/** ISO 日期加 n 天(只动日期,不跨时区) */
function shiftIso(iso: string, days: number): string {
  const d = new Date(iso + 'T00:00:00Z')
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().slice(0, 10)
}

const router = useRouter()
const auth = useAuthStore()
const data = ref<GanttResponse | null>(null)
const loading = ref(false)

// 过滤项
const includeCompleted = ref(true)
const fromInput = ref('')
const toInput = ref('')
const pmFilter = ref<number | null>(null)        // 选某 PM
const deptFilter = ref<number | null>(null)      // 选某部门

/** PM 列表(从 /users 拉,只挑 enabled) */
interface UserOption { id: number; username: string; fullName: string }
const pmOptions = ref<UserOption[]>([])

/** 部门列表(从 /departments 拉) */
interface DeptOption { id: number; code: string; name: string }
const deptOptions = ref<DeptOption[]>([])

/** 当前用户 id(用于"PM 选我") */
const myUserId = computed<number | null>(() => (auth.user as any)?.id ?? null)

async function loadFilterOptions() {
  try {
    const [users, depts] = await Promise.all([
      api.get<UserOption[]>('/users/options'),
      api.get<DeptOption[]>('/departments'),
    ])
    pmOptions.value = (users ?? []).filter((u: any) => u.enabled !== false)
    deptOptions.value = depts ?? []
  } catch (e: any) {
    // 不阻塞主功能
    console.warn('加载过滤选项失败:', e.message)
  }
}

/** 一键"只看我作为 PM 的项目" */
function filterMine() {
  if (myUserId.value == null) {
    ElMessage.warning('未登录或会话已过期')
    return
  }
  pmFilter.value = myUserId.value
  load()
}

async function load() {
  loading.value = true
  try {
    const params: GanttQuery = { includeCompleted: includeCompleted.value }
    if (fromInput.value) params.from = fromInput.value
    if (toInput.value) params.to = toInput.value
    if (pmFilter.value != null) params.pmUserId = pmFilter.value
    if (deptFilter.value != null) params.departmentId = deptFilter.value
    data.value = await workloadApi.gantt(params)
  } catch (e: any) {
    ElMessage.error('加载甘特图失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadFilterOptions()
  await load()
})

// 像素换算:每天 pxPerDay 宽
const pxPerDay = ref(8)
const months = computed(() => buildMonths(data.value))
const days = computed(() => buildDays(data.value))
const totalDays = computed(() => days.value.length)
const widthPx = computed(() => totalDays.value * pxPerDay.value)

/** 今天的 ISO(YYYY-MM-DD)— 在范围内才画今天竖线 */
const todayIso = ref(new Date().toISOString().slice(0, 10))
const todayPct = computed(() => {
  if (!data.value) return null
  if (todayIso.value < data.value.rangeFrom || todayIso.value > data.value.rangeTo) return null
  return leftPct(todayIso.value, data.value.rangeFrom)
})

function buildDays(d: GanttResponse | null) {
  if (!d) return []
  const a = new Date(d.rangeFrom)
  const b = new Date(d.rangeTo)
  const out: string[] = []
  const cur = new Date(a)
  while (cur <= b) {
    out.push(cur.toISOString().slice(0, 10))
    cur.setDate(cur.getDate() + 1)
  }
  return out
}
function buildMonths(d: GanttResponse | null): { label: string; span: number }[] {
  if (!d) return []
  const out: { label: string; span: number }[] = []
  let cur = new Date(d.rangeFrom)
  cur.setDate(1)
  const end = new Date(d.rangeTo)
  while (cur <= end) {
    const ym = cur.toISOString().slice(0, 7)
    const monthStart = new Date(cur)
    const next = new Date(cur)
    next.setMonth(next.getMonth() + 1)
    const spanStart = monthStart < new Date(d.rangeFrom) ? new Date(d.rangeFrom) : monthStart
    const spanEnd = next > end ? end : new Date(next.getTime() - 86400000)
    const span = Math.round((spanEnd.getTime() - spanStart.getTime()) / 86400000) + 1
    out.push({ label: ym, span })
    cur = next
  }
  return out
}

function leftPct(date: string | null | undefined, rangeFrom: string): number {
  if (!date) return 0
  const ms = new Date(date).getTime() - new Date(rangeFrom).getTime()
  return (ms / 86400000 / totalDays.value) * 100
}
function widthPct(start: string | null | undefined, end: string | null | undefined, rangeFrom: string): number {
  if (!start || !end) return 0
  const ms = new Date(end).getTime() - new Date(start).getTime()
  return (ms / 86400000 / totalDays.value) * 100
}

function progressColor(pct: number) {
  if (pct >= 80) return '#67c23a'
  if (pct >= 50) return '#409eff'
  if (pct >= 20) return '#e6a23c'
  return '#909399'
}

function openProject(bar: GanttBar) {
  // 拖拽过则不跳转(防止误点)
  if (wasDragged.value) {
    wasDragged.value = false
    return
  }
  router.push(`/projects/${bar.projectId}`)
}

// =============== 拖拽改期 ===============

/**
 * 计划条拖拽启动(view 端 mousedown 调用)
 * @param bar 当前项目 bar(响应式 data.value.bars[i] 的引用 — 我们直接改它的 planStart/planEnd)
 */
function onBarMouseDown(e: MouseEvent, bar: GanttBar, mode: 'bar-move' | 'bar-resize-l' | 'bar-resize-r') {
  if (!bar.planStart || !bar.planEnd) return
  e.preventDefault()
  e.stopPropagation()
  const target: BarDragTarget = {
    kind: 'bar',
    projectId: bar.projectId,
    origStart: bar.planStart,
    origEnd: bar.planEnd,
    setStart: (iso) => { bar.planStart = iso },
    setEnd: (iso) => { bar.planEnd = iso },
    getCurrent: () => ({
      planStart: bar.planStart ?? '',
      planEnd: bar.planEnd ?? '',
    }),
  }
  startDrag(mode, target, e.clientX)
}

// 拖拽 hook(实际 PUT 在 onCommit*)
const { startDrag, active, saving, wasDragged } = useGanttDrag({
  pxPerDay: () => pxPerDay.value,
  shiftDate: shiftIso,
  onCommitBar: async (projectId, payload) => {
    // 失败回滚:再 load 一次最稳;这里简单做:catch 时 reload
    try {
      await workloadApi.updateGanttBar(projectId, payload)
      ElMessage.success('计划区间已保存')
    } catch (err: any) {
      ElMessage.error('保存失败: ' + err.message)
      await load()
    }
  },
  onCommitMilestone: async (milestoneId, payload) => {
    try {
      await workloadApi.updateGanttMilestone(milestoneId, payload)
      ElMessage.success('里程碑日期已保存')
    } catch (err: any) {
      // Spring 后端 PATCH 路径会 500 当成静态资源;实测 PUT /api/milestones/{id} {planDate} 同效
      const msg = String(err?.message || '')
      const isStaticResource500 = msg.includes('No static resource')
      if (isStaticResource500) {
        try {
          await api.put(`/milestones/${milestoneId}`, payload)
          ElMessage.success('里程碑日期已保存')
          return
        } catch (err2: any) {
          ElMessage.error('保存失败: ' + err2.message)
          await load()
          return
        }
      }
      ElMessage.error('保存失败: ' + msg)
      await load()
    }
  },
})

// =============== 里程碑拖拽(6c 占位) ===============
function onMilestoneMouseDown(e: MouseEvent, m: GanttMilestone, _bar: GanttBar) {
  if (!m.planDate) return
  e.preventDefault()
  e.stopPropagation()
  const target: MilestoneDragTarget = {
    kind: 'milestone',
    milestoneId: m.id,
    origDate: m.planDate,
    setDate: (iso) => { m.planDate = iso },
    getCurrent: () => m.planDate ?? m.actualDate ?? '',
  }
  startDrag('milestone-move', target, e.clientX)
}

function goToday() {
  if (!data.value) return
  const today = new Date().toISOString().slice(0, 10)
  if (today >= data.value.rangeFrom && today <= data.value.rangeTo) return
  fromInput.value = ''
  toInput.value = ''
  load()
}
</script>

<template>
  <div class="gantt-page">
    <el-card>
      <template #header>
        <div class="gantt-header">
          <span style="font-weight: 600; font-size: 16px">📊 甘特图(全项目)</span>
          <div class="gantt-tools">
            <el-date-picker
              v-model="fromInput"
              type="date"
              placeholder="开始"
              size="small"
              value-format="YYYY-MM-DD"
              style="width: 140px"
            />
            <span style="color: #909399">→</span>
            <el-date-picker
              v-model="toInput"
              type="date"
              placeholder="结束"
              size="small"
              value-format="YYYY-MM-DD"
              style="width: 140px"
            />
            <el-select
              v-model="pmFilter"
              placeholder="所有 PM"
              size="small"
              clearable
              filterable
              style="width: 140px; margin-left: 8px"
              @change="load"
            >
              <el-option label="所有 PM" :value="null" />
              <el-option
                v-for="u in pmOptions"
                :key="u.id"
                :label="`${u.fullName} (${u.username})`"
                :value="u.id"
              />
            </el-select>
            <el-select
              v-model="deptFilter"
              placeholder="所有部门"
              size="small"
              clearable
              filterable
              style="width: 140px"
              @change="load"
            >
              <el-option label="所有部门" :value="null" />
              <el-option
                v-for="d in deptOptions"
                :key="d.id"
                :label="`${d.name} (${d.code})`"
                :value="d.id"
              />
            </el-select>
            <el-button
              v-if="myUserId"
              size="small"
              :type="pmFilter === myUserId ? 'primary' : 'default'"
              @click="filterMine"
              title="只看当前用户作为 PM 的项目"
            >
              PM 选我
            </el-button>
            <el-checkbox v-model="includeCompleted" @change="load" style="margin-left: 8px">
              含已完成
            </el-checkbox>
            <el-button type="primary" size="small" @click="load" :loading="loading">
              加载
            </el-button>
            <el-button size="small" @click="goToday">回到今天</el-button>
            <el-button-group size="small">
              <el-button @click="pxPerDay = Math.max(2, pxPerDay - 2)">-</el-button>
              <el-button @click="pxPerDay = Math.min(40, pxPerDay + 2)">+</el-button>
            </el-button-group>
          </div>
        </div>
      </template>

      <div v-if="data" style="margin-bottom: 12px; color: #606266; font-size: 13px">
        时间范围: <b>{{ data.rangeFrom }}</b> → <b>{{ data.rangeTo }}</b> · 共
        <b>{{ data.projectCount }}</b> 个项目
      </div>

      <div v-if="loading" v-loading="true" style="height: 200px"></div>

      <div v-else-if="data && data.bars.length === 0" class="gantt-empty">
        暂无项目
      </div>

      <div v-else-if="data" class="gantt-container" :class="{ 'is-dragging-active': active }" v-loading="saving" element-loading-text="保存中…">
        <!-- 月份标尺 -->
        <div class="gantt-months" :style="{ width: (widthPx + 280) + 'px' }">
          <div class="gantt-months-spacer"></div>
          <div
            v-for="(m, i) in months"
            :key="i"
            class="gantt-month"
            :style="{ width: m.span * pxPerDay + 'px' }"
          >
            {{ m.label }}
          </div>
        </div>

        <!-- 项目行 -->
        <div
          v-for="bar in data.bars"
          :key="bar.projectId"
          class="gantt-row"
          @click="openProject(bar)"
        >
          <div class="gantt-row-label">
            {{ bar.projectCode }} {{ bar.projectName }}
            <!-- P3 修复:无排期数据时给个 badge,让用户知道不是渲染 bug -->
            <el-tag v-if="!bar.planStart && !bar.actualStart" type="info" size="small" effect="plain" style="margin-left: 6px">未排期</el-tag>
          </div>
          <div class="gantt-row-timeline" :style="{ width: widthPx + 'px' }">
            <!-- 今天竖线 — 在范围内才显示 -->
            <div
              v-if="todayPct !== null"
              class="gantt-today-line"
              :style="{ left: todayPct + '%' }"
              :title="`今天: ${todayIso}`"
            >
              <span class="gantt-today-label">今天</span>
            </div>
            <!-- 计划区间(背景条) — 整条可拖,左右边缘可改端点 -->
            <div
              v-if="bar.planStart && bar.planEnd"
              class="gantt-bar plan"
              :class="{ 'is-dragging': active && active.target?.kind === 'bar' && active.target?.projectId === bar.projectId }"
              :style="{
                left: leftPct(bar.planStart, data.rangeFrom) + '%',
                width: widthPct(bar.planStart, bar.planEnd, data.rangeFrom) + '%',
              }"
              :title="`计划: ${bar.planStart} → ${bar.planEnd}  拖动改期`"
              @mousedown="onBarMouseDown($event, bar, 'bar-move')"
            >
              <div class="gantt-bar-handle gantt-bar-handle-l" @mousedown="onBarMouseDown($event, bar, 'bar-resize-l')"></div>
              <div class="gantt-bar-handle gantt-bar-handle-r" @mousedown="onBarMouseDown($event, bar, 'bar-resize-r')"></div>
            </div>
            <!-- 实际区间(前景条 + 进度)
                 P3 修复:actualEnd 经常为 null(项目进行中),退化为 planEnd 显示 -->
            <div
              v-if="bar.actualStart && (bar.actualEnd || bar.planEnd)"
              class="gantt-bar actual"
              :style="{
                left: leftPct(bar.actualStart, data.rangeFrom) + '%',
                width: widthPct(bar.actualStart, bar.actualEnd || bar.planEnd, data.rangeFrom) + '%',
                background: progressColor(bar.progressPct),
              }"
              :title="`实际: ${bar.actualStart} → ${bar.actualEnd || '(进行中)'}  进度 ${bar.progressPct}%`"
            >
              <div
                class="gantt-bar-progress"
                :style="{
                  width: (bar.progressPct ?? 0) + '%',
                  background: 'rgba(255,255,255,0.4)',
                  height: '100%',
                }"
              ></div>
              <span class="gantt-bar-label">{{ bar.progressPct ?? 0 }}%</span>
            </div>
            <!-- 里程碑 ▼ — 拖动改 planDate -->
            <div
              v-for="m in bar.milestones"
              :key="m.id"
              class="gantt-milestone"
              :class="{ 'is-dragging': active && active.target?.kind === 'milestone' && active.target?.milestoneId === m.id }"
              :style="{
                left: leftPct(m.planDate, data.rangeFrom) + '%',
              }"
              :title="`${m.name} (${m.status})  拖动改期`"
              @mousedown="onMilestoneMouseDown($event, m, bar)"
            >
              ▼
            </div>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.gantt-page { padding: 16px; }
.gantt-header { display: flex; justify-content: space-between; align-items: center; }
.gantt-tools { display: flex; align-items: center; gap: 6px; }
.gantt-empty { padding: 60px; text-align: center; color: #909399; }
.gantt-container { overflow-x: auto; padding-top: 8px; }
.gantt-months {
  display: flex;
  border-bottom: 1px solid var(--pmo-border);
  background: #fafafa;
  font-size: 12px;
  font-weight: 600;
  color: #606266;
}
.gantt-months-spacer {
  width: 280px;
  flex: 0 0 280px;
  border-right: 1px solid var(--pmo-border);
  background: #fcfcfc;
}
.gantt-month {
  padding: 4px 8px;
  border-right: 1px solid var(--pmo-border);
  text-align: center;
}
.gantt-row {
  display: flex;
  align-items: center;
  border-bottom: 1px solid var(--pmo-border);
  cursor: pointer;
  min-height: 40px;
}
.gantt-row:hover { background: #f5f7fa; }
.gantt-row-label {
  width: 280px;
  padding: 8px 12px;
  font-size: 13px;
  border-right: 1px solid var(--pmo-border);
  background: #fcfcfc;
  position: sticky;
  left: 0;
  z-index: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.gantt-row-timeline {
  position: relative;
  height: 40px;
  background:
    repeating-linear-gradient(
      to right,
      transparent 0,
      transparent 79px,
      rgba(0, 0, 0, 0.04) 79px,
      rgba(0, 0, 0, 0.04) 80px
    );
}
.gantt-today-line {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 0;
  border-left: 2px solid #f56c6c;
  z-index: 4;
  pointer-events: none;
}
.gantt-today-line::before {
  content: '';
  position: absolute;
  top: 0;
  left: -3px;
  width: 8px;
  height: 8px;
  background: #f56c6c;
  border-radius: 50%;
}
.gantt-today-label {
  position: absolute;
  top: 2px;
  left: 4px;
  font-size: 10px;
  font-weight: 600;
  color: #f56c6c;
  background: rgba(255, 255, 255, 0.9);
  padding: 0 4px;
  border-radius: 2px;
  white-space: nowrap;
}
.gantt-bar {
  position: absolute;
  top: 8px;
  height: 24px;
  border-radius: 4px;
  transition: opacity 0.15s;
}
.gantt-bar:hover { opacity: 0.85; }
.gantt-bar.plan {
  background: #e6f0ff;
  border: 1px dashed #909399;
  height: 24px;
  top: 8px;
  opacity: 0.6;
  cursor: grab;
}
.gantt-bar.plan.is-dragging {
  cursor: grabbing;
  opacity: 0.9;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.4);
}
.gantt-bar-handle {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 6px;
  cursor: ew-resize;
  z-index: 1;
}
.gantt-bar-handle-l { left: 0; }
.gantt-bar-handle-r { right: 0; }
.gantt-bar-handle:hover { background: rgba(64, 158, 255, 0.25); }
.gantt-bar.actual {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0 6px;
  color: white;
  font-size: 11px;
  font-weight: 600;
  overflow: hidden;
}
.gantt-bar-label { position: relative; z-index: 1; }
.gantt-bar-progress {
  position: absolute;
  left: 0;
  top: 0;
  border-radius: 4px 0 0 4px;
}
.gantt-milestone {
  position: absolute;
  top: 4px;
  color: #f56c6c;
  font-size: 16px;
  transform: translateX(-50%);
  cursor: grab;
  z-index: 2;
  padding: 0 6px;
  user-select: none;
}
.gantt-milestone:hover { color: #f00; }
.gantt-milestone.is-dragging {
  cursor: grabbing;
  color: #f00;
  text-shadow: 0 0 4px rgba(255, 0, 0, 0.4);
}
/* 拖拽时全局禁选 — 防止拖动时浏览器选中文字干扰 */
.gantt-container.is-dragging-active,
.gantt-container.is-dragging-active * {
  user-select: none;
  cursor: grabbing !important;
}
</style>
