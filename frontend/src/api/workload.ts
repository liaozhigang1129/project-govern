/**
 * Workload API (P2.B)
 */
import api from './client'

export interface UserWeekRow {
  userId: number
  username: string
  fullName: string
  departmentId: number
  departmentName: string
  weekStart: string
  weekEnd: string
  status: 'NO_DATA' | 'DRAFT' | 'SUBMITTED' | 'APPROVED'
  totalHours: number
  projectCount: number
  /** P2.5: 该人该周窗口内所参与项目下的里程碑总数 */
  milestoneCount: number
  /** P2.5: 其中 14 天内到期的数量 */
  upcomingCount: number
}

export interface UserLoadMatrix {
  from: string
  to: string
  weekCount: number
  rows: UserWeekRow[]
}

export interface ProjectMemberHours {
  userId: number
  username: string
  totalHours: number
  dayCount: number
}

export interface ProjectDayHours {
  workDate: string
  totalHours: number
  memberCount: number
}

export interface ProjectLoad {
  projectId: number
  projectName: string
  from: string
  to: string
  totalHours: number
  memberCount: number
  dayCount: number
  byMember: ProjectMemberHours[]
  byDay: ProjectDayHours[]
}

async function call<T>(p: Promise<unknown>): Promise<T> {
  return p as Promise<T>
}

export const workloadApi = {
  userMatrix: (params: { departmentId?: number; userId?: number; from?: string; to?: string } = {}) =>
    call<UserLoadMatrix>(api.get('/workload/users', { params })),
  projectLoad: (projectId: number, params: { from?: string; to?: string } = {}) =>
    call<ProjectLoad>(api.get(`/workload/projects/${projectId}`, { params })),
  /** 甘特图(自动 / 手动范围) */
  gantt: (
    params: {
      from?: string
      to?: string
      departmentId?: number
      pmUserId?: number
      includeCompleted?: boolean
    } = {},
  ) => call<import('@/components/GanttView.vue').GanttResponse>(api.get('/gantt', { params })),
  /**
   * 拖拽甘特图上整条项目计划条后,提交新 planStart / planEnd
   *  - 后端: PUT /api/projects/{id} (ProjectUpdateRequest)
   *  - 仅传需要改的字段,其它保持不变
   */
  updateGanttBar: (projectId: number, payload: { planStartDate?: string; planEndDate?: string }) =>
    call<{ id: number }>(api.put(`/projects/${projectId}`, payload)),
  /**
   * 拖拽甘特图上单个里程碑后,提交新 planDate
   *  - 后端: PATCH /api/milestones/{id}/plan-date
   *  - 注:当前 Spring 后端 PATCH 路径会被当成静态资源处理(500),
   *      实测 PUT /api/milestones/{id} {planDate:...} 是同效 fallback。
   *      这里先放回 PATCH(更符合语义);若遇到 500 调用方自动 fallback 到 PUT。
   */
  updateGanttMilestone: (milestoneId: number, payload: { planDate: string }) =>
    call<{ id: number }>(api.patch(`/milestones/${milestoneId}/plan-date`, payload)),
}

/** P2.5: 单元格点击下钻 — 单人单周里程碑列表 */
export interface UserMilestoneRow {
  milestoneId: number
  milestoneName: string
  projectId: number
  projectCode: string
  projectName: string
  phaseId: number
  phaseName: string
  statusCode: string
  statusName: string
  planDate: string
  weight: number
}

export interface UserMilestoneList {
  userId: number
  fullName: string
  weekStart: string
  total: number
  items: UserMilestoneRow[]
}

/** 单元格下钻 API */
export async function fetchUserMilestones(userId: number, weekStart: string): Promise<UserMilestoneList> {
  return api.get<UserMilestoneList>(`/workload/users/${userId}/milestones`, {
    params: { weekStart },
  })
}
