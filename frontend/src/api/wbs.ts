/**
 * WBS API (V2.5)
 *
 * 后端: /api/wbs/**
 *  - 树查询 / CRUD / 资源分配 / EVM 快照
 *  - client.ts 拦截器已解 ApiResponse 包装, 这里直接返回 T
 */
import api from './client'

/** WBS 任务节点 — 对齐后端 WbsTaskNode */
export interface WbsTaskNode {
  id: number
  projectId: number
  parentId: number | null
  wbsCode: string
  name: string
  taskType: 'SUMMARY' | 'EXECUTION' | 'MILESTONE' | 'DELIVERABLE'
  status: 'NOT_STARTED' | 'IN_PROGRESS' | 'BLOCKED' | 'COMPLETED' | 'CANCELLED'
  ownerUserId: number | null
  planStartDate: string | null
  planEndDate: string | null
  actualStartDate: string | null
  actualEndDate: string | null
  planHours: number
  actualHours: number
  progressPct: number
  weight: number
  critical: boolean
  milestone: boolean
  milestoneId: number | null
  predecessorIds: number[]
  deliverable: string | null
  remark: string | null
  createdAt: string
  updatedAt: string
  depth: number
  path: string[]
  children: WbsTaskNode[]
}

/** WBS 项目级汇总 — 对齐后端 WbsProgressSummary */
export interface WbsProgressSummary {
  projectId: number
  taskCount: number
  completedCount: number
  inProgressCount: number
  blockedCount: number
  notStartedCount: number
  criticalCount: number
  milestoneCount: number
  weightedProgressPct: number
  totalPlanHours: number
  totalActualHours: number
  hoursBurnPct: number
}

/** WBS 任务分配(人员) — 对齐后端 WbsAssignmentResponse */
export interface WbsAssignment {
  id: number
  wbsTaskId: number
  userId: number
  role: string
  plannedHours: number
  actualHours: number
  startDate: string | null
  endDate: string | null
  createdAt: string
  updatedAt: string
}

/** EVM 快照 — 对齐后端 BudgetSnapshotResponse */
export interface BudgetSnapshot {
  id: number
  projectId: number
  snapshotDate: string
  version: number
  reason: string | null
  bac: number
  pv: number
  ev: number
  ac: number
  cpi: number
  spi: number
  eac: number
  etc: number
  vac: number
  createdBy: number | null
  createdAt: string
}

// ============================================================
// WBS 任务
// ============================================================

/** 拉项目全树 */
export function getWbsTree(projectId: number) {
  return api.get<WbsTaskNode[]>(`/wbs/tasks/by-project/${projectId}`)
}

/** 拉项目扁平列表 */
export function getWbsFlat(projectId: number) {
  return api.get<WbsTaskNode[]>(`/wbs/tasks/flat/by-project/${projectId}`)
}

/** 拉单个任务 */
export function getWbsTask(id: number) {
  return api.get<WbsTaskNode>(`/wbs/tasks/${id}`)
}

/** 新建/更新任务 (id 不传=新建) */
export function saveWbsTask(req: Partial<WbsTaskNode> & { projectId: number }) {
  return api.post<WbsTaskNode>('/wbs/tasks', req)
}

/** 软删除任务 */
export function deleteWbsTask(id: number) {
  return api.delete<void>(`/wbs/tasks/${id}`)
}

/** 项目级进度汇总 */
export function getWbsProgress(projectId: number) {
  return api.get<WbsProgressSummary>(`/wbs/progress/${projectId}`)
}

// ============================================================
// 资源分配
// ============================================================

export function getAssignmentsByTask(wbsTaskId: number) {
  return api.get<WbsAssignment[]>(`/wbs/assignments/by-task/${wbsTaskId}`)
}

export function getAssignmentsByUser(userId: number) {
  return api.get<WbsAssignment[]>(`/wbs/assignments/by-user/${userId}`)
}

export function upsertAssignment(req: {
  id?: number
  wbsTaskId: number
  userId: number
  role: 'LEAD' | 'DOER' | 'REVIEWER' | 'QA' | 'OBSERVER'
  plannedHours: number
  actualHours?: number
  startDate?: string
  endDate?: string
}) {
  return api.post<WbsAssignment>('/wbs/assignments', req)
}

export function deleteAssignment(id: number) {
  return api.delete<void>(`/wbs/assignments/${id}`)
}

// ============================================================
// EVM 快照
// ============================================================

export function getSnapshots(projectId: number) {
  return api.get<BudgetSnapshot[]>(`/wbs/snapshots/${projectId}`)
}

export function getSnapshotsRange(projectId: number, from: string, to: string) {
  return api.get<BudgetSnapshot[]>(`/wbs/snapshots/${projectId}/range?from=${from}&to=${to}`)
}

export function triggerSnapshot(projectId: number, reason?: string) {
  return api.post<BudgetSnapshot>(`/wbs/snapshots/${projectId}/trigger`, { reason })
}

/** P3.1 趋势: 项目最近 N 天 EVM 快照 (每天 1 条, 升序) */
export function getSnapshotsTrend(projectId: number, days = 30) {
  return api.get<BudgetSnapshot[]>(`/wbs/snapshots/${projectId}/trend?days=${days}`)
}

/** P3.2 资源矩阵: 项目下所有 (task, user) 分配 (扁平, 前端二次组装成矩阵) */
export function getAssignmentsByProject(projectId: number) {
  return api.get<WbsAssignment[]>(`/wbs/assignments/by-project/${projectId}`)
}

// ============================================================
// P3.3 WBS 甘特图
// ============================================================

/** 单行: 一个 WbsTask 的甘特图渲染数据 (对齐后端 WbsGanttRow) */
export interface WbsGanttRow {
  taskId: number
  wbsCode: string
  name: string
  depth: number
  parentId: number | null
  taskType: 'SUMMARY' | 'EXECUTION' | 'MILESTONE' | 'DELIVERABLE'
  status: 'NOT_STARTED' | 'IN_PROGRESS' | 'BLOCKED' | 'COMPLETED' | 'CANCELLED'
  ownerUserId: number | null
  ownerName: string | null
  planStart: string | null // YYYY-MM-DD
  planEnd: string | null
  actualStart: string | null
  actualEnd: string | null
  progressPct: number
  weight: number
  critical: boolean
  milestone: boolean
  planHours: number
  actualHours: number
}

/** 项目级 WBS 甘特图响应 (对齐后端 WbsGanttResponse) */
export interface WbsGanttResponse {
  projectId: number
  rangeFrom: string
  rangeTo: string
  taskCount: number
  rows: WbsGanttRow[]
}

/** 拉项目的 WBS 任务甘特图数据 (后端自动算坐标轴) */
export function getWbsGantt(projectId: number) {
  return api.get<WbsGanttResponse>(`/wbs/gantt/by-project/${projectId}`)
}

// ============================================================
// P3.2 网络图 + P3.3 关键路径 (后端一次返回)
// ============================================================

/** 单个任务节点 (网络图) */
export interface WbsNetworkNode {
  taskId: number
  wbsCode: string
  name: string
  status: 'NOT_STARTED' | 'IN_PROGRESS' | 'BLOCKED' | 'COMPLETED' | 'CANCELLED'
  progressPct: number
  milestone: boolean
  critical: boolean
  planStart: string | null
  planEnd: string | null
  planDurationDays: number | null
  planHours: number
  ownerName: string | null
}

/** 一条有向边 (A → B, A 是 B 的紧前) */
export interface WbsNetworkEdge {
  fromTaskId: number
  toTaskId: number
  isCriticalEdge: boolean
}

/** 网络图 + 关键路径响应 */
export interface WbsNetworkResponse {
  projectId: number
  taskCount: number
  nodes: WbsNetworkNode[]
  edges: WbsNetworkEdge[]
  criticalTaskIds: number[]
}

/** 拉项目 WBS 网络图 + 关键路径 */
export function getWbsNetwork(projectId: number) {
  return api.get<WbsNetworkResponse>(`/wbs/network/by-project/${projectId}`)
}
