/**
 * 系统参数 API (P1-4)
 * 7 端点, 与后端 SystemConfigAdminController 严格对齐
 */
import api from './client'

// ============================================================
// 类型
// ============================================================

export type SystemConfigValueType = 'STRING' | 'NUMBER' | 'BOOLEAN' | 'JSON' | 'ENUM'

export interface SystemConfigItem {
  id: number
  configKey: string
  configValue: string | null
  defaultValue: string | null
  valueType: SystemConfigValueType
  options: string | null // ENUM 类型的可选项 (逗号分隔)
  configGroup: string
  description: string
  sortOrder: number
  isDefault: boolean
  updatedAt: string
  updatedBy: string | null
}

export interface ConfigUpdateBody {
  configValue: string
}

export interface ConfigBatchItem {
  configKey: string
  configValue: string
}
export interface ConfigBatchBody {
  items: ConfigBatchItem[]
}

// ============================================================
// 7 API
// ============================================================

export const systemConfigApi = {
  /** 列表 (可按 group 过滤) */
  list: (group?: string) =>
    api.get<SystemConfigItem[]>(
      '/admin/system-config' + (group ? `?group=${encodeURIComponent(group)}` : ''),
    ),

  /** 各组计数 */
  groups: () => api.get<Record<string, number>>('/admin/system-config/groups'),

  /** 单条 */
  get: (key: string) => api.get<SystemConfigItem>(`/admin/system-config/${encodeURIComponent(key)}`),

  /** 更新单条 */
  update: (key: string, body: ConfigUpdateBody) =>
    api.put<SystemConfigItem>(`/admin/system-config/${encodeURIComponent(key)}`, body),

  /** 批量更新 */
  batchUpdate: (body: ConfigBatchBody) => api.post<number>('/admin/system-config/batch-update', body),

  /** 复位到默认值 */
  reset: (key: string) => api.post<SystemConfigItem>(`/admin/system-config/${encodeURIComponent(key)}/reset`),

  /** 手动清缓存 (多节点场景) */
  evictCache: () => api.post<void>('/admin/system-config/cache/evict'),
}
