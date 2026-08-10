/**
 * 里程碑分析 API (V3.1 改:按 PHASE 桶 = 7 阶段)
 *  - 主视图: GET /api/milestones/analysis/distribution
 *  - 下钻:   GET /api/milestones/analysis/projects
 */
import api from './client'

// 7 阶段常量 (与后端 V3.1 milestone_phase 对齐)
export const PHASE_LIST = [
  { id: 1, code: 'INITIATION', name: '立项', color: '#909399' },
  { id: 2, code: 'REQUIREMENT', name: '需求', color: '#409EFF' },
  { id: 3, code: 'DESIGN', name: '设计', color: '#67C23A' },
  { id: 4, code: 'DEVELOPMENT', name: '开发', color: '#E6A23C' },
  { id: 5, code: 'TESTING', name: '测试', color: '#F56C6C' },
  { id: 6, code: 'DEPLOY', name: '上线运维', color: '#9C27B0' },
  { id: 7, code: 'MAINTENANCE', name: '维保', color: '#795548' },
] as const

// 4 状态常量
export const STATUS_LIST = [
  { code: 'PENDING', name: '未开始', color: '#909399' },
  { code: 'IN_PROGRESS', name: '进行中', color: '#409EFF' },
  { code: 'COMPLETED', name: '已完成', color: '#67C23A' },
  { code: 'DELAYED', name: '已延期', color: '#F56C6C' },
] as const

export type MilestoneAnalysisQuery = {
  scope: 'company' | 'bu' | 'pl'
  period?: 'this_week' | 'this_month' | 'next_week' | 'next_month' | 'custom'
  from?: string
  to?: string
  buId?: number
  plId?: number
}

export type PhaseBucketItem = {
  phaseId: number
  code: string
  phaseName: string
  count: number
}

export type NameStatusCount = {
  name: string
  count: number
  statusCode: string
}

export type PhaseBucket = {
  count: number
  byStatus: Record<string, number>
  byName: NameStatusCount[]
}

export type MilestoneAnalysis = {
  scope: string
  periodLabel: string
  from: string
  to: string
  totalMilestones: number
  byPhase: PhaseBucketItem[]
  phases: Record<string, PhaseBucket>
}

export type MilestoneProjectRow = {
  projectId: number
  projectCode: string
  projectName: string
  status: string
  statusCode: string
  statusName: string
  milestoneName: string
  planDate: string
  actualDate: string | null
  weight: number
  ownerName: string | null
  departmentName: string | null
  buName: string | null
  pmName: string | null
}

export type MilestoneDrillDown = {
  milestoneId: number | null
  milestoneName: string | null
  phaseId: number | null
  phaseName: string | null
  statusCode: string | null
  statusName: string | null
  from: string
  to: string
  scope: string
  filters: string
  total: number
  projects: MilestoneProjectRow[]
}

/** 主视图: 按 PHASE 桶 = 7 阶段 */
export async function fetchAnalysis(q: MilestoneAnalysisQuery): Promise<MilestoneAnalysis> {
  return api.get<MilestoneAnalysis>('/milestones/analysis/distribution', { params: q })
}

/** 下钻: 某 phase / status / milestone → 命中项目列表 */
export async function fetchDrillDown(params: {
  scope: 'company' | 'bu' | 'pl'
  period?: string
  from?: string
  to?: string
  buId?: number
  plId?: number
  phaseId?: number
  statusCode?: string
  milestoneName?: string
}): Promise<MilestoneDrillDown> {
  return api.get<MilestoneDrillDown>('/milestones/analysis/projects', { params })
}
