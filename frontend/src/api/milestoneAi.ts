/**
 * Milestone AI Advisor API (P5 智能预警 - rule-engine v1.0)
 *
 * 后端: /api/milestone-ai/**
 *  - 5 维信号 (OVERDUE / SPI / PHASE_LAG / VELOCITY / HISTORICAL)
 *  - 评分引擎: sum(信号强度 × 权重)
 *  - 严重度: score >= 60 = CRITICAL, >= 30 = WARNING, else INFO
 *  - 一键落地: apply → 复用 RiskService.save()
 *
 *  client.ts 拦截器已解 ApiResponse 包装, 这里直接返回 T
 */
import api from './client'

// ============================================================
// 类型 (对齐后端 MilestoneAiAdvisoryDto + MilestoneAiSignalDto)
// ============================================================

/** 严重度 */
export type Severity = 'CRITICAL' | 'WARNING' | 'INFO'

/** 状态机 */
export type AdvisoryStatus = 'PENDING' | 'APPLIED' | 'REJECTED' | 'EXPIRED'

/** 5 维信号类型 */
export type SignalType = 'OVERDUE' | 'SPI' | 'PHASE_LAG' | 'VELOCITY' | 'HISTORICAL'

/** 批跑范围 */
export type RunScope = 'PROJECT' | 'BU' | 'PL' | 'PORTFOLIO' | 'ALL'

/** 单条信号 — 对齐后端 MilestoneAiSignalDto */
export interface MilestoneAiSignalDto {
  id?: number
  signalType: SignalType
  intensity: number // 0-100
  weight: number // 0-1
  score: number // intensity × weight
  description: string
  missing: boolean
}

/** 主建议 — 对齐后端 MilestoneAiAdvisoryDto */
export interface MilestoneAiAdvisoryDto {
  id: number
  projectId: number
  projectName?: string
  milestoneId: number
  milestoneName: string
  phaseId?: number | null
  phaseCode?: string | null
  phaseName?: string | null
  milestonePlanDate?: string | null
  milestoneStatusCode?: string | null
  severity: Severity
  score: number // 0-100
  confidence: number // 0-1
  signalOverdue: number
  signalSpi: number
  signalPhaseLag: number
  signalVelocity: number
  signalHistorical: number
  reasonsJson: string[] // 字符串数组
  suggestionsJson: Array<{ signal: SignalType; action: string; priority?: number }>
  category: 'SCHEDULE' | 'COST' | 'SCOPE' | 'QUALITY' | 'RESOURCE' | 'EXTERNAL'
  suggestedProbability: number // 1-5
  suggestedImpact: number // 1-5
  status: AdvisoryStatus
  modelVersion: string
  fingerprint: number
  decidedAt: string | null
  appliedAt: string | null
  appliedBy: number | null
  appliedRiskId: number | null
  rejectReason?: string | null
  createdAt: string
  updatedAt: string
  // 展开字段 (后端 fetch detail 时返回)
  signals?: MilestoneAiSignalDto[]
}

/** 严重度汇总 */
export interface SeveritySummary {
  projectId: number
  total: number
  critical: number
  warning: number
  info: number
  pending: number
  applied: number
  rejected: number
  avgScore: number
  lastRunAt: string | null
}

/** 拒绝请求 */
export interface RejectRequest {
  reason: string
}

/** 批跑参数 */
export interface RunBatchParams {
  scope: RunScope
  buId?: number | null
  plId?: number | null
  daysToPlan?: number // 只看 plan_date 距离今天 N 天内 (默认 60)
}

// ============================================================
// API 调用层
// ============================================================

/** 单里程碑分析: 立即跑规则引擎, 落库 */
export function runAdvisor(projectId: number, milestoneId: number) {
  return api.post<MilestoneAiAdvisoryDto>('/milestone-ai/run', null, { params: { projectId, milestoneId } })
}

/** 取建议详情 (含 5 维信号明细) */
export function getAdvisory(advisoryId: number) {
  return api.get<MilestoneAiAdvisoryDto>(`/milestone-ai/advisory/${advisoryId}`)
}

/** 列建议 (项目维度, 可按状态筛) */
export function listAdvisory(params: {
  projectId: number
  status?: AdvisoryStatus | 'ALL'
  severity?: Severity | 'ALL'
}) {
  return api.get<MilestoneAiAdvisoryDto[]>('/milestone-ai/advisory', { params })
}

/** 严重度汇总 */
export function getSummary(projectId: number) {
  return api.get<SeveritySummary>('/milestone-ai/summary', { params: { projectId } })
}

/** 一键落地: 建议 → Risk (后端调 RiskService.save) */
export function applyAdvisory(advisoryId: number, ownerUserId?: number) {
  return api.post<MilestoneAiAdvisoryDto>(`/milestone-ai/apply/${advisoryId}`, null, {
    params: ownerUserId != null ? { ownerUserId } : {},
  })
}

/** 拒绝: 标 REJECTED + 写理由 */
export function rejectAdvisory(advisoryId: number, reason: string) {
  return api.post<MilestoneAiAdvisoryDto>(`/milestone-ai/reject/${advisoryId}`, { reason } as RejectRequest)
}

/** 批量跑: 范围 PROJECT / BU / PL / PORTFOLIO / ALL */
export function runBatch(params: RunBatchParams) {
  return api.post<{ scanned: number; newAdvisories: number; skipped: number; durationMs: number }>(
    '/milestone-ai/run-batch',
    null,
    {
      params: {
        scope: params.scope,
        buId: params.buId ?? null,
        plId: params.plId ?? null,
        daysToPlan: params.daysToPlan ?? 60,
      },
    },
  )
}
