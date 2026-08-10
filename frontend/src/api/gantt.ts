/**
 * 里程碑 / 甘特图 API
 *
 * 端点参考 backend MilestoneController + GanttController
 */
import api from './client'

/* ============================================================
 * 里程碑
 * ============================================================ */

export type MilestoneStatusCode = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'DELAYED'

export interface MilestoneStatusRef {
  id: number
  code: MilestoneStatusCode
  name: string
  terminal?: boolean
}

export interface Milestone {
  id: number
  projectId: number
  name: string
  sequence: number
  planDate: string // YYYY-MM-DD
  actualDate: string | null
  status: MilestoneStatusRef | null
  weight: number
  ownerUserId: number | null
  deliverable: string | null
  remark: string | null
  completedAt: string | null
  createdAt: string
  updatedAt: string
}

export const milestoneApi = {
  /** 某项目里程碑列表(JOIN FETCH status) */
  list: (projectId: number) => api.get<Milestone[]>(`/milestones/by-project/${projectId}`),

  /** 详情(同 list,但只取单条) */
  get: (id: number) =>
    api
      .get<Milestone>(`/milestones/by-project/0`)
      .then(() => null)
      .catch(() => null),
  // 上面这个 get 是占位,实际后端没有 /milestones/{id} 单独接口,前端用 list + filter

  /** 改期专用(单一职责)— 拖拽打这里 */
  patchPlanDate: (id: number, planDate: string) =>
    api.patch<Milestone>(`/milestones/${id}/plan-date`, { planDate }),

  /** 改状态(用于抽屉里"标记为已完成"等) */
  putStatus: (id: number, status: MilestoneStatusCode, actualDate?: string) =>
    api.put<Milestone>(`/milestones/${id}/status`, {
      status,
      actualDate: actualDate ?? null,
    }),

  /** 局部更新(name / weight / owner / deliverable / remark) */
  update: (
    id: number,
    body: Partial<{
      name: string
      planDate: string
      weight: number
      ownerUserId: number
      deliverable: string
      remark: string
    }>,
  ) => api.put<Milestone>(`/milestones/${id}`, body),
}

/* ============================================================
 * 部门
 * ============================================================ */

export interface Department {
  id: number
  name: string
  code: string
  parentId: number | null
  sortOrder: number
  enabled: boolean
}

export const departmentApi = {
  list: () => api.get<Department[]>('/departments'),
}

/* ============================================================
 * 用户(供 owner 下拉)
 * ============================================================ */

export const userApi = {
  /** 对齐后端 /users 接口(可加 ?enabled=true 过滤) */
  list: (params: { enabled?: boolean; departmentId?: number } = {}) =>
    api.get<import('./client').AppUser[]>('/users', { params }),
}
