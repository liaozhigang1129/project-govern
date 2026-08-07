/**
 * 甘特图拖拽改期工具(纯状态机,不直接发请求)
 *  - 拖动过程中只改 view 提供的 setter(乐观更新),不发请求
 *  - mouseup 时回调 onCommit;失败由 view 决定回滚策略
 *  - 支持四种模式:
 *      'bar-move'        整条项目计划条平移 → 改 planStart + planEnd
 *      'bar-resize-l'    改 planStart
 *      'bar-resize-r'    改 planEnd
 *      'milestone-move'  改 planDate
 */
import { onBeforeUnmount, ref } from 'vue'

export type DragMode = 'bar-move' | 'bar-resize-l' | 'bar-resize-r' | 'milestone-move'

export interface BarCommitPayload {
  planStartDate?: string
  planEndDate?: string
}
export interface MilestoneCommitPayload {
  planDate: string
}

export interface UseGanttDragOptions {
  /** 每天多少像素(view 提供,因 pxPerDay 是 ref) */
  pxPerDay: () => number
  /** ISO 日期加/减 n 天 */
  shiftDate: (iso: string, days: number) => string
  /**
   * 拖拽结束 — view 负责真正 PUT
   * 返回 Promise:成功 resolve,失败 reject(view 自己回滚 + 提示)
   */
  onCommitBar: (projectId: number, payload: BarCommitPayload) => Promise<void>
  onCommitMilestone: (milestoneId: number, payload: MilestoneCommitPayload) => Promise<void>
}

/** view 端调用 startDrag 时传入的"目标"描述 */
export interface BarDragTarget {
  kind: 'bar'
  projectId: number
  origStart: string
  origEnd: string
  /** 拖动期间实时更新 — view 传闭包,内部 set 本地 bar 字段 */
  setStart: (iso: string) => void
  setEnd: (iso: string) => void
  /** mouseup 时从这里读最新值 */
  getCurrent: () => { planStart: string; planEnd: string }
}
export interface MilestoneDragTarget {
  kind: 'milestone'
  milestoneId: number
  origDate: string
  setDate: (iso: string) => void
  getCurrent: () => string
}
export type DragTarget = BarDragTarget | MilestoneDragTarget

/** 内部状态机 */
interface ActiveDrag {
  mode: DragMode
  startX: number
  target: DragTarget
}

export function useGanttDrag(opts: UseGanttDragOptions) {
  const active = ref<ActiveDrag | null>(null)
  const saving = ref(false)
  /** 拖过 → 阻止 click 跳转项目详情 */
  const wasDragged = ref(false)

  function onMouseMove(e: MouseEvent) {
    if (!active.value) return
    e.preventDefault()
    const dx = e.clientX - active.value.startX
    const days = Math.round(dx / opts.pxPerDay())
    if (days === 0) return

    const a = active.value
    if (a.target.kind === 'bar') {
      if (a.mode === 'bar-move') {
        a.target.setStart(opts.shiftDate(a.target.origStart, days))
        a.target.setEnd(opts.shiftDate(a.target.origEnd, days))
      } else if (a.mode === 'bar-resize-l') {
        const newStart = opts.shiftDate(a.target.origStart, days)
        if (newStart <= a.target.origEnd) a.target.setStart(newStart)
      } else if (a.mode === 'bar-resize-r') {
        const newEnd = opts.shiftDate(a.target.origEnd, days)
        if (newEnd >= a.target.origStart) a.target.setEnd(newEnd)
      }
    } else {
      // milestone-move
      a.target.setDate(opts.shiftDate(a.target.origDate, days))
    }
  }

  async function onMouseUp() {
    if (!active.value) return
    const a = active.value
    cleanup()

    if (a.target.kind === 'bar') {
      const cur = a.target.getCurrent()
      // 没动过(原值 === 当前值)不提交
      if (cur.planStart === a.target.origStart && cur.planEnd === a.target.origEnd) {
        wasDragged.value = false
        return
      }
      saving.value = true
      try {
        await opts.onCommitBar(a.target.projectId, {
          planStartDate: cur.planStart,
          planEndDate: cur.planEnd,
        })
      } finally {
        saving.value = false
      }
    } else {
      const cur = a.target.getCurrent()
      if (cur === a.target.origDate) {
        wasDragged.value = false
        return
      }
      saving.value = true
      try {
        await opts.onCommitMilestone(a.target.milestoneId, { planDate: cur })
      } finally {
        saving.value = false
      }
    }
  }

  function cleanup() {
    active.value = null
    window.removeEventListener('mousemove', onMouseMove)
    window.removeEventListener('mouseup', onMouseUp)
  }

  /** view 端调用 — 由 mousedown 触发 */
  function startDrag(mode: DragMode, target: DragTarget, startX: number) {
    active.value = { mode, startX, target }
    wasDragged.value = true
    window.addEventListener('mousemove', onMouseMove)
    window.addEventListener('mouseup', onMouseUp)
  }

  onBeforeUnmount(cleanup)

  return {
    active,
    saving,
    wasDragged,
    startDrag,
    cleanup,
  }
}
