/**
 * 钉钉请休假同步 API
 * - 对应后端 DingTalkLeaveAdminController
 * - /admin/dingtalk/leave/**
 */
import api from './client'

export interface DingTalkLeave {
  id: number
  leaveId: string
  userid: string
  userName: string | null
  departmentId: number | null
  pmoUserId: number | null
  leaveType: string | null
  startTime: string
  endTime: string
  duration: number | null
  durationUnit: string | null
  reason: string | null
  status: string | null
  approverUserid: string | null
  dingtalkUpdatedAt: string | null
  syncedAt: string | null
  deleted: boolean
  createdAt: string
  updatedAt: string
}

export interface DingTalkLeaveSyncState {
  id: number
  syncKey: string
  lastSyncTime: string | null
  lastTotal: number
  lastCreated: number
  lastUpdated: number
  lastDeleted: number
  updatedAt: string | null
}

export interface DingTalkLeaveSyncLog {
  id: number
  startedAt: string
  finishedAt: string | null
  triggerType: string
  triggeredBy: string | null
  status: 'RUNNING' | 'SUCCESS' | 'FAILED' | string
  syncMode: 'INCREMENTAL' | 'FULL' | string
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

export interface LeaveStats {
  total: number
  thisMonth: number
}

export const dingtalkLeaveApi = {
  /** 1. 触发同步 (异步, 立即返回 RUNNING 日志) */
  triggerSync: (operator = 'admin', fullSync = false) =>
    api.post<DingTalkLeaveSyncLog>('/admin/dingtalk/leave/sync/trigger', null, {
      params: { operator, fullSync },
    }),

  /** 2. 同步状态 (上次同步时间 + 累计数) */
  getState: () =>
    api.get<DingTalkLeaveSyncState>('/admin/dingtalk/leave/sync/state'),

  /** 3. 同步日志 */
  listLogs: (page = 0, size = 20) =>
    api.get<PageResult<DingTalkLeaveSyncLog>>('/admin/dingtalk/leave/sync/logs', {
      params: { page, size },
    }),

  /** 4. 统计 (总数 + 本月数) */
  getStats: () =>
    api.get<{ code: number; message: string; data: LeaveStats }>('/admin/dingtalk/leave/stats'),

  /** 5. 请休假列表 (分页) */
  list: (page = 0, size = 20) =>
    api.get<PageResult<DingTalkLeave>>('/admin/dingtalk/leave', {
      params: { page, size },
    }),

  /** 6. 详情 */
  get: (id: number) =>
    api.get<DingTalkLeave>(`/admin/dingtalk/leave/${id}`),
}