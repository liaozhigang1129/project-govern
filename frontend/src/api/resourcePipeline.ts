/**
 * P6 资源管道大盘 - 资源管理协同
 *  后端: /api/resource-pipeline/**
 *  - 4 KPI (总资源/已分配/空闲/加班)
 *  - 人员 × 周 产能热力图
 *  - 技能矩阵 Top-20
 *  - 加班预警
 *  - 部门产能分布
 */
import api from './client'

// ============================================================
// 类型
// ============================================================

/** 4 项 KPI */
export interface ResourceKpis {
  totalResources: number
  allocated: number
  idle: number
  overloaded: number
  utilization: number // %
  totalSkills: number // 技能种类
  activeProjects: number // 当前在岗项目数
  avgAllocation: number // 平均分配率 (%)
}

/** 单周单元格 */
export interface WeekCell {
  allocPct: number
  actualHrs: number
  overload: boolean
}

/** 人员 × 周 矩阵 */
export interface CapacityMatrix {
  from: string
  to: string
  weeks: string[]
  users: Array<{
    userId: number
    weeks: Record<string, WeekCell>
  }>
}

/** 技能聚合 */
export interface SkillStat {
  skillCode: string
  count: number
  avgLevel: number
  certified: number
}

/** 加班预警 */
export interface OverloadAlert {
  userId: number
  userName: string
  departmentId: number
  departmentName: string
  allocSum: number
  projectCount: number
}

/** 部门产能 */
export interface DeptCapacity {
  departmentId: number
  departmentName: string
  headCount: number
  totalAllocation: number
}

// ============================================================
// API
// ============================================================

export function getResourceKpis() {
  return api.get<ResourceKpis>('/resource-pipeline/kpis')
}

export function getCapacityMatrix(from: string, to: string) {
  return api.get<CapacityMatrix>('/resource-pipeline/capacity-matrix', {
    params: { from, to },
  })
}

export function getSkillMatrix() {
  return api.get<SkillStat[]>('/resource-pipeline/skill-matrix')
}

export function getOverloadAlerts() {
  return api.get<OverloadAlert[]>('/resource-pipeline/overload-alerts')
}

export function getDepartmentCapacity() {
  return api.get<DeptCapacity[]>('/resource-pipeline/department-capacity')
}
