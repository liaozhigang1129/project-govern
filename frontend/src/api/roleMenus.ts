/**
 * 角色 × 菜单 授权 API (L1-3)
 */
import api from './client'

export interface RoleMenuAssignBody {
  roleId: number
  menuIds: number[]
}

export const roleMenuApi = {
  /** 查询某角色已授权菜单 ID */
  listByRole: (roleId: number) => api.get<number[]>(`/role-menus/${roleId}`),

  /** 全量替换授权 */
  assign: (roleId: number, body: RoleMenuAssignBody) => api.put<number[]>(`/role-menus/${roleId}`, body),

  /** 当前登录用户可见的菜单 code 列表 */
  myVisibleMenuCodes: (roleIds: number[]) =>
    api.get<string[]>('/role-menus/mine', {
      params: { roleIds: roleIds.join(',') },
    }),
}
