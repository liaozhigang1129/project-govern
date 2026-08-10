/**
 * P6 商机配置大盘 - 漏斗
 *  后端: /api/opportunity-funnel/**
 *  - 5 KPI (进行中/已成交/已流失/赢率/加权管道)
 *  - 6 阶段漏斗
 *  - 阶段转化率
 *  - 月度成交趋势
 *  - 销售排行 Top-20
 *  - BU × PL 金额分布
 */
import api from './client'

// ============================================================
// 类型
// ============================================================

/** 5 项 KPI */
export interface OpportunityKpis {
  openCount: number
  wonCount: number
  lostCount: number
  openAmount: number
  wonAmount: number
  winRate: number // %
  weightedPipeline: number // amount × probability 加权
  totalOpportunities: number // 总商机数 (含 CLOSED)
  buCount: number // 涉及 BU 数
  avgDealSize: number // 平均成交单额 (万)
}

/** 漏斗单段 */
export interface FunnelStage {
  stage: 'LEAD' | 'QUALIFIED' | 'PROPOSAL' | 'NEGOTIATION' | 'WON' | 'LOST'
  count: number
  amount: number
  color: string
}

/** 阶段转化率 */
export interface ConversionRate {
  from: string
  to: string
  rate: number // %
}

export interface MonthlyTrend {
  month: string // 2026-06
  wonCount: number
  wonAmount: number
}

export interface SalesRank {
  userId: number
  userName: string
  openCount: number
  wonCount: number
  wonAmount: number
}

export interface BuPlAmount {
  buId: number | null
  buName: string
  plId: number | null
  plName: string
  openAmount: number
  wonAmount: number
  wonCount: number
}

// ============================================================
// API
// ============================================================

export function getOpportunityKpis() {
  return api.get<OpportunityKpis>('/opportunity-funnel/kpis')
}

export function getFunnel() {
  return api.get<FunnelStage[]>('/opportunity-funnel/funnel')
}

export function getConversionRates() {
  return api.get<ConversionRate[]>('/opportunity-funnel/conversion-rates')
}

export function getMonthlyTrend() {
  return api.get<MonthlyTrend[]>('/opportunity-funnel/monthly-trend')
}

export function getSalesRank() {
  return api.get<SalesRank[]>('/opportunity-funnel/sales-rank')
}

export function getAmountByBuPl() {
  return api.get<BuPlAmount[]>('/opportunity-funnel/amount-by-bu-pl')
}
