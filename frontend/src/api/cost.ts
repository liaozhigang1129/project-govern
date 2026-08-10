/**
 * Cost Engine API (P0-A.1 — F1 工时→成本引擎)
 *
 * 12 端点,与后端 CostController 严格对齐:
 *   6 角色档 CRUD + 6 HourlyRate CRUD(含 CSV) + 2 用户维度成本查询(月/日)
 *
 * 用法:
 *   import { costApi } from '@/api/cost'
 *   const resp = await costApi.userMonthCost(1, '2026-06')
 *   console.log(resp.totalCost) // => 24000.00
 */
import api from './client'

// ============================================================
// 类型
// ============================================================

/** 6 角色档默认价 (财务在 admin 页顶部直接编辑) */
export interface RoleCostDefaultItem {
  code: string
  name: string
  rate: number
  sortOrder: number
}

/** HourlyRate 一行(单人 override 或 角色档调价) */
export interface HourlyRateItem {
  id: number
  roleCode: string
  userId: number | null // null = 角色档,非空 = 单人 override
  userName: string | null
  rate: number
  effectiveMonth: string // 'YYYY-MM'
  endMonth: string | null // null = 仍生效
  remark: string | null
  createdBy: number | null
  createdAt: string | null
  updatedAt: string | null
}

/** 写入 HourlyRate 共用 DTO(userId 为 null 时 = 角色档调价) */
export interface HourlyRateUpsertBody {
  roleCode: string
  userId?: number | null
  rate: number
  effectiveMonth: string // 'YYYY-MM'
  endMonth?: string | null // 'YYYY-MM' 或 null
  remark?: string
}

/** 改 6 角色档默认价 */
export interface RoleDefaultUpdateBody {
  code: string
  rate: number
}

/** CSV 导入返回 */
export interface CsvImportResult {
  okCount: number
  failCount: number
  errors: string[]
}

/** 单条工时 × 时薪的小计 */
export interface CostBreakdownItem {
  projectId: number
  projectCode: string | null
  projectName: string | null
  milestoneId: number | null
  hours: number
  rate: number
  rateSource: 'USER_OVERRIDE' | 'ROLE_OVERRIDE' | 'ROLE_COST_DEFAULT' | 'USER_DEFAULT' | 'NONE'
  cost: number
}

/** 费率来源分账 (5 个 Bucket 的小时数) */
export interface RateSourceBreakdown {
  userOverrideHours: number
  roleOverrideHours: number
  roleDefaultHours: number
  userDefaultHours: number
  noneHours: number
}

/** F1 主验收响应: GET /api/cost/user/{userId}?month=2026-06 */
export interface UserMonthCostResponse {
  userId: number
  userName: string
  month: string // 'YYYY-MM'
  totalHours: number
  totalCost: number
  primaryRoleCode: string | null
  items: CostBreakdownItem[]
  rateSourceBreakdown: RateSourceBreakdown
}

/** 单日成本: GET /api/cost/user/{userId}/day?date=2026-06-15 */
export interface UserDayCostResponse {
  userId: number
  userName: string
  date: string // 'YYYY-MM-DD'
  hours: number
  cost: number
  rate: number
  rateSource: string
  primaryRoleCode: string | null
  items: CostBreakdownItem[]
}

// 自封装,绕开 axios 的 AxiosResponse<T> 推断
async function call<T>(p: Promise<unknown>): Promise<T> {
  return p as Promise<T>
}

// ============================================================
// 12 API
// ============================================================

export const costApi = {
  // ----------------------------------------------------------
  // 6 角色档默认价
  // ----------------------------------------------------------
  listRoleDefaults: () => call<RoleCostDefaultItem[]>(api.get('/cost/role-defaults')),

  updateRoleDefault: (body: RoleDefaultUpdateBody) =>
    call<RoleCostDefaultItem>(api.put('/cost/role-defaults', body)),

  // ----------------------------------------------------------
  // HourlyRate 列表 / 详情 / CRUD
  // ----------------------------------------------------------
  listHourlyRates: (params: { userId?: number } = {}) =>
    call<HourlyRateItem[]>(api.get('/cost/hourly-rates', { params })),

  getHourlyRate: (id: number) => call<HourlyRateItem>(api.get(`/cost/hourly-rates/${id}`)),

  createHourlyRate: (body: HourlyRateUpsertBody) =>
    call<HourlyRateItem>(api.post('/cost/hourly-rates', body)),

  updateHourlyRate: (id: number, body: HourlyRateUpsertBody) =>
    call<HourlyRateItem>(api.put(`/cost/hourly-rates/${id}`, body)),

  closeHourlyRate: (id: number, atMonth: string /* YYYY-MM */) =>
    call<HourlyRateItem>(api.post(`/cost/hourly-rates/${id}/close`, null, { params: { atMonth } })),

  deleteHourlyRate: (id: number) => call<void>(api.delete(`/cost/hourly-rates/${id}`)),

  // ----------------------------------------------------------
  // CSV 上传 / 模板下载
  // ----------------------------------------------------------
  /**
   * 下载 CSV 模板 — 后端返回 text/csv 文本
   * 用法:
   *   const csv = await costApi.downloadCsvTemplate()
   *   saveAs(new Blob([csv], { type: 'text/csv' }), 'hourly_rate_template.csv')
   */
  downloadCsvTemplate: async (): Promise<string> => {
    const r = await axiosRaw.get<string>('/cost/hourly-rates/csv-template', {
      responseType: 'text',
      transformResponse: (d) => d, // 不要 JSON.parse
    })
    return r.data
  },

  /**
   * 上传 CSV 文件
   * 用法:
   *   const fd = new FormData(); fd.append('file', file)
   *   await costApi.importCsv(fd)
   */
  importCsv: (formData: FormData) =>
    call<CsvImportResult>(
      api.post('/cost/hourly-rates/import', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      }),
    ),

  // ----------------------------------------------------------
  // 用户维度成本查询 — F1 主验收
  // ----------------------------------------------------------
  /**
   * F1 主验收: GET /api/cost/user/{userId}?month=2026-06
   * 返回 totalHours / totalCost / items / rateSourceBreakdown
   */
  userMonthCost: (userId: number, month: string /* YYYY-MM */) =>
    call<UserMonthCostResponse>(api.get(`/cost/user/${userId}`, { params: { month } })),

  /**
   * 单日成本(辅助校验): GET /api/cost/user/{userId}/day?date=2026-06-15
   */
  userDayCost: (userId: number, date: string /* YYYY-MM-DD */) =>
    call<UserDayCostResponse>(api.get(`/cost/user/${userId}/day`, { params: { date } })),

  // ----------------------------------------------------------
  // 多维成本核算 — F2 价值核心
  // ----------------------------------------------------------
  /**
   * T3/T4 端到端: GET /api/cost/dimension?dim=PROJECT&month=2026-06
   * dim 可选: PROJECT / PHASE / DEPT
   * month 可选 (YYYY-MM): 不传则返回全历史
   */
  dimension: (params: { dim: 'PROJECT' | 'PHASE' | 'DEPT'; month?: string }) =>
    call<CostDimensionResponse>(api.get('/cost/dimension', { params })),
}

/** F2 多维成本行 (T3/T4) */
export interface CostDimensionRow {
  dimension: 'PROJECT' | 'PHASE' | 'DEPT'
  key: string
  code: string
  label: string
  phaseId: number | null
  phaseName: string | null
  sortOrder: number | null
  yearMonth: string | null
  hours: number
  cost: number
  budget: number | null
  costRate: number // 人均时薪 = cost / hours
  headcount: number
  costPct: number // 占比 (0-100)
}

/** F2 多维成本响应 */
export interface CostDimensionResponse {
  dimension: 'PROJECT' | 'PHASE' | 'DEPT'
  yearMonth: string | null
  totalHours: number
  totalCost: number
  totalHeadcount: number
  activeProjects: number // 活跃项目数
  avgCostPerUser: number // 人均成本
  budgetCoveragePct: number // 预算覆盖率 (%)
  avgHourlyRate: number // 平均时薪 (¥/h)
  rows: CostDimensionRow[]
}

// ============================================================
// axios 原始实例(给 downloadCsvTemplate 这种特殊场景用)
// ============================================================
import { axiosRaw } from './client'
