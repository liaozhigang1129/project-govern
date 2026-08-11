/**
 * Alert API — 预警模块 (F4)
 *
 * 端点来源:
 *  - 后端 AlertController: 列表/详情/ack/resolve/stats
 *  - 后端 AlertRuleController: COST_DIFF 规则 seed
 *
 * @since V5.1+ / WP-M5-02 / T-05
 */
import api from './client'

// ============================================================
// 类型
// ============================================================

export type AlertStatus = 'NEW' | 'ACKNOWLEDGED' | 'RESOLVED' | 'SUPPRESSED'
export type AlertSeverity = 'HIGH' | 'MEDIUM' | 'LOW' | 'CRITICAL'

export interface AlertItem {
  id: number
  ruleId: number
  triggeredAt: string | null
  severity: AlertSeverity
  message: string
  targetType: string
  targetId: number | null
  projectId: number | null
  actualValue: number | null
  thresholdValue: number | null
  status: AlertStatus
  acknowledgedBy: number | null
  acknowledgedAt: string | null
  resolvedAt: string | null
  notifyStatus: string
}

export interface AlertListResponse {
  total: number
  page: number
  size: number
  totalPages: number
  items: AlertItem[]
}

export interface AlertListParams {
  typeCode?: string
  severity?: AlertSeverity
  status?: AlertStatus
  projectId?: number
  page?: number
  size?: number
}

export interface AlertStats {
  bySeverity: Record<string, number>
  byTypeCode: Record<string, number>
}

// ============================================================
// API
// ============================================================

export const alertApi = {
  /** 列表 */
  list(params: AlertListParams = {}): Promise<AlertListResponse> {
    const q: Record<string, string | number> = {}
    if (params.typeCode) q.typeCode = params.typeCode
    if (params.severity) q.severity = params.severity
    if (params.status) q.status = params.status
    if (params.projectId !== undefined) q.projectId = params.projectId
    q.page = params.page ?? 0
    q.size = params.size ?? 20
    return api.get('/alerts', { params: q })
  },

  /** 详情 */
  get(id: number): Promise<AlertItem> {
    return api.get(`/alerts/${id}`)
  },

  /** 确认 (NEW → ACKNOWLEDGED) */
  ack(id: number): Promise<AlertItem> {
    return api.post(`/alerts/${id}/ack`)
  },

  /** 解决 */
  resolve(id: number): Promise<AlertItem> {
    return api.post(`/alerts/${id}/resolve`)
  },

  /** 统计 */
  stats(projectId?: number): Promise<AlertStats> {
    const params = projectId !== undefined ? { projectId } : {}
    return api.get('/alerts/stats', { params })
  },

  /** 播种 COST_DIFF 规则 (idempotent) — 与 financeApi 同实现,这里转发 */
  seedCostDiffRule(): Promise<{ created: boolean; ruleId: number; code: string }> {
    return api.post('/alert/rules/seed/cost-diff')
  },
}
