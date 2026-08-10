/**
 * 部门管理 API (L1-3)
 * 7 端点, 与后端 DepartmentAdminController 严格对齐
 */
import api from './client'

// ============================================================
// 类型
// ============================================================
export interface DepartmentNode {
  id: number
  code: string
  name: string
  parentId: number | null
  parentName: string | null
  sortOrder: number
  enabled: boolean
  /** 直属用户数 (不含子部门) */
  memberCount: number
  /** V4.18: 含子部门的总人数 (自身 + 所有后代) */
  memberCountTotal?: number
  children: DepartmentNode[]
  // V4.14 字段
  dingtalkDeptId?: string | null
  dingtalkParentId?: string | null
  treePath?: string | null
  treeLevel?: number
  /** 是否未挂到 Java 树 (parent_id = NULL 但 treePath 不为空) */
  orphaned?: boolean
}

export interface DepartmentOption {
  id: number
  code: string
  name: string
  parentId: number | null
  enabled: boolean
}

export interface DepartmentCreateBody {
  code: string // 字母/数字/下划线, 2-32 字符
  name: string
  parentId?: number | null // null = 根
  sortOrder?: number
  enabled?: boolean
}

export interface DepartmentUpdateBody {
  name: string
  parentId?: number | null
  sortOrder?: number
  enabled?: boolean
}

// ============================================================
// API
// ============================================================
export const departmentApi = {
  /** 1. 树状全量 (前端表格用) */
  tree: () => api.get<DepartmentNode[]>('/departments/tree'),

  /** 2. 简表 (下拉用, 仅启用) */
  options: () => api.get<DepartmentOption[]>('/departments/options'),

  /** 3. 详情 */
  get: (id: number) => api.get<DepartmentNode>(`/departments/${id}`),

  /** 4. 新建 (PMO_ADMIN/ADMIN) */
  create: (body: DepartmentCreateBody) => api.post<DepartmentNode>('/departments', body),

  /** 5. 更新 (PMO_ADMIN/ADMIN) */
  update: (id: number, body: DepartmentUpdateBody) => api.put<DepartmentNode>(`/departments/${id}`, body),

  /** 6. 启停 */
  setEnabled: (id: number, enabled: boolean) =>
    api.patch<DepartmentNode>(`/departments/${id}/enabled`, { enabled }),

  /** 7. 删除 (软删) */
  delete: (id: number) => api.delete<void>(`/departments/${id}`),

  // ============================================================
  // V4.14 用户-部门分配
  // ============================================================
  /** 8. 单个用户分配部门 (拖拽/手动用) */
  assignUser: (userId: number, departmentId: number | null) =>
    api.put<{ userId: number; departmentId: number; departmentName: string; updatedAt: string }>(
      `/departments/users/${userId}/department`,
      null,
      { params: { departmentId } },
    ),

  /** 9. 批量分配部门 */
  bulkAssignUsers: (userIds: number[], departmentId: number) =>
    api.post<{ updated: number; departmentId: number; departmentName: string; updatedAt: string }>(
      `/departments/users/bulk-assign`,
      { userIds, departmentId },
    ),

  /** 10. 子部门 ID 列表 (含自身, 给前端筛选) */
  descendantIds: (deptId: number) => api.get<number[]>(`/departments/${deptId}/descendants`),

  /** 5.5 缺失部门的用户列表 (admin 限定) */
  missingUsers: (page: number, size: number) =>
    api.get<PageResult<UserLite>>(`/departments/users/missing`, {
      params: { page, size },
    }),

  /** 11. 未分配部门的用户列表 */
  usersWithoutDepartment: (params: { keyword?: string; page?: number; size?: number } = {}) => {
    const q: Record<string, string> = {}
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') q[k] = String(v)
    })
    return api.get<PageResult<UserLite>>('/departments/users/missing', { params: q })
  },
}

// ============================================================
// 补充类型 (V4.14)
// ============================================================
export interface PageResult<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface UserLite {
  id: number
  username: string
  fullName: string
  email: string
  enabled: boolean
  primaryRole?: { id: number; code: string; name: string } | null
  dingtalkUserId?: string | null
}
