/**
 * 用户管理 API (L1-1)
 * 11 个端点, 与后端 UserAdminController 严格对齐
 */
import api from './client'

// ============================================================
// 类型 — 与 UserListItem / UserDetailVO 对齐
// ============================================================
export interface RoleRef {
  id: number
  code: string
  name: string
}

export interface DepartmentRef {
  id: number
  name: string
  fullPath?: string  // V4.15: 全路径 "总公司 / 一级 / 二级"
}

export interface UserListItem {
  id: number
  username: string
  fullName: string
  email: string
  phone: string | null      // 后端已脱敏成 138****8000 形式
  jobTitle: string | null
  enabled: boolean
  locked: boolean           // 后端 isLocked() 派生
  loginFailCount: number
  // V4.17: 后端 UserListItem 返 code/name 字符串 + 数组, 不是嵌套对象
  primaryRoleCode?: string | null
  primaryRoleName?: string | null
  roleCodes?: string[]      // 已分配角色代码列表 (含主角色)
  departmentId?: number | null
  departmentName?: string | null
  departmentPath?: string | null
  department: DepartmentRef | null
  lastLoginAt: string | null
  lastLoginIp: string | null
  mustChangePassword: boolean
  createdAt: string
}

export interface UserDetailVO extends UserListItem {
  backupUserId: number | null
  backupUserName?: string
  passwordChangedAt: string | null
  lockedUntil: string | null
}

export interface UserOption {
  id: number
  fullName: string
  username: string
  primaryRoleCode: string
  departmentId: number | null
  enabled: boolean
}

export interface UserQuery {
  keyword?: string          // 模糊匹配 username/fullName/phone
  roleCode?: string         // 主角色
  departmentId?: number
  enabled?: boolean
  locked?: boolean
  page?: number             // 0-based
  size?: number             // 默认 20
  sort?: string             // 默认 createdAt,desc
}

export interface PageResult<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number            // 当前页
  size: number
}

// ============================================================
// 请求体
// ============================================================
export interface UserCreateBody {
  username: string
  password: string
  fullName: string
  email: string
  phone?: string
  primaryRoleCode: string
  extraRoleCodes?: string[]
  departmentId?: number
  jobTitle?: string
  enabled?: boolean
  backupUserId?: number
  mustChangePassword?: boolean
}

export interface UserUpdateBody {
  fullName?: string
  email?: string
  phone?: string
  primaryRoleCode?: string
  extraRoleCodes?: string[]
  departmentId?: number
  jobTitle?: string
  enabled?: boolean
  backupUserId?: number
}

// ============================================================
// API
// ============================================================
export const userApi = {
  /** 1. 列表 (分页) */
  list: (q: UserQuery = {}) => {
    const params: Record<string, string> = {}
    Object.entries(q).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') params[k] = String(v)
    })
    return api.get<PageResult<UserListItem>>('/users', { params })
  },

  /** 2. 详情 */
  get: (id: number) =>
    api.get<UserDetailVO>(`/users/${id}`),

  /** 3. 新建 */
  create: (body: UserCreateBody) =>
    api.post<UserListItem>('/users', body),

  /** 4. 更新 */
  update: (id: number, body: UserUpdateBody) =>
    api.put<UserListItem>(`/users/${id}`, body),

  /** 5. 启停 */
  setEnabled: (id: number, enabled: boolean) =>
    api.patch<UserListItem>(`/users/${id}/enabled`, { enabled }),

  /** 6. 解锁 */
  unlock: (id: number) =>
    api.post<UserListItem>(`/users/${id}/unlock`),

  /** 7. 管理员重置密码 */
  resetPassword: (id: number, newPassword: string, opts?: { mustChangeOnNextLogin?: boolean; notifyByEmail?: boolean }) =>
    api.post(`/users/${id}/password:reset`, { newPassword, ...opts }),

  /** 8. 离职 */
  offboard: (id: number, transferTo: number | null, reason: string, offboardDate?: string) =>
    api.post(`/users/${id}/offboard`, { transferTo, reason, offboardDate }),

  /** 9. 复职 */
  reinstate: (id: number) =>
    api.post<UserListItem>(`/users/${id}/reinstate`),

  /** 10. 简表 (下拉用) */
  options: () =>
    api.get<UserOption[]>('/users/options'),

  /** 11. 自己改密码 (需要当前密码) */
  changeOwnPassword: (oldPassword: string, newPassword: string) =>
    api.post('/users/me/password:change', { oldPassword, newPassword }),

  // ============================================================
  // V4.13 用户-角色分配
  // ============================================================
  /** 查询某用户已分配的角色(含主角色标识) */
  getRoles: (userId: number) =>
    api.get<{
      userId: number
      primaryRoleId: number | null
      primaryRoleCode: string | null
      roles: Array<{ id: number; code: string; name: string; enabled: boolean; builtin: boolean; primary: boolean }>
    }>(`/users/${userId}/roles`),

  /** 全量替换某用户的角色分配 (roleIds 必填, 主角色 = 列表第一个) */
  assignRoles: (userId: number, roleIds: number[]) =>
    api.put<{ userId: number; primaryRoleId: number; primaryRoleCode: string; roleIds: number[] }>(
      `/users/${userId}/roles`,
      { userId, roleIds },
    ),

  /** V4.16: 批量给多个用户应用同一组角色 */
  batchAssignRoles: (userIds: number[], roleIds: number[], mode: 'REPLACE' | 'ADD' | 'REMOVE' = 'REPLACE') =>
    api.post<{
      mode: string
      roleIds: number[]
      totalRequested: number
      success: number
      failed: number
      errors: string[]
    }>('/users/batch/roles', { userIds, roleIds, mode }),

  /** 拿可分配的角色列表 (供下拉用) */
  listAssignableRoles: () =>
    api.get<Array<{ id: number; code: string; name: string; builtin: boolean }>>('/role-menus/assignable'),

  // ============================================================
  // V4.14 按部门树筛选
  // ============================================================
  /** 按部门 ID 列表筛选 (用于"按组织"展示, includeSubDepts=true 时同时含子部门) */
  listByDepartments: (departmentIds: number[], opts: { keyword?: string; page?: number; size?: number; sort?: string } = {}) =>
    api.post<PageResult<UserListItem>>('/users/by-departments', { departmentIds, ...opts }),
}
