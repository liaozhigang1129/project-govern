/**
 * Finance API — 财务模块统一入口 (F3 + V5.0 3-way match)
 *
 * 端点来源:
 *  - 后端 FinanceController: 合同 / 发票 / 付款 / 成本项 / 对账
 *  - 后端 AlertRuleController: COST_DIFF 规则
 *
 * @since V5.0 / WP-M4-03 / T-06
 */
import api from './client'

// ============================================================
// 类型 — 3-way match 对账
// ============================================================

export type MatchStatus = 'MATCHED' | 'PARTIAL' | 'MISMATCH' | 'PENDING'

export interface ReconciliationItem {
  id: number
  projectId: number
  contractId: number | null
  invoiceId: number | null
  paymentId: number | null
  costItemId: number | null
  period: string // 'YYYY-MM'
  contractAmount: number
  invoiceAmount: number
  paymentAmount: number
  costAmount: number
  diffAmount: number
  diffReason: string | null
  matchStatus: MatchStatus
  reconciledAt: string | null
  reconciledBy: number | null
}

export interface ReconciliationListResponse {
  total: number
  page: number
  size: number
  totalPages: number
  items: ReconciliationItem[]
}

export interface ReconciliationHealth {
  projectId: number | null
  total: number
  matched: number
  mismatch: number
  partial: number
  pending: number
  totalDiff: number
  greenRate: number // 0..1
}

export interface ReconciliationListParams {
  projectId?: number
  status?: MatchStatus
  from?: string // ISO
  to?: string
  page?: number
  size?: number
}

// ============================================================
// API — 全部基于 ApiResponse 解包后的 axios 实例
// ============================================================

export const financeApi = {
  /**
   * 对账列表
   * GET /api/finance/reconciliation
   */
  reconciliationList(params: ReconciliationListParams = {}): Promise<ReconciliationListResponse> {
    const q: Record<string, string | number> = {}
    if (params.projectId !== undefined) q.projectId = params.projectId
    if (params.status) q.status = params.status
    if (params.from) q.from = params.from
    if (params.to) q.to = params.to
    q.page = params.page ?? 0
    q.size = params.size ?? 20
    return api.get('/finance/reconciliation', { params: q })
  },

  /**
   * 对账详情
   * GET /api/finance/reconciliation/{id}
   */
  reconciliationGet(id: number): Promise<ReconciliationItem> {
    return api.get(`/finance/reconciliation/${id}`)
  },

  /**
   * 重跑单条对账
   * POST /api/finance/reconciliation/retry/{id}
   */
  reconciliationRetry(id: number): Promise<ReconciliationItem> {
    return api.post(`/finance/reconciliation/retry/${id}`)
  },

  /**
   * 对账健康度聚合
   * GET /api/finance/reconciliation/health?projectId=
   */
  reconciliationHealth(projectId?: number): Promise<ReconciliationHealth> {
    const params = projectId !== undefined ? { projectId } : {}
    return api.get('/finance/reconciliation/health', { params })
  },

  /**
   * 播种 COST_DIFF 告警规则 (幂等)
   * POST /api/alert/rules/seed/cost-diff
   */
  seedCostDiffRule(): Promise<{ created: boolean; ruleId: number; code: string }> {
    return api.post('/alert/rules/seed/cost-diff')
  },
}
