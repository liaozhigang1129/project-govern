/**
 * Risks API (P4 风险管理)
 *
 * 后端: /api/risks/**
 *  - 风险主表 / 应对行动 / 变更历史 / 5x5 矩阵 / 健康度 KPI
 *  - client.ts 拦截器已解 ApiResponse 包装, 这里直接返回 T
 */
import api from './client'

/** 风险主表 — 对齐后端 RiskResponse */
export interface RiskItem {
  id: number
  projectId: number
  title: string
  description?: string
  category?: string
  level: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
  status: 'OPEN' | 'MITIGATING' | 'OCCURRED' | 'ACCEPTED' | 'CLOSED'
  // ... 其余字段
  [k: string]: any
}

// 兼容旧组件引用
export type RiskLevel = RiskItem['level']
export type RiskStatus = RiskItem['status']

/** 风险主表 — 对齐后端 RiskResponse (详细字段) */
export interface RiskItemFull {
  id: number
  projectId: number
  code: string // R-001
  title: string
  description: string | null
  category: 'TECHNICAL' | 'SCHEDULE' | 'COST' | 'QUALITY' | 'EXTERNAL' | 'ORGANIZATIONAL' | 'OTHER'
  probability: number // 1-5
  impact: number // 1-5
  score: number // 1-25 (probability × impact)
  level: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  status: 'OPEN' | 'MITIGATING' | 'CLOSED' | 'OCCURRED' | 'ACCEPTED'
  ownerUserId: number | null
  ownerName: string | null
  mitigation: string | null
  contingency: string | null
  responseStrategy: 'AVOID' | 'MITIGATE' | 'TRANSFER' | 'ACCEPT' | 'EXPLOIT' | 'ENHANCE' | 'SHARE' | null
  identifiedDate: string | null
  targetCloseDate: string | null
  actualCloseDate: string | null
  relatedWbsTaskId: number | null
  relatedWbsTaskName: string | null
  relatedMilestoneId: number | null
  relatedMilestoneName: string | null
  createdBy: number | null
  createdAt: string
  updatedAt: string
}

/** 风险主表 — 创建/更新请求 (id 缺失=新建) */
export interface RiskRequest {
  id?: number
  projectId: number
  code: string
  title: string
  description?: string | null
  category: string
  probability: number
  impact: number
  status?: string
  ownerUserId?: number | null
  mitigation?: string | null
  contingency?: string | null
  responseStrategy?: string | null
  identifiedDate?: string | null
  targetCloseDate?: string | null
  relatedWbsTaskId?: number | null
  relatedMilestoneId?: number | null
}

// ============================================================
// 应对行动
// ============================================================

export interface RiskResponseItem {
  id: number
  riskId: number
  action: string
  ownerUserId: number | null
  ownerName: string | null
  dueDate: string | null
  completedAt: string | null
  status: 'PLANNED' | 'IN_PROGRESS' | 'DONE' | 'CANCELLED'
  note: string | null
  createdAt: string
}

export interface RiskResponseRequest {
  id?: number
  action: string
  ownerUserId?: number | null
  dueDate?: string | null
  status?: string
  note?: string | null
}

// ============================================================
// 变更历史
// ============================================================

export interface RiskHistoryItem {
  id: number
  riskId: number
  action:
    | 'CREATED'
    | 'STATUS_CHANGED'
    | 'SCORE_CHANGED'
    | 'OWNER_CHANGED'
    | 'LEVEL_CHANGED'
    | 'COMMENTED'
    | 'RESPONSE_ADDED'
    | 'RESPONSE_DONE'
    | 'DELETED' // V2.7 新增, 软删专用
  fieldName: string | null
  oldValue: string | null
  newValue: string | null
  comment: string | null
  operatorId: number | null
  operatorName: string | null
  createdAt: string
}

// ============================================================
// 健康度 + 矩阵
// ============================================================

export interface RiskHealthSummary {
  projectId: number
  totalCount: number
  activeCount: number
  criticalActive: number
  highActive: number
  occurredCount: number
  maxActiveScore: number
  byCategory: Record<string, number>
  byLevel: Record<string, number>
}

export interface RiskMatrixCell {
  probability: number
  impact: number
  count: number
  risks: RiskItem[]
}

export interface RiskMatrix {
  cells: RiskMatrixCell[]
}

// ============================================================
// API 调用
// ============================================================

/** 拉项目全部风险 (按 score 降序, 含已关闭) */
export function getRisksByProject(projectId: number) {
  return api.get<RiskItem[]>(`/risks/by-project/${projectId}`)
}

/** 拉项目活跃风险 (排除 CLOSED/ACCEPTED) */
export function getActiveRisks(projectId: number) {
  return api.get<RiskItem[]>(`/risks/by-project/${projectId}/active`)
}

/** 单条 */
export function getRisk(id: number) {
  return api.get<RiskItem>(`/risks/${id}`)
}

/** 新建/更新 */
export function saveRisk(req: RiskRequest) {
  return api.post<RiskItem>('/risks', req)
}

/** 软删 */
export function deleteRisk(id: number) {
  return api.delete<void>(`/risks/${id}`)
}

/** 应对行动 */
export function getRiskResponses(riskId: number) {
  return api.get<RiskResponseItem[]>(`/risks/${riskId}/responses`)
}
export function saveRiskResponse(riskId: number, req: RiskResponseRequest) {
  return api.post<RiskResponseItem>(`/risks/${riskId}/responses`, req)
}
export function deleteRiskResponse(responseId: number) {
  return api.delete<void>(`/risks/responses/${responseId}`)
}

/** 历史 */
export function getRiskHistory(riskId: number) {
  return api.get<RiskHistoryItem[]>(`/risks/${riskId}/history`)
}

/** 健康度 */
export function getRiskHealth(projectId: number) {
  return api.get<RiskHealthSummary>(`/risks/health/by-project/${projectId}`)
}

/** 5x5 矩阵 */
export function getRiskMatrix(projectId: number) {
  return api.get<RiskMatrix>(`/risks/matrix/by-project/${projectId}`)
}
