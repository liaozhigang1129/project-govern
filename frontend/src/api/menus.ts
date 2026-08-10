/**
 * 菜单管理 API (L1-3)
 * 与后端 SysMenuAdminController 严格对齐
 */
import api from './client'

// ============================================================
// 类型
// ============================================================
export interface SysMenuItem {
  id: number
  code: string
  name: string
  parentId: number | null
  parentName: string | null
  path: string | null
  icon: string | null
  sortOrder: number
  menuType: 'DIR' | 'PAGE'
  enabled: boolean
  builtin: boolean
  description: string | null
  createdAt: string
}

export interface SysMenuCreateBody {
  code: string // 大写字母/数字/下划线, 以字母开头
  name: string
  parentId?: number | null // NULL = 顶层
  path?: string
  icon?: string
  sortOrder?: number
  menuType?: 'DIR' | 'PAGE'
  enabled?: boolean
  description?: string
}

export interface SysMenuUpdateBody {
  name: string
  parentId?: number | null
  path?: string
  icon?: string
  sortOrder?: number
  menuType?: 'DIR' | 'PAGE'
  enabled?: boolean
  description?: string
}

// ============================================================
// API
// ============================================================
export const menuApi = {
  /** 1. 列表 (?includeDisabled=true 包含已停用) */
  list: (includeDisabled = false) => api.get<SysMenuItem[]>('/menus', { params: { includeDisabled } }),

  /** 2. 父菜单下拉 (排除自身) */
  parentOptions: (excludeId?: number) =>
    api.get<SysMenuItem[]>('/menus/parent-options', {
      params: excludeId ? { excludeId } : {},
    }),

  /** 3. 详情 */
  get: (id: number) => api.get<SysMenuItem>(`/menus/${id}`),

  /** 4. 新建 (PMO_ADMIN / ADMIN) */
  create: (body: SysMenuCreateBody) => api.post<SysMenuItem>('/menus', body),

  /** 5. 更新 (PMO_ADMIN / ADMIN) */
  update: (id: number, body: SysMenuUpdateBody) => api.put<SysMenuItem>(`/menus/${id}`, body),

  /** 6. 启停 */
  setEnabled: (id: number, enabled: boolean) => api.patch<SysMenuItem>(`/menus/${id}/enabled`, { enabled }),

  /** 7. 删除 */
  delete: (id: number) => api.delete<void>(`/menus/${id}`),
}
