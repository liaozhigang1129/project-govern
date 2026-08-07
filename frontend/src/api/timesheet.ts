/**
 * Timesheet API (P2.A)
 *
 * 一周一行(weekStart = 周一),包含若干 entry(每行一天/一个项目/一个里程碑)
 *
 * 由于 client.ts 的 response interceptor 会拆掉 ApiResponse.data,
 * 这里直接返回 T,调用方不需要 cast。
 */
import api from './client'

export interface Entry {
  id?: number
  workDate: string            // YYYY-MM-DD
  projectId: number
  milestoneId?: number
  hours: number
  description?: string
}

export interface TimesheetSummary {
  id: number
  userId: number
  userName: string
  weekStart: string
  weekEnd: string
  status: 'DRAFT' | 'SUBMITTED' | 'APPROVED'
  totalHours: number
  projectCount: number
  entryCount: number
  submitterNote?: string
  submittedAt?: string
  approverName?: string
  approvedAt?: string
  createdAt: string
  updatedAt: string
}

export interface TimesheetDetail extends TimesheetSummary {
  entries: Entry[]
}

/**
 * P3 修复:后端 Spring `Page<T>` 序列化后的形状是
 *   { content: T[], totalElements, totalPages, number, size, ... }
 * 老代码用 { rows, total } 一直拿不到数据,改成真实形状。
 * 历史/审批页的 .rows → .content, .total → .totalElements
 */
export interface PageResult<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

// 自封装,绕开 axios 的 AxiosResponse<T> 推断:
// interceptor 已经把 .data 拆出来了,TS 还是推断为 AxiosResponse<T> 是个 bug
// 解决:内部 cast `as T`,外面就拿干净 T
async function call<T>(p: Promise<unknown>): Promise<T> {
  return p as Promise<T>
}

export const timesheetApi = {
  list: (params: {
    userId?: number
    status?: string
    from?: string
    to?: string
    page?: number
    size?: number
  } = {}) => call<PageResult<TimesheetSummary>>(
    api.get('/timesheets', { params })
  ),
  get: (id: number) => call<TimesheetDetail>(
    api.get(`/timesheets/${id}`)
  ),
  create: (userId: number, weekStart: string) => call<TimesheetDetail>(
    api.post('/timesheets', { userId, weekStart })
  ),
  upsertEntries: (id: number, entries: Entry[]) => call<TimesheetDetail>(
    api.put(`/timesheets/${id}/entries`, { entries })
  ),
  submit: (id: number, note?: string) => call<TimesheetDetail>(
    api.post(`/timesheets/${id}/submit`, { note: note ?? '' })
  ),
  approve: (id: number) => call<TimesheetDetail>(
    api.post(`/timesheets/${id}/approve`, {})
  ),
  reject: (id: number, comment: string) => call<TimesheetDetail>(
    api.post(`/timesheets/${id}/reject`, { comment })
  ),
  batchApprove: (ids: number[]) => call<TimesheetDetail[]>(
    api.post(`/timesheets/batch-approve`, { ids })
  ),
  remove: (id: number) => call<void>(api.delete(`/timesheets/${id}`)),

  // ============================================================
  //  V4.34 工时自动填报
  // ============================================================

  /**
   * 单用户单周自动填报
   * @param body.userId     必填 (普通用户会被后端覆盖为当前用户)
   * @param body.weekStart  必填 周一 YYYY-MM-DD
   * @param body.dryRun     可选 true=只返回结果不写库
   * @param body.overwrite  可选 true=覆盖已存在 entry
   */
  autoFill: (body: { userId: number; weekStart: string; dryRun?: boolean; overwrite?: boolean }) =>
    call<AutoFillResult>(api.post('/timesheets/_auto-fill', body)),

  /**
   * 批量自动填报 (PMO_ADMIN/EXEC 限)
   * @param body.weekStart  必填
   * @param body.userIds    可选 null/空=全员
   * @param body.dryRun     可选
   * @param body.overwrite  可选
   */
  autoFillBatch: (body: { weekStart: string; userIds?: number[]; dryRun?: boolean; overwrite?: boolean }) =>
    call<BatchAutoFillResult>(api.post('/timesheets/_auto-fill-batch', body))
}

export interface DayFillResult {
  workDate: string
  workDurationMinutes: number | null
  leaveHours: number
  matchReason: 'PM' | 'BU' | 'PL' | 'DEPT_GROUP' | 'WBS' | 'PLACEHOLDER'
  projectId: number | null
  milestoneId: number | null
  priority: number
  hours: number
  description: string
  skipped: boolean
}

export interface AutoFillResult {
  userId: number
  userName: string
  weekStart: string
  weekEnd: string
  dryRun: boolean
  overwrite: boolean
  totalDays: number
  filledDays: number
  skippedDays: number
  placeholderDays: number
  totalHours: number
  days: DayFillResult[]
  summary: string
}

export interface BatchAutoFillResult {
  weekStart: string
  requested: number
  successCount: number
  skippedCount: number
  errorCount: number
  results: AutoFillResult[]
}