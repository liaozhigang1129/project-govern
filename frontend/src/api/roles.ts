/**
 * 角色管理 API (L1-2)
 * 7 端点, 与后端 RoleAdminController 严格对齐
 */
import api from './client'

// ============================================================
// 类型
// ============================================================
export interface RoleListItem {
  id: number
  code: string
  name: string
  description: string | null
  builtIn: boolean           // 内置角色 (不可删/不可改 code)
  enabled: boolean
  sortOrder: number
  primaryUserCount: number   // 该角色下"主角色"是它的启用用户数
  createdAt: string
}

export interface RoleOption {
  id: number
  code: string
  name: string
  builtIn: boolean
}

export interface RoleCreateBody {
  code: string               // 大写字母/数字/下划线, 2-32 字符
  name: string
  description?: string
  enabled?: boolean
  sortOrder?: number
}

export interface RoleUpdateBody {
  name: string
  description?: string
  enabled?: boolean
  sortOrder?: number
}

// ============================================================
// API
// ============================================================
export const roleApi = {
  /** 1. 列表 (?includeDisabled=true 包含已停用) */
  list: (includeDisabled = false) =>
    api.get<RoleListItem[]>('/roles', { params: { includeDisabled } }),

  /** 2. 简表 (下拉用, 仅启用) */
  options: () =>
    api.get<RoleOption[]>('/roles/options'),

  /** 3. 详情 */
  get: (id: number) =>
    api.get<RoleListItem>(`/roles/${id}`),

  /** 4. 新建 (PMO_ADMIN / ADMIN) */
  create: (body: RoleCreateBody) =>
    api.post<RoleListItem>('/roles', body),

  /** 5. 更新 (PMO_ADMIN / ADMIN) */
  update: (id: number, body: RoleUpdateBody) =>
    api.put<RoleListItem>(`/roles/${id}`, body),

  /** 6. 启停 */
  setEnabled: (id: number, enabled: boolean) =>
    api.patch<RoleListItem>(`/roles/${id}/enabled`, { enabled }),

  /** 7. 删除 */
  delete: (id: number) =>
    api.delete<void>(`/roles/${id}`),
}
