/**
 * RiskStore (P4 风险管理) — Pinia store
 *
 * 数据分类 (3 类, 跟前端 risk.ts 11 个调用对齐):
 *   - 风险主表: list / byId / health / matrix
 *   - 应对行动: 按 riskId 缓存在 byRiskId
 *   - 历史:     按 riskId 缓存在 byRiskId
 *
 * 状态不持久化 (跟 auth.ts 一致, 走内存 + 路由刷新)
 * 组件直接用 computed 包 store.state, 配合 watch 触发 load
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  getRisksByProject, getActiveRisks, getRisk, saveRisk, deleteRisk,
  getRiskResponses, saveRiskResponse, deleteRiskResponse,
  getRiskHistory, getRiskHealth, getRiskMatrix,
  type RiskItem, type RiskRequest, type RiskResponseItem, type RiskResponseRequest,
  type RiskHistoryItem, type RiskHealthSummary, type RiskMatrix,
} from '@/api/risk'

export const useRiskStore = defineStore('risk', () => {
  // ============================================================
  // state
  // ============================================================

  /** 项目级风险主表缓存 (projectId → RiskItem[]) */
  const listByProject = ref<Map<number, RiskItem[]>>(new Map())
  /** 项目级活跃风险 (projectId → RiskItem[]) */
  const activeByProject = ref<Map<number, RiskItem[]>>(new Map())
  /** 单条缓存 (riskId → RiskItem) */
  const byId = ref<Map<number, RiskItem>>(new Map())

  /** 健康度 (projectId → RiskHealthSummary) */
  const healthByProject = ref<Map<number, RiskHealthSummary>>(new Map())
  /** 矩阵 (projectId → RiskMatrix) */
  const matrixByProject = ref<Map<number, RiskMatrix>>(new Map())

  /** 应对行动 (riskId → RiskResponseItem[]) */
  const responsesByRisk = ref<Map<number, RiskResponseItem[]>>(new Map())
  /** 历史 (riskId → RiskHistoryItem[]) */
  const historyByRisk = ref<Map<number, RiskHistoryItem[]>>(new Map())

  /** 加载态 (action → bool) — 颗粒度到每个 projectId/riskId, 避免互锁 */
  const loading = ref<Set<string>>(new Set())

  // ============================================================
  // actions — 风险主表
  // ============================================================

  function isLoading(key: string) {
    return loading.value.has(key)
  }
  function setLoading(key: string, on: boolean) {
    if (on) loading.value.add(key)
    else loading.value.delete(key)
  }

  async function loadList(projectId: number, activeOnly = false) {
    const k = `list:${projectId}:${activeOnly}`
    if (isLoading(k)) return
    setLoading(k, true)
    try {
      const data = activeOnly
        ? await getActiveRisks(projectId)
        : await getRisksByProject(projectId)
      ;(activeOnly ? activeByProject : listByProject).value.set(projectId, data)
      // 顺手回填 byId
      for (const r of data) byId.value.set(r.id, r)
    } finally {
      setLoading(k, false)
    }
  }

  async function loadOne(id: number) {
    const k = `one:${id}`
    if (isLoading(k)) return
    setLoading(k, true)
    try {
      const r = await getRisk(id)
      byId.value.set(id, r)
    } finally {
      setLoading(k, false)
    }
  }

  async function save(req: RiskRequest) {
    const saved = await saveRisk(req)
    byId.value.set(saved.id, saved)
    // 写完刷所属项目的 list (idempotent: 覆盖)
    const projectMap = listByProject.value.get(saved.projectId)
    if (projectMap) {
      const idx = projectMap.findIndex(r => r.id === saved.id)
      if (idx >= 0) projectMap[idx] = saved
      else projectMap.unshift(saved)
    }
    const activeMap = activeByProject.value.get(saved.projectId)
    if (activeMap) {
      const idx = activeMap.findIndex(r => r.id === saved.id)
      // 状态非活跃 (CLOSED/ACCEPTED) → 移出活跃列表
      if (saved.status === 'CLOSED' || saved.status === 'ACCEPTED') {
        if (idx >= 0) activeMap.splice(idx, 1)
      } else {
        if (idx >= 0) activeMap[idx] = saved
        else activeMap.unshift(saved)
      }
    }
    // KPI / 矩阵也失效, 下次访问时重拉
    healthByProject.value.delete(saved.projectId)
    matrixByProject.value.delete(saved.projectId)
    return saved
  }

  async function remove(id: number, projectId: number) {
    await deleteRisk(id)
    byId.value.delete(id)
    listByProject.value.get(projectId)?.splice(
      listByProject.value.get(projectId)!.findIndex(r => r.id === id), 1)
    activeByProject.value.get(projectId)?.splice(
      activeByProject.value.get(projectId)!.findIndex(r => r.id === id), 1)
    healthByProject.value.delete(projectId)
    matrixByProject.value.delete(projectId)
  }

  // ============================================================
  // actions — 应对行动
  // ============================================================

  async function loadResponses(riskId: number) {
    const k = `resp:${riskId}`
    if (isLoading(k)) return
    setLoading(k, true)
    try {
      const data = await getRiskResponses(riskId)
      responsesByRisk.value.set(riskId, data)
    } finally {
      setLoading(k, false)
    }
  }

  async function saveResponse(riskId: number, req: RiskResponseRequest) {
    const saved = await saveRiskResponse(riskId, req)
    const list = responsesByRisk.value.get(riskId) ?? []
    const idx = list.findIndex(x => x.id === saved.id)
    if (idx >= 0) list[idx] = saved
    else list.push(saved)
    responsesByRisk.value.set(riskId, list)
    return saved
  }

  async function removeResponse(riskId: number, responseId: number) {
    await deleteRiskResponse(responseId)
    const list = responsesByRisk.value.get(riskId)
    if (list) {
      const idx = list.findIndex(x => x.id === responseId)
      if (idx >= 0) list.splice(idx, 1)
    }
  }

  // ============================================================
  // actions — 历史
  // ============================================================

  async function loadHistory(riskId: number) {
    const k = `hist:${riskId}`
    if (isLoading(k)) return
    setLoading(k, true)
    try {
      const data = await getRiskHistory(riskId)
      historyByRisk.value.set(riskId, data)
    } finally {
      setLoading(k, false)
    }
  }

  // ============================================================
  // actions — 健康度 + 矩阵
  // ============================================================

  async function loadHealth(projectId: number) {
    const k = `health:${projectId}`
    if (isLoading(k)) return
    setLoading(k, true)
    try {
      const data = await getRiskHealth(projectId)
      healthByProject.value.set(projectId, data)
    } finally {
      setLoading(k, false)
    }
  }

  async function loadMatrix(projectId: number) {
    const k = `matrix:${projectId}`
    if (isLoading(k)) return
    setLoading(k, true)
    try {
      const data = await getRiskMatrix(projectId)
      matrixByProject.value.set(projectId, data)
    } finally {
      setLoading(k, false)
    }
  }

  // ============================================================
  // 工具: 清空 (项目切换时调用, 避免数据串)
  // ============================================================
  function reset() {
    listByProject.value.clear()
    activeByProject.value.clear()
    byId.value.clear()
    responsesByRisk.value.clear()
    historyByRisk.value.clear()
    healthByProject.value.clear()
    matrixByProject.value.clear()
    loading.value.clear()
  }

  return {
    // state
    listByProject, activeByProject, byId,
    healthByProject, matrixByProject,
    responsesByRisk, historyByRisk,
    loading,
    // 风险
    loadList, loadOne, save, remove,
    // 应对行动
    loadResponses, saveResponse, removeResponse,
    // 历史
    loadHistory,
    // KPI
    loadHealth, loadMatrix,
    // 工具
    reset,
    isLoading,
  }
})
