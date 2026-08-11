<script setup lang="ts">
/**
 * GanttView — 简单甘特图(P2.B)
 *
 * 用纯 HTML/CSS 画:行 = 项目,列 = 日期。
 * 每个项目一条 bar:
 *   - 计划区间(planStart..planEnd)半透明
 *   - 实际区间(actualStart..actualEnd)实色
 *   - 进度条(progressPct)叠加
 *   - 里程碑菱形(milestones[])
 *
 * 比 ECharts custom series 简单,且零依赖。
 * 区间由后端 GanttService.gantt() 自适应,前端只负责"紧贴 bar 实际范围"。
 */

import { computed, h, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { Calendar, Refresh } from '@element-plus/icons-vue'

export interface GanttMilestone {
  id: number
  name: string
  planDate: string // YYYY-MM-DD
  actualDate: string | null
  status: string // PENDING/IN_PROGRESS/COMPLETED/DELAYED
  weight: number
  /** V3.1: 阶段 id (1-7) */
  phaseId: number | null
  /** V3.1: 阶段名(立项/需求/设计/开发/测试/上线运维/维保) */
  phaseName: string | null
}

export interface GanttBar {
  projectId: number
  projectCode: string
  projectName: string
  planStart: string | null
  planEnd: string | null
  actualStart: string | null
  actualEnd: string | null
  progressPct: number
  milestones: GanttMilestone[]
}

export interface GanttResponse {
  rangeFrom: string
  rangeTo: string
  projectCount: number
  bars: GanttBar[]
}

const props = defineProps<{
  data: GanttResponse | null
  loading: boolean
  mode: 'auto' | 'manual'
  /** 部门多选(空 = 全选) */
  departmentIds?: number[]
  /** 改期后回调(通知父组件刷新 / 显示 toast) */
}>()
const emit = defineEmits<{
  (e: 'milestone-click', m: GanttMilestone & { projectId: number }): void
  (e: 'milestone-moved', payload: { id: number; projectId: number; newPlanDate: string }): void
  (e: 'drag-broadcast', payload: { ids: number[]; dayOffset: number; x: number; y: number; ts: number }): void
  (e: 'drag-end-broadcast', payload: { ids: number[]; dayOffset: number; ts: number }): void
}>()

// ---------- 把字符串日期转成 Date ----------
function toDate(s: string | null | undefined): Date | null {
  if (!s) return null
  return new Date(s + 'T00:00:00')
}

// ---------- 视图范围(优先用 data.rangeFrom/To,否则 bar 自身) ----------
const viewFrom = computed<Date>(() => {
  if (props.data?.rangeFrom) return toDate(props.data.rangeFrom)!
  // 兜底:bar 最早 planStart
  const dates =
    props.data?.bars
      ?.map((b) => toDate(b.planStart) || toDate(b.actualStart))
      .filter((d): d is Date => !!d) ?? []
  if (dates.length) return new Date(Math.min(...dates.map((d) => d.getTime())))
  return new Date()
})
const viewTo = computed<Date>(() => {
  if (props.data?.rangeTo) return toDate(props.data.rangeTo)!
  const dates =
    props.data?.bars?.map((b) => toDate(b.planEnd) || toDate(b.actualEnd)).filter((d): d is Date => !!d) ?? []
  if (dates.length) return new Date(Math.max(...dates.map((d) => d.getTime())))
  return new Date(viewFrom.value.getTime() + 90 * 24 * 3600 * 1000)
})

const totalDays = computed(() =>
  Math.max(1, Math.round((viewTo.value.getTime() - viewFrom.value.getTime()) / 86400000)),
)

// ---------- 月份分隔条 ----------
interface MonthTick {
  x: number
  label: string
}
const monthTicks = computed<MonthTick[]>(() => {
  const ticks: MonthTick[] = []
  const start = new Date(viewFrom.value.getFullYear(), viewFrom.value.getMonth(), 1)
  const end = viewTo.value
  let cur = new Date(start)
  while (cur <= end) {
    const days = (cur.getTime() - viewFrom.value.getTime()) / 86400000
    const x = (days / totalDays.value) * 100
    ticks.push({
      x,
      label: `${cur.getFullYear()}-${String(cur.getMonth() + 1).padStart(2, '0')}`,
    })
    cur = new Date(cur.getFullYear(), cur.getMonth() + 1, 1)
  }
  return ticks
})

// ---------- bar 像素位置 ----------
function barStyle(bar: GanttBar) {
  // 实际区间(优先)
  const s = toDate(bar.actualStart) || toDate(bar.planStart)
  const e = toDate(bar.actualEnd) || toDate(bar.planEnd)
  if (!s || !e) {
    return { display: 'none' as const }
  }
  const startDays = Math.max(0, (s.getTime() - viewFrom.value.getTime()) / 86400000)
  const endDays = Math.min(totalDays.value, (e.getTime() - viewFrom.value.getTime()) / 86400000)
  if (endDays < 0 || startDays > totalDays.value) {
    return { display: 'none' as const }
  }
  // 用绝对 px(跟缩放联动),但 % 已不可用(父容器 fixed-width)
  const left = LABEL_W + startDays * pxPerDay()
  const width = Math.max(2, (endDays - startDays) * pxPerDay())
  return { left: `${left}px`, width: `${width}px` }
}
function planBarStyle(bar: GanttBar) {
  // 计划区间(背景半透明)
  if (!bar.planStart || !bar.planEnd) return { display: 'none' as const }
  return barStyle({ ...bar, actualStart: bar.planStart, actualEnd: bar.planEnd })
}
function progressStyle(bar: GanttBar) {
  const base = barStyle(bar)
  if (base.display === 'none') return base
  // base.width 是 "123.4px", 乘以 pct/100
  const m = /^([\d.]+)px$/.exec(base.width as string)
  if (!m) return base
  const w = (parseFloat(m[1]) * Math.min(100, Math.max(0, bar.progressPct))) / 100
  return { left: base.left, width: `${w}px` }
}
function milestoneStyle(bar: GanttBar, m: GanttMilestone) {
  const d = toDate(m.actualDate || m.planDate)
  if (!d) return { display: 'none' as const }
  const days = (d.getTime() - viewFrom.value.getTime()) / 86400000
  if (days < 0 || days > totalDays.value) return { display: 'none' as const }
  // 用绝对 px 定位,跟缩放联动
  const left = LABEL_W + days * pxPerDay()
  const size = Math.max(8, Math.min(20, 4 + m.weight * 2)) // 8~20px
  return {
    position: 'absolute' as const,
    top: '12px', // 跟父 .gantt-track 顶部对齐(36px - size/2 = 18?取 12px 让菱形大致居中)
    left: `${left - size / 2}px`,
    width: `${size}px`,
    height: `${size}px`,
  }
}

/** 偏移量徽章定位:复用 wrap 的 left,但放右上角 */
function offsetBadgeStyle(bar: GanttBar, m: GanttMilestone) {
  const s = milestoneStyle(bar, m)
  if (s.display === 'none') return { display: 'none' as const }
  // 拿到 size 数值
  const size = parseFloat((s.width as string).replace('px', ''))
  const wrapLeft = parseFloat((s.left as string).replace('px', ''))
  return {
    position: 'absolute' as const,
    top: '2px',
    left: `${wrapLeft + size - 11}px`, // 右上角偏移
    zIndex: 12,
  }
}

// ---------- 里程碑拖拽改期(PATCH /milestones/{id}/plan-date) ----------
import { milestoneApi } from '@/api/gantt'
import { ElMessage, ElMessageBox } from 'element-plus'

/** 图例用: 数据里实际出现的 phaseId → hue, 取并集去重保持顺序 */
const phaseLegend = computed<Record<number, number>>(() => {
  const seen = new Set<number>()
  const out: Record<number, number> = {}
  for (const b of props.data?.bars ?? []) {
    for (const m of b.milestones) {
      if (m.phaseId && PHASE_HUE[m.phaseId] != null && !seen.has(m.phaseId)) {
        seen.add(m.phaseId)
        out[m.phaseId] = PHASE_HUE[m.phaseId]
      }
    }
  }
  return out
})

/** phaseId → 中文名 (从第一个匹配的 milestone 拿) */
function phaseName(pid: number): string {
  for (const b of props.data?.bars ?? []) {
    for (const m of b.milestones) {
      if (m.phaseId === pid) return m.phaseName ?? `阶段${pid}`
    }
  }
  return `阶段${pid}`
}

// 幽灵拖拽列表明细(最多 8 行,主拖排第一,辅拖按项目分组)
const GHOST_LIST_MAX = 8
const dragGhostPreview = computed(() => {
  if (!dragGhost.value || !dragCtx) return [] as Array<{ id: number; name: string; newDate: string }>
  // 把 id → name 建索引
  const nameById = new Map<number, string>()
  for (const b of props.data?.bars ?? []) {
    for (const mm of b.milestones) nameById.set(mm.id, mm.name)
  }
  // 主拖排第一
  const main = dragCtx.ids[0]
  const others = dragCtx.ids.slice(1)
  const rows: Array<{ id: number; name: string; newDate: string }> = []
  for (const id of [main, ...others]) {
    const orig = dragCtx.originalPlanDates.get(id)
    if (!orig) continue
    const d = toDate(orig)
    if (!d) continue
    rows.push({
      id,
      name: nameById.get(id) ?? `#${id}`,
      newDate: dateAtOffset(d, dragGhost.value.dayOffset),
    })
  }
  return rows.slice(0, GHOST_LIST_MAX)
})
const dragGhostHiddenCount = computed(() => {
  if (!dragGhost.value) return 0
  const total = dragGhost.value.count
  return Math.max(0, total - dragGhostPreview.value.length)
})

// ---------- 主控 + 辅拖的中心点(像素坐标,用于 SVG 画线) ----------
// ★ 项目色:稳定 HSL(基于 projectId 哈希,黄金角均匀分布)
const PROJECT_COLOR_CACHE = new Map<number, string>()
function projectColor(projectId: number): string {
  if (!PROJECT_COLOR_CACHE.has(projectId)) {
    const hue = Math.abs((projectId * 137.5) % 360) // 黄金角,均匀分布
    PROJECT_COLOR_CACHE.set(projectId, `hsl(${hue}, 70%, 55%)`)
  }
  return PROJECT_COLOR_CACHE.get(projectId)!
}
interface MilestonePos {
  x: number
  y: number
  projectId: number
  size: number
  color: string
}
// ★ 关键修复:用真实 DOM 位置而不是计算位置(因为缩放/滚动会让像素位置错位)
function getMilestonePosById(id: number): MilestonePos | null {
  if (!scrollRef.value) return null
  // 找 .gantt-milestone-wrap,里面 data-mid 是 milestone.id
  const el = scrollRef.value.querySelector(`.gantt-milestone-wrap[data-mid="${id}"]`) as HTMLElement | null
  if (!el) return null
  const wrap = scrollRef.value
  const wrapRect = wrap.getBoundingClientRect()
  const elRect = el.getBoundingClientRect()
  // 中心点(相对 svg 容器,svg 是 gantt-inner 的子元素,绝对定位,所以要算 relative to gantt-inner)
  // gantt-inner 起点 = wrapRect.left;主控中心 = (elRect.left + elRect.width/2) - wrapRect.left + wrap.scrollLeft
  // 但 svg 是 gantt-inner 的子元素,svg 的 (0,0) = gantt-inner 的 (0,0)
  // gantt-inner 的 left = wrapRect.left - wrap.scrollLeft?不,gantt-inner 的 left = wrapRect.left
  // 实际上,gantt-inner 是 wrap 的内容;wrap.scrollLeft 滚动的是 gantt-inner
  // 所以 svg 内的 x = elRect.left - wrapRect.left + wrap.scrollLeft
  const x = elRect.left - wrapRect.left + wrap.scrollLeft
  const y = elRect.top - wrapRect.top + wrap.scrollTop + elRect.height / 2
  const projectId = parseInt(el.dataset.projectId || '0')
  const size = elRect.width
  return { x, y, projectId, size, color: projectColor(projectId) }
}

// 主控中心点
const primaryDragCenter = computed<MilestonePos | null>(() => {
  if (primaryDragId.value == null) return null
  return getMilestonePosById(primaryDragId.value)
})

// 拖动轨迹的像素偏移(dragGhost.movedPx 直接是像素)
const dragGhostOffsetPx = computed(() => dragGhost.value?.movedPx ?? 0)

// 辅拖连接线:从主控当前位置到主控目标位置
const dragConnectionLines = computed(() => {
  if (!dragCtx || primaryDragId.value == null || !dragGhost.value) return []
  const main = getMilestonePosById(primaryDragId.value)
  if (!main) return []
  return dragCtx.ids
    .filter((id) => id !== primaryDragId.value)
    .map((id) => {
      const p = getMilestonePosById(id)
      if (!p) return null
      return {
        toId: id,
        x1: main.x + dragGhostOffsetPx.value,
        y1: main.y,
        x2: p.x + dragGhostOffsetPx.value,
        y2: p.y,
        color: p.color,
      }
    })
    .filter((x): x is NonNullable<typeof x> => !!x)
})

// 目标预览菱形(每个辅拖的"目标位置" ghost rect)
const dragTargetGhosts = computed(() => {
  if (!dragCtx || primaryDragId.value == null || !dragGhost.value) return []
  return dragCtx.ids
    .map((id) => {
      const p = getMilestonePosById(id)
      if (!p) return null
      // 目标位置 = 当前位置 + movedPx
      return {
        id,
        x: p.x + dragGhostOffsetPx.value - p.size / 2,
        y: p.y - p.size / 2,
        size: p.size,
        color: p.color,
      }
    })
    .filter((x): x is NonNullable<typeof x> => !!x)
})

// ---------- 远程协作(光标 + 拖动轨迹) ----------
export interface RemoteCursor {
  userId: number
  userName: string
  color: string
  x: number
  y: number
  labelWidth: number
  ts: number
}
export interface RemoteTrail {
  userId: number
  color: string
  startX: number | null
  currentX: number | null
  y: number
  dayOffset: number
}
const remoteCursors = ref<RemoteCursor[]>([])
const remoteTrails = ref<RemoteTrail[]>([])

// 暴露给父组件的协作 API(父组件用 SSE / WebSocket 接收广播,转给这里)
function applyRemoteCursor(c: Omit<RemoteCursor, 'ts' | 'labelWidth'>) {
  const labelWidth = Math.max(36, c.userName.length * 8 + 14)
  const next = remoteCursors.value.filter((rc) => rc.userId !== c.userId)
  next.push({ ...c, labelWidth, ts: Date.now() })
  remoteCursors.value = next
}
function applyRemoteTrail(t: RemoteTrail) {
  const next = remoteTrails.value.filter((rt) => rt.userId !== t.userId)
  next.push(t)
  remoteTrails.value = next
}
function clearRemoteUser(userId: number) {
  remoteCursors.value = remoteCursors.value.filter((rc) => rc.userId !== userId)
  remoteTrails.value = remoteTrails.value.filter((rt) => rt.userId !== userId)
}
// 暴露给父组件
defineExpose({ applyRemoteCursor, applyRemoteTrail, clearRemoteUser, projectColor })

// 广播节流(本地拖动时给父组件发)
let broadcastThrottle: number | null = null
function broadcastDrag(payload: { ids: number[]; dayOffset: number; x: number; y: number; ts: number }) {
  if (broadcastThrottle) return // 16ms 内只发一次(~60fps)
  broadcastThrottle = window.setTimeout(() => {
    broadcastThrottle = null
  }, 16)
  emit('drag-broadcast', payload)
}

// 拖拽中的所有里程碑(主拖 + 辅拖)。空 = 无拖拽
const draggingIds = ref<Set<number>>(new Set())
// 主拖的 id(用于区分"主拖的高亮"vs"辅拖的暗色高亮");null 时不区分(单选拖)
const primaryDragId = ref<number | null>(null)

// ---------- 远程协作(光标 + 拖动轨迹) + 过期清理 ----------
// ★ 协作:陈旧光标定时清理(5 秒没收到更新 → 移除)
const REMOTE_STALE_MS = 5000
let remoteStaleRaf: number | null = null
function startRemoteStaleSweep() {
  if (remoteStaleRaf) return
  const tick = () => {
    const now = Date.now()
    const before = remoteCursors.value.length
    remoteCursors.value = remoteCursors.value.filter((rc) => now - rc.ts < REMOTE_STALE_MS)
    if (remoteCursors.value.length !== before) {
      /* 触发响应式 */
    }
    remoteStaleRaf = requestAnimationFrame(tick)
  }
  remoteStaleRaf = requestAnimationFrame(tick)
}
onMounted(startRemoteStaleSweep)
const dragGhost = ref<{
  x: number
  y: number
  dayOffset: number
  movedPx: number
  planDate: string
  count: number
} | null>(null)

// 多选
const selectedIds = ref<Set<number>>(new Set())
const lastClickedId = ref<number | null>(null)

interface DragCtx {
  ids: number[] // 1 个或多个(多选时)
  projectIds: Map<number, number> // id -> projectId
  originalPlanDates: Map<number, string> // id -> 原 planDate
  startX: number
  movedPx: number
  lastDayOffset: number
}
let dragCtx: DragCtx | null = null
let autoScrollRaf: number | null = null
let edgeScrollDir: -1 | 0 | 1 = 0

function dayAtX(clientX: number): number {
  if (!scrollRef.value) return 0
  const rect = scrollRef.value.getBoundingClientRect()
  const xInInner = clientX - rect.left + scrollRef.value.scrollLeft - LABEL_W
  return Math.round(xInInner / pxPerDay())
}

function dateAtOffset(startDate: Date, dayOffset: number): string {
  const d = new Date(startDate)
  d.setDate(d.getDate() + dayOffset)
  return d.toISOString().slice(0, 10)
}

function onMilestoneClick(bar: GanttBar, m: GanttMilestone, e: MouseEvent) {
  if (e.shiftKey && lastClickedId.value != null) {
    // Shift+click 多选:从 lastClickedId 选到当前
    toggleRangeSelection(bar, m)
  } else if (e.ctrlKey || e.metaKey) {
    // Ctrl/Cmd+click 切换单选
    toggleSingleSelection(m.id)
    lastClickedId.value = m.id
  } else {
    // 普通 click:清空选择,只选这个;并打开详情
    if (selectedIds.value.size === 1 && selectedIds.value.has(m.id)) {
      // 已单选,再点 → 打开详情
      selectedIds.value = new Set()
      emit('milestone-click', { ...m, projectId: bar.projectId })
      return
    }
    selectedIds.value = new Set([m.id])
    lastClickedId.value = m.id
  }
}

function toggleSingleSelection(id: number) {
  const next = new Set(selectedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selectedIds.value = next
}

function toggleRangeSelection(bar: GanttBar, m: GanttMilestone) {
  // 简化为:把当前 visible 范围内所有里程碑当一维数组,Shift+click 选从 lastClicked 到 current 的连续段
  if (lastClickedId.value == null) {
    selectedIds.value = new Set([m.id])
    lastClickedId.value = m.id
    return
  }
  // 收集所有里程碑(扁平)
  const all: { id: number; projectId: number; ms: GanttMilestone }[] = []
  for (const b of props.data?.bars ?? []) {
    for (const mm of b.milestones) {
      all.push({ id: mm.id, projectId: b.projectId, ms: mm })
    }
  }
  const startIdx = all.findIndex((x) => x.id === lastClickedId.value)
  const endIdx = all.findIndex((x) => x.id === m.id)
  if (startIdx < 0 || endIdx < 0) {
    selectedIds.value = new Set([m.id])
    lastClickedId.value = m.id
    return
  }
  const [a, b] = startIdx < endIdx ? [startIdx, endIdx] : [endIdx, startIdx]
  const next = new Set<number>()
  for (let i = a; i <= b; i++) next.add(all[i].id)
  selectedIds.value = next
}

function clearSelection() {
  selectedIds.value = new Set()
  lastClickedId.value = null
}

/** gantt 背景区点击:只在点击空白时清除选中 (避免拖到任务条上误清) */
function onScrollerClick(e: MouseEvent) {
  if (e.target === e.currentTarget) clearSelection()
}

// 键盘改期:记录"键盘焦点"的里程碑(用 Tab 选)
const focusedId = ref<number | null>(null)

function focusMilestone(id: number | null) {
  focusedId.value = id
}

function onMilestoneKeydown(e: KeyboardEvent, bar: GanttBar, m: GanttMilestone) {
  if (e.key === 'Enter' || e.key === ' ') {
    e.preventDefault()
    emit('milestone-click', { ...m, projectId: bar.projectId })
  } else if (e.key === 'Escape') {
    e.preventDefault()
    clearSelection()
    focusMilestone(null)
  }
}

// 拖拽状态确认:开始时为 false,mousemove 触发后变 true,Esc 可取消
const dragConfirmOpen = ref(false)
let dragConfirmId: number | null = null

function onMilestoneDragStart(bar: GanttBar, m: GanttMilestone, e: MouseEvent) {
  // 已完成里程碑:不允拖拽(只让 click 触发详情)
  if (m.status === 'COMPLETED') return
  if (!scrollRef.value) return
  e.preventDefault()

  // 多选:如果当前里程碑在 selectedIds 里,拖所有;否则只拖当前
  // 注意:取一个不同名字的局部变量,避免和外层 draggingIds ref 冲突
  const idsToDrag: number[] = selectedIds.value.has(m.id) ? [...selectedIds.value] : [m.id]

  // 收集每个 id 的原 planDate 和 projectId
  const projectIds = new Map<number, number>()
  const originalPlanDates = new Map<number, string>()
  for (const b of props.data?.bars ?? []) {
    for (const mm of b.milestones) {
      if (idsToDrag.includes(mm.id)) {
        projectIds.set(mm.id, b.projectId)
        originalPlanDates.set(mm.id, mm.planDate)
      }
    }
  }

  dragCtx = {
    ids: idsToDrag,
    projectIds,
    originalPlanDates,
    startX: e.clientX,
    movedPx: 0,
    lastDayOffset: 0,
  }
  draggingIds.value = new Set(idsToDrag) // 所有被一起拖的 id
  primaryDragId.value = m.id // 主拖的 id
  document.body.style.cursor = 'grabbing'
  window.addEventListener('mousemove', onMilestoneDragMove)
  window.addEventListener('mouseup', onMilestoneDragEnd, { once: true })
  // 启动边缘自动滚动循环
  startAutoScroll()
  // ★ 拖动时:让主控自动滚到视图中心(便于边缘拖动时不需要手动滚)
  scrollMainIntoView(m.id)
}

/** 把里程碑滚到视口中央(用于拖动开始时锁定视野) */
function scrollMainIntoView(_id: number) {
  // 用 nextTick 等当前 mousemove 把主控移到目标位置后再滚
  void nextTick(() => {
    if (!scrollRef.value) return
    // 找主控的 DOM(用 data attr 标记更稳,这里用 querySelectorAll 过滤 class)
    const el = scrollRef.value.querySelector('.gantt-milestone-dragging') as HTMLElement | null
    if (!el) return
    // 不滚:用户刚按下,还不需要跳
    // 仅在主控不在视口内时滚,避免误触
    const wrap = scrollRef.value
    const wrapRect = wrap.getBoundingClientRect()
    const elRect = el.getBoundingClientRect()
    const margin = 80
    if (elRect.left < wrapRect.left + margin || elRect.right > wrapRect.right - margin) {
      // 主控在边缘 → 把视口滚到让主控在中央
      const target = wrap.scrollLeft + (elRect.left + elRect.width / 2) - (wrapRect.left + wrapRect.width / 2)
      wrap.scrollLeft = Math.max(0, Math.min(wrap.scrollWidth - wrap.clientWidth, target))
    }
  })
}

function onMilestoneDragMove(e: MouseEvent) {
  if (!dragCtx || !scrollRef.value) return
  dragCtx.movedPx = e.clientX - dragCtx.startX
  const dayOffset = Math.round(dragCtx.movedPx / pxPerDay())
  dragCtx.lastDayOffset = dayOffset
  // 主拖 milestone 的原日期
  const mainId = dragCtx.ids[0]
  const originalPlanDate = dragCtx.originalPlanDates.get(mainId)
  if (!originalPlanDate) return
  const originalDate = toDate(originalPlanDate)
  if (!originalDate) return
  const newPlanDate = dateAtOffset(originalDate, dayOffset)
  // 边缘方向(根据 clientX 相对 scroller viewport 位置)
  const rect = scrollRef.value.getBoundingClientRect()
  const distToLeft = e.clientX - rect.left
  const distToRight = rect.right - e.clientX
  const EDGE = 50
  let dir: -1 | 0 | 1 = 0
  if (distToLeft < EDGE) dir = -1
  else if (distToRight < EDGE) dir = 1
  edgeScrollDir = dir
  dragGhost.value = {
    x: e.clientX,
    y: e.clientY,
    dayOffset,
    movedPx: dragCtx.movedPx,
    planDate: newPlanDate,
    count: dragCtx.ids.length,
  }
  // 第一次真正移动 + dayOffset != 0 → 弹确认提示(只弹一次,直到 mouseup)
  if (!dragConfirmOpen.value && dayOffset !== 0) {
    dragConfirmOpen.value = true
  }
  // ★ 协作广播:本地拖动时给父组件发(16ms 节流,父组件转给 SSE/WebSocket)
  broadcastDrag({
    ids: dragCtx.ids,
    dayOffset,
    x: e.clientX,
    y: e.clientY,
    ts: Date.now(),
  })
}

function startAutoScroll() {
  if (autoScrollRaf) cancelAnimationFrame(autoScrollRaf)
  const tick = () => {
    if (!dragCtx || !scrollRef.value) {
      autoScrollRaf = null
      return
    }
    if (edgeScrollDir !== 0) {
      const speed = edgeScrollDir === -1 ? -8 : 8
      const max = scrollRef.value.scrollWidth - scrollRef.value.clientWidth
      scrollRef.value.scrollLeft = Math.max(0, Math.min(max, scrollRef.value.scrollLeft + speed))
    }
    autoScrollRaf = requestAnimationFrame(tick)
  }
  autoScrollRaf = requestAnimationFrame(tick)
}

function stopAutoScroll() {
  edgeScrollDir = 0
  if (autoScrollRaf) {
    cancelAnimationFrame(autoScrollRaf)
    autoScrollRaf = null
  }
}

async function onMilestoneDragEnd() {
  window.removeEventListener('mousemove', onMilestoneDragMove)
  document.body.style.cursor = ''
  stopAutoScroll()
  const ctx = dragCtx
  dragCtx = null
  draggingIds.value = new Set() // 重置:所有被一起拖的
  primaryDragId.value = null // 重置:主拖的
  const ghost = dragGhost.value
  dragGhost.value = null
  const confirmOpen = dragConfirmOpen.value
  dragConfirmOpen.value = false
  if (!ctx || !ghost) return
  if (ghost.dayOffset === 0) return
  // ★ 协作广播:本地拖动结束(让协作者清掉他们的 ghost 轨迹)
  emit('drag-end-broadcast', { ids: ctx.ids, dayOffset: ghost.dayOffset, ts: Date.now() })
  // ★ 拖拽确认:批量 >=2 个且 confirmOpen 还在(用户没主动取消)→ 弹一次确认 toast
  // 注:ElMessageBox 会阻塞;为了不打断主流程,改用 ElMessage + ElNotification
  if (confirmOpen && ctx.ids.length >= 2) {
    // 给个非阻塞提示,用户可继续拖
    showBatchConfirm(ctx, ghost)
    // 等待用户点 "应用" 按钮才真发请求;点 "取消" 则直接返回
    return
  }
  // 直接发请求
  await performBatchPatch(ctx, ghost)
}

/** 显示批量改期确认提示(非阻塞,用户可点取消) */
function showBatchConfirm(ctx: DragCtx, ghost: { dayOffset: number; planDate: string; count: number }) {
  // ★ 用 h() 渲染富文本预览列表(避免 dangerouslyUseHTMLString)
  const previewRows = dragGhostPreview.value
  const hidden = dragGhostHiddenCount.value
  const daySign = ghost.dayOffset > 0 ? '+' : ''
  ElMessageBox({
    title: '批量改期确认',
    type: 'warning',
    customClass: 'gantt-batch-confirm',
    showCancelButton: true,
    confirmButtonText: '应用',
    cancelButtonText: '取消',
    closeOnClickModal: false,
    closeOnPressEscape: true,
    message: () =>
      h('div', { class: 'gantt-batch-confirm-body' }, [
        h('div', { class: 'gantt-batch-confirm-header' }, [
          h('span', null, `📅 即将改期 `),
          h('strong', null, `${ghost.count} 个里程碑`),
          h('span', null, ',共偏移 '),
          h('strong', { class: 'gantt-batch-confirm-offset' }, `${daySign}${ghost.dayOffset} 天`),
          h('span', null, '。主控日期:'),
          h('strong', null, ` ${ghost.planDate}`),
        ]),
        // ★ 预览列表(展示每个被改期里程碑的:◆ 名称 → 新日期)
        h(
          'div',
          { class: 'gantt-batch-confirm-list' },
          previewRows.map((row) =>
            h('div', { class: 'gantt-batch-confirm-row' }, [
              h(
                'span',
                {
                  class: 'gantt-batch-confirm-bullet ' + (row.id === ctx.ids[0] ? 'is-primary' : 'is-batch'),
                },
                '◆',
              ),
              h('span', { class: 'gantt-batch-confirm-name' }, row.name),
              h('span', { class: 'gantt-batch-confirm-date' }, row.newDate),
            ]),
          ),
        ),
        hidden > 0 ? h('div', { class: 'gantt-batch-confirm-more' }, `… 还有 ${hidden} 个`) : null,
      ]),
  })
    .then(async () => {
      await performBatchPatch(ctx, ghost)
    })
    .catch(() => {
      // 取消 → 刷一下数据(父组件会重拉甘特,数据自动还原)
      ElMessage.info('已取消批量改期')
      emit('milestone-moved', { id: -1, projectId: -1, newPlanDate: '' })
    })
}

/** 真正执行批量 PATCH(主流程) */
async function performBatchPatch(
  ctx: DragCtx,
  ghost: { dayOffset: number; planDate: string; count: number },
) {
  const isBatch = ctx.ids.length > 1
  try {
    const promises = ctx.ids.map((id) => {
      const orig = ctx.originalPlanDates.get(id)!
      const d = toDate(orig)!
      const newDate = dateAtOffset(d, ghost.dayOffset)
      return milestoneApi
        .patchPlanDate(id, newDate)
        .then((updated: any) => ({
          id,
          newPlanDate: newDate,
          projectId: ctx.projectIds.get(id)!,
          ok: true as const,
          updated,
        }))
        .catch((err: any) => ({ id, ok: false as const, err: err.message ?? '改期失败' }))
    })
    const results = await Promise.all(promises)
    const ok = results.filter((r: any) => r.ok).length
    const fail = results.length - ok
    if (ok > 0) {
      ElMessage.success(
        isBatch ? `已批量改期 ${ok} 个里程碑${fail ? ` (${fail} 失败)` : ''}` : `已改期至 ${ghost.planDate}`,
      )
      // 发一个聚合事件(用主拖的 id)
      emit('milestone-moved', {
        id: ctx.ids[0],
        projectId: ctx.projectIds.get(ctx.ids[0])!,
        newPlanDate: ghost.planDate,
      })
    }
    if (fail > 0 && ok === 0) {
      ElMessage.error('全部改期失败')
    }
  } catch (err: any) {
    ElMessage.error(err.message ?? '改期失败')
  }
}

// 键盘改期(全局):焦点在甘特图内时,←/→ 调 ±1 天
async function onGlobalKeydownForReschedule(e: KeyboardEvent) {
  if (!focusedId.value) return
  const target = e.target as HTMLElement | null
  if (target && ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName)) return
  // 找聚焦的里程碑
  for (const b of props.data?.bars ?? []) {
    for (const mm of b.milestones) {
      if (mm.id !== focusedId.value) continue
      if (mm.status === 'COMPLETED') return // 完成的不能改
      if (e.key === 'ArrowLeft') {
        e.preventDefault()
        await rescheduleOne(mm.id, b.projectId, mm.planDate, -1)
        return
      }
      if (e.key === 'ArrowRight') {
        e.preventDefault()
        await rescheduleOne(mm.id, b.projectId, mm.planDate, 1)
        return
      }
      if (e.key === 'ArrowLeft' && e.shiftKey) {
        e.preventDefault()
        await rescheduleOne(mm.id, b.projectId, mm.planDate, -7)
        return
      }
      if (e.key === 'ArrowRight' && e.shiftKey) {
        e.preventDefault()
        await rescheduleOne(mm.id, b.projectId, mm.planDate, 7)
        return
      }
    }
  }
}

async function rescheduleOne(id: number, projectId: number, originalPlanDate: string, delta: number) {
  const d = toDate(originalPlanDate)!
  const newPlanDate = dateAtOffset(d, delta)
  try {
    await milestoneApi.patchPlanDate(id, newPlanDate)
    ElMessage.success(`已改期至 ${newPlanDate}`)
    emit('milestone-moved', { id, projectId, newPlanDate })
  } catch (err: any) {
    ElMessage.error(err.message ?? '改期失败')
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('keydown', onGlobalKeydownForReschedule)
})
onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('keydown', onGlobalKeydownForReschedule)
  stopAutoScroll()
  if (remoteStaleRaf) cancelAnimationFrame(remoteStaleRaf)
  if (broadcastThrottle) clearTimeout(broadcastThrottle)
})

const STATUS_COLOR: Record<string, string> = {
  PENDING: '#909399',
  IN_PROGRESS: '#e6a23c',
  COMPLETED: '#67c23a',
  DELAYED: '#f56c6c',
}

/**
 * V3.1: 7 阶段 × 4 槽位 = 28 色矩阵 — 同 phase 内不同 milestone 不同颜色
 *
 * 设计思路:
 *  - 同一 phase 的里程碑共享"主色相" (视觉分组:立项 / 需求 / 设计 / 开发 / 测试 / 上线 / 维保)
 *  - 同 phase 内按"在 (project_id, phase_id) 内的序号"取 4 个深浅梯度 (slot 0..3)
 *  - 槽位分配: phase 内按 sequence 升序排,m.phaseSeqIndex (0..3) → 不同深浅
 *  - 这样需求评审 vs 需求确认 vs 需求变更 → 同一蓝色家族的 4 种深浅,既"分组"又"区分"
 *  - 跨 phase: 立项紫 / 需求蓝 / 设计青绿 / 开发橙 / 测试红 / 上线绿 / 维保灰 → 一眼可分辨
 */
const PHASE_HUE: Record<number, number> = {
  1: 280, // 立项 — 紫 (HSL hue 280)
  2: 210, // 需求 — 蓝 (HSL hue 210)
  3: 165, // 设计 — 青绿 (HSL hue 165)
  4: 30, // 开发 — 橙 (HSL hue 30)
  5: 0, // 测试 — 红 (HSL hue 0)
  6: 145, // 上线运维 — 绿 (HSL hue 145)
  7: 200, // 维保 — 灰蓝 (HSL hue 200)
}

/** 同 phase 内 4 个槽位 → 饱和度 / 亮度梯度 (从最深到最浅) */
const SLOT_STYLE: { s: number; l: number }[] = [
  { s: 75, l: 42 }, // 深 — 主色
  { s: 65, l: 52 }, // 中
  { s: 55, l: 62 }, // 浅
  { s: 45, l: 72 }, // 极浅 — 第 4 个 milestone 仍可区分
]

/** HSL → HEX (用于 SVG / inline style) */
function hslToHex(h: number, s: number, l: number): string {
  // h ∈ [0,360), s/l ∈ [0,100]
  const sn = s / 100,
    ln = l / 100
  const c = (1 - Math.abs(2 * ln - 1)) * sn
  const hp = h / 60
  const x = c * (1 - Math.abs((hp % 2) - 1))
  let r!: number, g!: number, b!: number
  if (hp < 1) [r, g, b] = [c, x, 0]
  else if (hp < 2) [r, g, b] = [x, c, 0]
  else if (hp < 3) [r, g, b] = [0, c, x]
  else if (hp < 4) [r, g, b] = [0, x, c]
  else if (hp < 5) [r, g, b] = [x, 0, c]
  else [r, g, b] = [c, 0, x]
  const m = ln - c / 2
  const toHex = (v: number) => {
    const n = Math.round((v + m) * 255)
    return Math.max(0, Math.min(255, n)).toString(16).padStart(2, '0')
  }
  return '#' + toHex(r) + toHex(g) + toHex(b)
}

/**
 * 取 phase 内序号: 同 phaseId 内,按出现顺序 0,1,2,3 循环
 * 注: GanttMilestone 没带 phase 内的序号字段 (DTO 没返 sequence),
 *     这里用 id 在 bar.milestones 内匹配 phaseId 后再统计
 *     → 简单且不引入新接口
 */
function milestoneSlotIndex(bar: GanttBar, m: GanttMilestone): number {
  if (!m.phaseId) return -1
  let idx = 0
  for (const x of bar.milestones) {
    if (x.phaseId === m.phaseId) {
      if (x.id === m.id) return idx % SLOT_STYLE.length
      idx++
    }
  }
  return 0
}

/**
 * 里程碑节点颜色: phaseId + phase 内序号 → HSL → HEX
 *  - 没有 phaseId → fallback 到 STATUS_COLOR (旧行为,留作兜底)
 *  - 这是"用不同的颜色表示不同的里程碑"的核心实现
 */
function milestoneColor(bar: GanttBar, m: GanttMilestone): string {
  if (m.phaseId && PHASE_HUE[m.phaseId] != null) {
    const slot = milestoneSlotIndex(bar, m)
    const { s, l } = SLOT_STYLE[slot < 0 ? 0 : slot]
    return hslToHex(PHASE_HUE[m.phaseId], s, l)
  }
  return STATUS_COLOR[m.status] || '#909399'
}

// 表格列:左 220px 标签 + 右 N px 时间轴(N = days * pxPerDay,固定宽度 → 横向滚动)
const LABEL_W = 220
const PX_PER_DAY = 18 // 每天 18px;默认 90 天 ≈ 1620px 容器宽

// 缩放:用百分比 60%~200%,实际 px = PX_PER_DAY * zoom/100
const zoom = ref(100)
const scrollRef = ref<HTMLElement | null>(null)

function pxPerDay() {
  return (PX_PER_DAY * zoom.value) / 100
}
function trackWidth() {
  return Math.max(800, totalDays.value * pxPerDay())
}
function innerWidth() {
  return LABEL_W + trackWidth()
}
const gridTemplate = computed(() => `${LABEL_W}px ${trackWidth()}px`)

// 滚动
function scrollToStart() {
  if (scrollRef.value) scrollRef.value.scrollLeft = 0
}
function scrollToEnd() {
  if (scrollRef.value) scrollRef.value.scrollLeft = scrollRef.value.scrollWidth
}
function scrollToToday() {
  if (!scrollRef.value) return
  const days = (Date.now() - viewFrom.value.getTime()) / 86400000
  if (days < 0 || days > totalDays.value) return
  // 标签列 220 + today 位置 - 视口半宽
  const target = LABEL_W + days * pxPerDay() - scrollRef.value.clientWidth / 2
  scrollRef.value.scrollLeft = Math.max(0, target)
}
function onZoom() {
  /* 缩放后让今天保持在视图中央 */ void nextTick(scrollToToday)
}

// ---------- 键盘 ←/→ 滚动(7 天一格) ----------
function scrollByDays(days: number) {
  if (!scrollRef.value) return
  const step = days * pxPerDay()
  const max = scrollRef.value.scrollWidth - scrollRef.value.clientWidth
  scrollRef.value.scrollLeft = Math.max(0, Math.min(max, scrollRef.value.scrollLeft + step))
}
function onKeydown(e: KeyboardEvent) {
  // 焦点在表单控件时不抢键
  const tag = (e.target as HTMLElement | null)?.tagName?.toLowerCase()
  if (tag === 'input' || tag === 'textarea' || tag === 'select') return
  if (!props.data || !props.data.bars.length) return
  switch (e.key) {
    case 'ArrowLeft':
      e.preventDefault()
      scrollByDays(e.shiftKey ? -30 : -7)
      break
    case 'ArrowRight':
      e.preventDefault()
      scrollByDays(e.shiftKey ? 30 : 7)
      break
    case 'Home':
      e.preventDefault()
      scrollToStart()
      break
    case 'End':
      e.preventDefault()
      scrollToEnd()
      break
    case 'PageUp':
      e.preventDefault()
      scrollByDays(-30)
      break
    case 'PageDown':
      e.preventDefault()
      scrollByDays(30)
      break
    case 't':
    case 'T':
      e.preventDefault()
      scrollToToday()
      break
  }
}
// 旧的 onMounted/onUnmounted 已被下方的统一注册取代

// ---------- Ctrl+滚轮缩放(鼠标在时间轴上) ----------
function onWheel(e: WheelEvent) {
  if (!e.ctrlKey && !e.metaKey) return // 没按 Ctrl 走默认行为(竖向滚动 → 实际很少触发,被 max-height 兜住)
  if (!props.data || !props.data.bars.length) return
  e.preventDefault()
  // 滚上(deltaY<0)=放大,滚下=缩小
  const delta = e.deltaY < 0 ? 10 : -10
  const newZoom = Math.max(60, Math.min(200, zoom.value + delta))
  if (newZoom === zoom.value) return
  // 以鼠标位置为锚缩放,不让用户丢失当前视野中心
  const oldPx = pxPerDay()
  const newPx = (PX_PER_DAY * newZoom) / 100
  const rect = scrollRef.value!.getBoundingClientRect()
  const mouseXInScroller = e.clientX - rect.left + (scrollRef.value?.scrollLeft ?? 0)
  // 鼠标对应的"日期" = (mouseX - LABEL_W) / oldPx
  const dayAtMouse = (mouseXInScroller - LABEL_W) / oldPx
  // 缩放后,要保持 dayAtMouse 仍出现在鼠标位置 → 反算 scrollLeft
  const newScrollLeft = LABEL_W + dayAtMouse * newPx - (e.clientX - rect.left)
  zoom.value = newZoom
  void nextTick(() => {
    if (scrollRef.value) {
      scrollRef.value.scrollLeft = Math.max(0, newScrollLeft)
    }
  })
}

// 监听数据变化,首次加载滚到今天
watch(
  () => props.data,
  (d) => {
    if (d && d.bars.length) void nextTick(scrollToToday)
  },
)

// ---------- "今天" 在视图中的位置(0~100%) ----------
const todayX = computed(() => {
  const days = (Date.now() - viewFrom.value.getTime()) / 86400000
  if (days < 0 || days > totalDays.value) return -1
  return (days / totalDays.value) * 100
})
</script>

<template>
  <div v-loading="props.loading" class="gantt-wrap">
    <!-- 工具条:缩放 + 滚到今天/到首/到尾 -->
    <div v-if="props.data && props.data.bars.length" class="gantt-toolbar">
      <span class="gantt-toolbar-label">缩放</span>
      <el-slider
        v-model="zoom"
        :min="6"
        :max="40"
        :step="1"
        :format-tooltip="(v: number) => `${v}px/天`"
        style="width: 160px"
        @change="onZoom"
      />
      <el-button-group>
        <el-button size="small" :icon="Refresh" @click="scrollToStart">← 起点</el-button>
        <el-button size="small" @click="scrollToToday">今天</el-button>
        <el-button size="small" :icon="Refresh" @click="scrollToEnd">终点 →</el-button>
      </el-button-group>
      <span style="margin-left: auto; font-size: 12px; color: #909399">
        共 {{ totalDays }} 天 · {{ Math.round((totalDays * PX_PER_DAY * zoom) / 100) }}px 宽 ·
        <span style="color: #c0c4cc">
          快捷键:← → 滚 7 天 / Shift+← → 滚 30 天 / Home/End 起点终点 / T 今天 / Ctrl+滚轮 缩放 ·
          里程碑:⇧+点选多 / Tab+Enter 打开 / ←→ ±1 天 (Shift:±7)
        </span>
      </span>
    </div>

    <!-- 滚动容器 -->
    <div
      ref="scrollRef"
      class="gantt-scroller"
      :class="{ 'gantt-scroller-dragging': !!dragCtx }"
      @wheel="onWheel"
      tabindex="0"
      @click="onScrollerClick"
    >
      <div
        class="gantt-inner"
        :style="{ width: `${LABEL_W + Math.max(800, (totalDays * PX_PER_DAY * zoom) / 100)}px` }"
      >
        <!-- ★ SVG 叠层:主控拖动轨迹 + 辅拖连接线 + 远程协作光标 + 远程协作轨迹 -->
        <svg
          v-if="dragCtx || remoteCursors.length > 0"
          class="gantt-svg-overlay"
          :width="LABEL_W + Math.max(800, (totalDays * PX_PER_DAY * zoom) / 100)"
          :height="Math.max(60, (props.data?.bars.length || 0) * 60 + 60)"
        >
          <!-- 主控拖动轨迹(主线) -->
          <template v-if="dragCtx && dragGhost">
            <line
              v-if="primaryDragCenter && dragGhost.x"
              :x1="primaryDragCenter.x"
              :y1="primaryDragCenter.y"
              :x2="primaryDragCenter.x + dragGhostOffsetPx"
              :y2="primaryDragCenter.y"
              stroke="#409eff"
              stroke-width="2"
              opacity="0.5"
              stroke-dasharray="6 3"
            />
            <!-- 辅拖到目标位置的细线 -->
            <line
              v-for="(line, i) in dragConnectionLines"
              :key="'conn-' + line.toId + '-' + i"
              :x1="line.x1"
              :y1="line.y1"
              :x2="line.x2"
              :y2="line.y2"
              :stroke="line.color"
              stroke-width="1.5"
              opacity="0.7"
              stroke-dasharray="4 3"
            />
            <!-- 目标预览菱形(每个辅拖的 ghost) -->
            <template v-for="(ghost, gid) in dragTargetGhosts" :key="'gh-' + gid">
              <rect
                :x="ghost.x"
                :y="ghost.y"
                :width="ghost.size"
                :height="ghost.size"
                :transform="`rotate(45 ${ghost.x + ghost.size / 2} ${ghost.y + ghost.size / 2})`"
                :fill="ghost.color"
                opacity="0.35"
                :stroke="ghost.color"
                stroke-width="1.5"
                stroke-dasharray="3 2"
              />
            </template>
          </template>
          <!-- 远程协作者的光标(只显示鼠标位置 + 用户名标签) -->
          <template v-for="rc in remoteCursors" :key="rc.userId">
            <g :transform="`translate(${rc.x}, ${rc.y})`">
              <!-- 鼠标箭头 -->
              <path d="M 0 0 L 14 5 L 6 7 L 4 14 Z" :fill="rc.color" stroke="#fff" stroke-width="1" />
              <!-- 用户名气泡 -->
              <rect x="14" y="2" :width="rc.labelWidth" height="18" rx="3" :fill="rc.color" />
              <text
                x="14 + rc.labelWidth/2"
                y="15"
                text-anchor="middle"
                fill="#fff"
                font-size="11"
                font-weight="600"
              >
                {{ rc.userName }}
              </text>
            </g>
          </template>
          <!-- 远程协作者的拖动轨迹 -->
          <template v-for="rt in remoteTrails" :key="'rt-' + rt.userId">
            <line
              v-if="rt.startX != null && rt.currentX != null"
              :x1="rt.startX"
              :y1="rt.y"
              :x2="rt.currentX"
              :y2="rt.y"
              :stroke="rt.color"
              stroke-width="2"
              opacity="0.4"
              stroke-dasharray="6 3"
            />
          </template>
        </svg>

        <!-- 头部月份刻度 -->
        <div v-if="props.data && props.data.bars.length" class="gantt-month-bar">
          <div class="gantt-axis-spacer" />
          <div class="gantt-axis-track">
            <div v-for="(t, i) in monthTicks" :key="i" class="gantt-month-tick" :style="{ left: t.x + '%' }">
              <span class="gantt-month-label">{{ t.label }}</span>
            </div>
            <div v-if="todayX >= 0 && todayX <= 100" class="gantt-today-line" :style="{ left: todayX + '%' }">
              <span class="gantt-today-label">今天</span>
            </div>
          </div>
        </div>

        <!-- 列表 -->
        <div v-else-if="!props.loading" class="gantt-empty">
          <el-empty description="暂无项目数据" />
        </div>

        <div v-if="props.data && props.data.bars.length" class="gantt-body">
          <div v-for="bar in props.data.bars" :key="bar.projectId" class="gantt-row">
            <!-- 左:项目卡 -->
            <div class="gantt-label">
              <div style="font-weight: 600; font-size: 13px">{{ bar.projectCode }}</div>
              <div
                style="
                  font-size: 12px;
                  color: #606266;
                  white-space: nowrap;
                  overflow: hidden;
                  text-overflow: ellipsis;
                "
                :title="bar.projectName"
              >
                {{ bar.projectName }}
              </div>
              <div style="font-size: 11px; color: #909399; margin-top: 2px">
                进度 {{ bar.progressPct }}% · 里程碑 {{ bar.milestones.length }}
              </div>
            </div>

            <!-- 右:时间轴 -->
            <div class="gantt-track">
              <!-- 计划区间(背景) -->
              <div
                class="gantt-bar gantt-bar-plan"
                :style="planBarStyle(bar)"
                :title="`计划 ${bar.planStart} ~ ${bar.planEnd}`"
              />
              <!-- 实际区间(实色) -->
              <div
                class="gantt-bar gantt-bar-actual"
                :style="barStyle(bar)"
                :title="`实际 ${bar.actualStart || bar.planStart} ~ ${bar.actualEnd || bar.planEnd}`"
              />
              <!-- 进度覆盖 -->
              <div class="gantt-bar gantt-bar-progress" :style="progressStyle(bar)" />
              <!-- 里程碑菱形(可点击 + 可拖拽改期 + 可多选 + 键盘改期) -->
              <div
                v-for="m in bar.milestones"
                :key="m.id"
                :data-mid="m.id"
                :data-project-id="bar.projectId"
                :data-project-color="projectColor(bar.projectId)"
                :data-phase-id="m.phaseId ?? 0"
                :data-status="m.status"
                class="gantt-milestone-wrap"
                :style="{
                  ...milestoneStyle(bar, m),
                  '--project-color': projectColor(bar.projectId),
                  '--ms-color': milestoneColor(bar, m),
                }"
              >
                <div
                  class="gantt-milestone"
                  :class="{
                    'gantt-milestone-dragging': primaryDragId === m.id,
                    'gantt-milestone-batch-dragging': draggingIds.has(m.id) && primaryDragId !== m.id,
                    'gantt-milestone-completed': m.status === 'COMPLETED',
                    'gantt-milestone-selected': selectedIds.has(m.id) && !draggingIds.has(m.id),
                    'gantt-milestone-focused': focusedId === m.id && !draggingIds.has(m.id),
                  }"
                  :title="`${m.name}\n阶段: ${m.phaseName ?? '未分阶段'}\n计划: ${m.planDate}\n${m.actualDate ? '实际: ' + m.actualDate : '未完成'}\n状态: ${m.status}\n权重: ${m.weight}\n\n🖱 单击:打开详情(再次单击)\n🖱 拖拽:改期\n⇧ + 单击:多选连续段\n⌘/Ctrl + 单击:多选切换\nTab + Enter:键盘进入 / 打开\n← / →:改 ±1 天 (Shift:±7 天)`"
                  :style="{
                    'background-color': 'var(--ms-color, ' + milestoneColor(bar, m) + ')',
                    cursor: m.status === 'COMPLETED' ? 'pointer' : 'grab',
                  }"
                  tabindex="0"
                  @click.stop="(e) => onMilestoneClick(bar, m, e)"
                  @mousedown.stop="(e) => onMilestoneDragStart(bar, m, e)"
                  @focus="focusMilestone(m.id)"
                  @blur="focusMilestone(null)"
                  @keydown.stop="(e) => onMilestoneKeydown(e, bar, m)"
                />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 拖拽幽灵提示 -->
    <div
      v-if="dragGhost"
      class="gantt-drag-ghost"
      :style="{ left: dragGhost.x + 12 + 'px', top: dragGhost.y - 36 + 'px' }"
    >
      <div class="gantt-drag-ghost-main">
        {{ dragGhost.count > 1 ? `📅 ${dragGhost.count} 个 → ` : '📅 ' }}{{ dragGhost.planDate }} ({{
          dragGhost.dayOffset > 0 ? '+' : ''
        }}{{ dragGhost.dayOffset }} 天)
      </div>
      <!-- 批量时:列出每个辅拖的明细(主拖第一个) -->
      <div v-if="dragGhost.count > 1" class="gantt-drag-ghost-list">
        <div v-for="row in dragGhostPreview" :key="row.id" class="gantt-drag-ghost-row">
          <span
            class="gantt-drag-ghost-bullet"
            :class="{ 'is-primary': row.id === primaryDragId, 'is-batch': row.id !== primaryDragId }"
          >
            ◆
          </span>
          <span class="gantt-drag-ghost-name">{{ row.name }}</span>
          <span class="gantt-drag-ghost-date">{{ row.newDate }}</span>
        </div>
        <div v-if="dragGhostHiddenCount > 0" class="gantt-drag-ghost-more">
          … 还有 {{ dragGhostHiddenCount }} 个
        </div>
      </div>
    </div>

    <!-- 图例 -->
    <div v-if="props.data && props.data.bars.length" class="gantt-legend">
      <span>
        <i class="lg-plan" />
        计划区间
      </span>
      <span>
        <i class="lg-actual" />
        实际区间
      </span>
      <span>
        <i class="lg-progress" />
        进度覆盖
      </span>
      <!-- 阶段色图例 (同 phase 同色相;同 phase 内 4 个深浅 = 不同 milestone) -->
      <span class="legend-divider">阶段:</span>
      <span v-for="(hue, pid) in phaseLegend" :key="String(pid)" :title="phaseName(Number(pid))">
        <i class="lg-diamond" :style="{ background: hslToHex(Number(hue), 75, 42) }" />
        {{ phaseName(Number(pid)) }}
      </span>
      <span style="margin-left: auto; color: #909399; font-size: 12px">
        <el-icon style="vertical-align: middle"><Calendar /></el-icon>
        区间 {{ props.data.rangeFrom }} ~ {{ props.data.rangeTo }} · 共 {{ props.data.projectCount }} 项目 ·
        模式:{{ props.mode === 'auto' ? '自动' : '手动' }}
        · 菱形色 = 阶段(同色相 4 深浅 = phase 内不同 milestone)
      </span>
    </div>
  </div>
</template>

<style scoped>
.gantt-wrap {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fafbfc;
  padding: 8px;
}
.gantt-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 8px 8px;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 4px;
}
.gantt-toolbar-label {
  font-size: 12px;
  color: #909399;
}
/* ★ 关键:横向滚动 */
.gantt-scroller {
  overflow-x: auto;
  overflow-y: hidden;
  width: 100%;
  max-height: 60vh;
  /* Firefox 兼容 */
  scrollbar-width: thin;
  scrollbar-color: #c0c4cc #f5f7fa;
}
.gantt-scroller::-webkit-scrollbar {
  height: 8px;
}
.gantt-scroller::-webkit-scrollbar-track {
  background: #f5f7fa;
}
.gantt-scroller::-webkit-scrollbar-thumb {
  background: #c0c4cc;
  border-radius: 4px;
}
.gantt-scroller::-webkit-scrollbar-thumb:hover {
  background: #909399;
}
/* 拖拽时滚动条变蓝,提示可拖到边缘外 */
.gantt-scroller-dragging::-webkit-scrollbar-thumb {
  background: #409eff;
}
.gantt-scroller-dragging::after {
  /* 提示边缘:左右各一个渐变指示 */
  content: '';
  position: sticky;
  display: block;
  height: 4px;
  background: linear-gradient(
    90deg,
    rgba(64, 158, 255, 0.3),
    transparent 30%,
    transparent 70%,
    rgba(64, 158, 255, 0.3)
  );
  pointer-events: none;
}

.gantt-inner {
  /* width 由 inline-style 注入 */
  min-width: 100%;
  position: relative;
}

.gantt-month-bar {
  display: grid;
  grid-template-columns: v-bind(gridTemplate);
  border-bottom: 1px solid #e4e7ed;
  padding-bottom: 4px;
  margin-bottom: 4px;
  position: sticky;
  top: 0;
  background: #fafbfc;
  z-index: 3;
}
.gantt-axis-spacer {
  width: 220px;
}
.gantt-axis-track {
  position: relative;
  height: 22px;
}
.gantt-month-tick {
  position: absolute;
  top: 0;
  transform: translateX(-1px);
  border-left: 1px dashed #dcdfe6;
  height: 100%;
  padding-left: 4px;
}
.gantt-month-label {
  font-size: 11px;
  color: #909399;
  background: #fafbfc;
  padding: 0 2px;
}
/* ★ "今天"竖线 */
.gantt-today-line {
  position: absolute;
  top: 0;
  width: 0;
  border-left: 2px solid #f56c6c;
  height: 100%;
  z-index: 2;
  pointer-events: none;
}
.gantt-today-label {
  position: absolute;
  top: -2px;
  left: 4px;
  font-size: 10px;
  color: #f56c6c;
  background: #fef0f0;
  padding: 0 4px;
  border-radius: 2px;
  white-space: nowrap;
}

.gantt-empty {
  padding: 40px 0;
}
.gantt-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.gantt-row {
  display: grid;
  grid-template-columns: v-bind(gridTemplate);
  height: 56px;
  align-items: center;
  border-bottom: 1px dashed #ebeef5;
}
.gantt-label {
  padding: 4px 8px;
  border-right: 1px solid #ebeef5;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  position: sticky;
  left: 0;
  background: #fafbfc;
  z-index: 1;
}
.gantt-track {
  position: relative;
  height: 36px;
  background-image: linear-gradient(to right, #ebeef5 0, #ebeef5 1px, transparent 1px, transparent 100%);
  background-size: calc(v-bind('pxPerDay()') * 7px) 100%; /* 7 天一周分隔 */
  border-radius: 3px;
}
.gantt-bar {
  position: absolute;
  top: 6px;
  height: 24px;
  border-radius: 3px;
  pointer-events: auto;
}
.gantt-bar-plan {
  background: rgba(103, 194, 58, 0.18);
  border: 1px dashed #b3e19d;
  height: 30px;
  top: 3px;
}
.gantt-bar-actual {
  background: rgba(64, 158, 255, 0.45);
  border: 1px solid #409eff;
}
.gantt-bar-progress {
  background: rgba(103, 194, 58, 0.85);
  height: 6px;
  top: 14px;
  border-radius: 2px;
  pointer-events: none;
}
.gantt-svg-overlay {
  position: absolute;
  top: 0;
  left: 0;
  pointer-events: none;
  z-index: 6;
}
.gantt-milestone-wrap {
  display: inline-block; /* ★ 防止 v-for fragment 把它当 transparent 折叠掉 */
  position: absolute;
  z-index: 2;
  /* 尺寸继承 milestoneStyle() 的 left/width/height/position:top */
  background-color: var(
    --ms-color,
    transparent
  ); /* ★ V3.1 兜底:内联 background-color 失效时,wrap div 也染色 */
}
/* 批量确认弹窗:预览列表 */
.gantt-batch-confirm-body {
  font-size: 13px;
}
.gantt-batch-confirm-header {
  line-height: 1.6;
  margin-bottom: 8px;
}
.gantt-batch-confirm-offset {
  color: #409eff;
  font-size: 14px;
  font-weight: 700;
  margin: 0 2px;
}
.gantt-batch-confirm-list {
  margin-top: 8px;
  max-height: 220px;
  overflow-y: auto;
  border-top: 1px solid #ebeef5;
  padding-top: 8px;
}
.gantt-batch-confirm-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 0;
  font-size: 12px;
}
.gantt-batch-confirm-bullet {
  font-size: 11px;
  flex-shrink: 0;
}
.gantt-batch-confirm-bullet.is-primary {
  color: #409eff;
}
.gantt-batch-confirm-bullet.is-batch {
  color: #e6a23c;
}
.gantt-batch-confirm-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #303133;
}
.gantt-batch-confirm-date {
  font-family: 'SF Mono', Menlo, Consolas, monospace;
  flex-shrink: 0;
  color: #67c23a;
  font-weight: 600;
}
.gantt-batch-confirm-more {
  color: #909399;
  font-size: 11px;
  font-style: italic;
  text-align: center;
  margin-top: 4px;
}
.gantt-milestone {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  border: 1.5px solid #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
  z-index: 2;
  transform: rotate(45deg);
  transition:
    transform 0.1s,
    box-shadow 0.1s,
    outline 0.1s;
  outline: 2px solid transparent;
  outline-offset: 2px;
}
/* 辅拖偏移量徽章:右上角小气泡 */
.gantt-milestone-offset-badge {
  position: absolute;
  top: -10px;
  right: -10px;
  min-width: 22px;
  height: 18px;
  padding: 0 4px;
  border-radius: 9px;
  font-size: 10px;
  font-weight: 700;
  font-family: 'SF Mono', Menlo, Consolas, monospace;
  line-height: 18px;
  text-align: center;
  white-space: nowrap;
  z-index: 12;
  pointer-events: none;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.25);
  transform: rotate(-45deg); /* 抵消菱形的旋转,徽章保持正向 */
}
.gantt-milestone-offset-badge.is-primary {
  background: #409eff;
  color: #fff;
  border: 1.5px solid #fff;
}
.gantt-milestone-offset-badge.is-batch {
  background: #e6a23c;
  color: #fff;
  border: 1.5px solid #fff;
}
.gantt-milestone:hover {
  transform: rotate(45deg) scale(1.25);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3);
  z-index: 4;
}
.gantt-milestone-selected {
  /* 多选:金色 outline */
  outline: 2px solid #e6a23c !important;
  outline-offset: 2px;
}
.gantt-milestone-focused {
  /* 键盘焦点:蓝色 outline */
  outline: 2px solid #409eff !important;
  outline-offset: 2px;
}
.gantt-milestone-dragging {
  /* 主拖:最大最亮,蓝色 dashed outline */
  transform: rotate(45deg) scale(1.4) !important;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.5) !important;
  z-index: 10 !important;
  outline: 2.5px dashed #409eff !important;
  outline-offset: 3px;
}
.gantt-milestone-batch-dragging {
  /* 辅拖(一起被拖的):放大 25%,橙色 dashed outline + 半透明光环 */
  transform: rotate(45deg) scale(1.25) !important;
  box-shadow:
    0 0 0 3px rgba(230, 163, 60, 0.4),
    0 2px 8px rgba(230, 163, 60, 0.4) !important;
  z-index: 8 !important;
  outline: 2px dashed #e6a23c !important;
  outline-offset: 3px;
  /* 微微脉动,暗示"我也被一起拖了" */
  animation: gantt-batch-pulse 0.9s ease-in-out infinite;
}
@keyframes gantt-batch-pulse {
  0%,
  100% {
    box-shadow:
      0 0 0 3px rgba(230, 163, 60, 0.4),
      0 2px 8px rgba(230, 163, 60, 0.4);
  }
  50% {
    box-shadow:
      0 0 0 5px rgba(230, 163, 60, 0.25),
      0 2px 10px rgba(230, 163, 60, 0.5);
  }
}
.gantt-milestone-completed {
  opacity: 0.7;
}
.gantt-drag-ghost {
  position: fixed;
  pointer-events: none;
  background: #409eff;
  color: #fff;
  padding: 6px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  z-index: 9999;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
  white-space: nowrap;
  max-width: 320px;
}
.gantt-drag-ghost-main {
  /* 顶部那行:数量 + 总日期 + dayOffset */
  font-weight: 600;
}
.gantt-drag-ghost-list {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px solid rgba(255, 255, 255, 0.3);
  font-size: 11px;
  font-weight: 400;
  max-height: 200px;
  overflow: hidden;
  white-space: nowrap;
}
.gantt-drag-ghost-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 1px 0;
}
.gantt-drag-ghost-bullet {
  font-size: 10px;
  flex-shrink: 0;
}
.gantt-drag-ghost-bullet.is-primary {
  color: #fff;
}
.gantt-drag-ghost-bullet.is-batch {
  color: rgba(255, 255, 255, 0.7);
}
.gantt-drag-ghost-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
}
.gantt-drag-ghost-date {
  font-family: 'SF Mono', Menlo, Consolas, monospace;
  flex-shrink: 0;
  opacity: 0.95;
}
.gantt-drag-ghost-more {
  color: rgba(255, 255, 255, 0.8);
  font-size: 10px;
  margin-top: 2px;
  text-align: center;
  font-style: italic;
}
.gantt-drag-ghost::before {
  content: '📅 ';
}
.gantt-legend {
  display: flex;
  gap: 16px;
  align-items: center;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #ebeef5;
  font-size: 12px;
  color: #606266;
  flex-wrap: wrap;
}
.gantt-legend i {
  display: inline-block;
  vertical-align: middle;
  margin-right: 4px;
}
.lg-plan {
  width: 20px;
  height: 12px;
  background: rgba(103, 194, 58, 0.18);
  border: 1px dashed #b3e19d;
}
.lg-actual {
  width: 20px;
  height: 12px;
  background: rgba(64, 158, 255, 0.45);
  border: 1px solid #409eff;
}
.lg-progress {
  width: 20px;
  height: 6px;
  background: rgba(103, 194, 58, 0.85);
  border-radius: 2px;
}
.lg-diamond {
  width: 10px;
  height: 10px;
  transform: rotate(45deg);
}
</style>
