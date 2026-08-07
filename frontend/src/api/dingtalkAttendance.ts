/**
 * 钉钉考勤(每日打卡)同步 API
 * - 对应后端 DingTalkAttendanceAdminController
 * - /admin/dingtalk/attendance/**
 * - V4.33 改造: 一行 = 一个 user-day (上班 + 下班 + 异常 + 项目)
 */
import api from './client'

/**
 * V4.33 每天聚合行 (新表 dingtalk_attendance_daily)
 * - 一行 = 一个 (userid, workDate)
 * - 上班/下班 各一列
 * - checkCount 当天打卡次数
 * - isAbnormal + abnormalTypes
 * - rawRecordIds: 详情抽屉 key, 调 /raw?ids= 反查老表
 */
export interface DingTalkAttendanceDaily {
  id: number
  userid: string
  userName: string | null
  departmentId: number | null
  pmoUserId: number | null
  workDate: string              // YYYY-MM-DD
  workDuration: number | null   // V4.34: 分钟, onDutyActual -> offDutyActual 差值

  // 上班
  onDutyPlan: string | null
  onDutyActual: string | null
  onDutyResult: string          // Normal / Late / Early / SeriousLate / NotSigned
  onDutySource: string          // MAP / ATM / WIFI / OTHER
  onDutyLocation: string
  onDutyLocationMethod: string
  onDutyLocationResult: string

  // 下班
  offDutyPlan: string | null
  offDutyActual: string | null
  offDutyResult: string
  offDutySource: string
  offDutyLocation: string
  offDutyLocationMethod: string
  offDutyLocationResult: string

  // 聚合
  checkCount: number
  isMakeup: boolean
  isAbnormal: boolean
  abnormalTypes: string         // "Late;Early" 多个用分号

  // 项目 (来自 timesheet_entry JOIN)
  projectIds: string            // "1,3,7"
  projectNames: string          // "A,B,C"

  // 详情抽屉 key
  rawRecordIds: string          // JSON 数组字符串: ["biz1","biz2"]
  dingtalkUpdatedAt: string | null
  syncedAt: string | null
  deleted: boolean
  createdAt: string
  updatedAt: string
}

/**
 * V4.33 老表原始打卡 (dingtalk_attendance 冻结只读, 仅给详情抽屉用)
 */
export interface DingTalkAttendanceRaw {
  id: number
  recordId: string
  userid: string
  userName: string | null
  departmentId: number | null
  pmoUserId: number | null
  workDate: string
  checkType: string             // OnDuty / OffDuty
  source: string
  timeResult: string
  locationMethod: string
  locationResult: string
  planTime: string | null
  actualTime: string | null
  baseCheckTime: string | null
  dingtalkUpdatedAt: string | null
  syncedAt: string | null
  deleted: boolean
  createdAt: string
  updatedAt: string
}

export interface DingTalkAttendanceSyncState {
  id: number
  syncKey: string
  lastSyncTime: string | null
  lastTotal: number
  lastCreated: number
  lastUpdated: number
  lastDeleted: number
  updatedAt: string | null
}

export interface DingTalkAttendanceSyncLog {
  id: number
  startedAt: string
  finishedAt: string | null
  triggerType: 'MANUAL' | 'SCHEDULED' | string
  triggeredBy: string | null
  status: 'RUNNING' | 'SUCCESS' | 'FAILED' | string
  syncMode: 'INCREMENTAL' | 'FULL' | string
  rangeFrom: string | null
  rangeTo: string | null
  lastSyncTime: string | null
  fetched: number
  createdCount: number
  updatedCount: number
  deletedCount: number
  skippedCount: number
  errorMessage: string | null
  errorDetail: string | null
}

export interface PageResult<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

/**
 * V4.33 stats: 加异常数 + 异常率
 */
export interface AttendanceStats {
  total: number
  thisMonth: number
  abnormalThisMonth: number
  abnormalRate: number          // 0-100 整数百分比
}

export const dingtalkAttendanceApi = {
  /** 1. 触发同步 (异步, 立即返回 RUNNING 日志) */
  triggerSync: (operator = 'admin', from?: string, to?: string) =>
    api.post<DingTalkAttendanceSyncLog>('/admin/dingtalk/attendance/sync/trigger', null, {
      params: { operator, from, to },
    }),

  /** 2. 同步状态 */
  getState: () =>
    api.get<DingTalkAttendanceSyncState>('/admin/dingtalk/attendance/sync/state'),

  /** 3. 同步日志 */
  listLogs: (page = 0, size = 20) =>
    api.get<PageResult<DingTalkAttendanceSyncLog>>('/admin/dingtalk/attendance/sync/logs', {
      params: { page, size },
    }),

  /** 4. 统计 (V4.33 加 abnormalThisMonth/abnormalRate) */
  getStats: () =>
    api.get<{ code: number; message: string; data: AttendanceStats }>('/admin/dingtalk/attendance/stats'),

  /** 5. 考勤列表 (V4.33: 一行 = 一个 user-day, V4.33+ 加筛选) */
  list: (page = 0, size = 20, filters?: { dateFrom?: string; dateTo?: string; useridKeyword?: string; isAbnormal?: boolean }) =>
    api.get<PageResult<DingTalkAttendanceDaily>>('/admin/dingtalk/attendance', {
      params: { page, size, ...(filters || {}) },
    }),

  /** 6. V4.33 详情抽屉: 拿某天聚合行的原始打卡 (走老表冻结只读) */
  fetchRawByRecordIds: (ids: string[]) => {
    if (!ids || ids.length === 0) {
      return Promise.resolve({ code: 0, message: 'ok', data: [] as DingTalkAttendanceRaw[], timestamp: Date.now() })
    }
    return api.get<{ code: number; message: string; data: DingTalkAttendanceRaw[]; timestamp: number }>(
      '/admin/dingtalk/attendance/raw',
      { params: { ids: ids.join(',') } }
    )
  },
}
