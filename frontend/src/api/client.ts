/**
 * 重新导出 AxiosResponse,以便调用方用 `as Foo` 解包
 */
export type { AxiosResponse }
import axios, { type AxiosError, type AxiosRequestConfig, type AxiosResponse } from 'axios'

// 拦截器:统一解 ApiResponse 包装(后端标准响应 {code, message, data})
// 解包后,后续 get<T>() 应直接返回 T,所以我们要"吞掉" AxiosResponse<T> 包装
// 做法:把 default export 的类型重写成 自定义 Api(返回 T 而非 AxiosResponse<T>)
const _axios = axios.create({
  baseURL: import.meta.env.VITE_API_BASE ?? '/api',
  timeout: 10000,
})

_axios.interceptors.request.use((cfg) => {
  const tok = localStorage.getItem('token')
  if (tok) cfg.headers.Authorization = `Bearer ${tok}`
  return cfg
})

_axios.interceptors.response.use(
  (r: AxiosResponse) => {
    const body = r.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code !== 0) {
        return Promise.reject(new Error(body.message ?? 'API error'))
      }
      return body.data
    }
    return body
  },
  (err: AxiosError<ApiErrorBody>) => {
    // V4.18 友好错误: 网络/5xx 给 ElMessage 提示 + 自动重试 1 次(幂等 GET)
    const status = err.response?.status
    if (status === 401) {
      localStorage.removeItem('token')
      window.location.href = '/login'
    } else if (status === undefined) {
      // 网络层错误(nginx 502/504/connection refused), 不弹框避免噪声, 让上层 catch
      console.warn('[api] network error:', err.message)
    } else if (status >= 500) {
      console.warn(`[api] server ${status}:`, err.config?.url)
      try {
        // 动态引入避免循环依赖
        import('element-plus').then(({ ElMessage }) => {
          ElMessage({
            type: 'error',
            message: `后端服务暂时不可达(${status}), 请稍后重试`,
            duration: 3000,
            showClose: true,
          })
        })
      } catch {
        /* ElMessage 不可用时静默 */
      }
    }
    const msg = err.response?.data?.message ?? err.message
    return Promise.reject(new Error(msg))
  },
)

/**
 * 类型化 api 客户端 — get/post/put/delete 都返回 T(已解 ApiResponse 包装)
 * 用法: await api.get<MyType>('/xxx')  // Promise<MyType>
 */
type Api = {
  get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T>
  post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  put<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  patch<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  delete<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T>
}
const api = _axios as unknown as Api

export default api
// 同时保留 axios 原始实例,需要 AxiosResponse 高级用法的代码可以 import 它
export const axiosRaw = _axios

// --- 类型定义(从 openapi.json 手抄的精简版) ---
export interface ApiErrorBody {
  code?: number
  message?: string
  data?: unknown
}

export interface UserInfo {
  id: number
  username: string
  fullName: string
  role: string
  departmentId: number
}

/**
 * 当前用户角色集合 (L1-3 配套: 配合 /api/role-menus/mine 用)
 */
export interface MyRolesResponse {
  roleIds: number[]
  primaryRoleId: number | null
  primaryRoleCode: string | null
}

/**
 * 用户(对齐后端 /users 接口返回的 AppUser 实体字段)
 *  - 后端字段:id / username / fullName / email / phone / enabled / jobTitle
 *  - role 是嵌套 Role 对象,前端做下拉时取 primaryRole.code
 */
export interface AppUser {
  id: number
  username: string
  fullName: string
  email?: string
  phone?: string
  departmentId?: number | null
  jobTitle?: string
  enabled?: boolean
  primaryRole?: { id: number; code: string; name: string }
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  user: UserInfo
}

export interface KpiResponse {
  activeCount: number
  overdueProjects: number
  closedThisMonth: number
  newInitiationsThisMonth: number
}

/** BU 分布统计行 */
export interface BuDistributionRow {
  buId: number
  buName: string
  buCode: string
  projectCount: number
  avgProgress: number
}

/** PL 分布统计行 */
export interface PlDistributionRow {
  plId: number
  plName: string
  plCode: string
  buName: string
  projectCount: number
  avgProgress: number
}

/**
 * 项目详情页聚合响应(对齐 ProjectOverviewResponse)
 */
export interface ProjectOverview {
  project: ProjectDetail
  milestones: Milestone[]
  progressPct: number
}

// 字典小引用:后端 /projects 接口嵌套的 type/status/health 都长这样
// (ProjectDetailResponse.DictRef)
export interface DictRef {
  id: number
  code: string
  name: string
  colorHex?: string // 仅 HealthLevel 用
  parentId?: number // 字典引用:仅 PL(指向 BU) / RP(指向 PL) 用,前端级联下拉过滤用
  version?: string // 字典引用:仅 RP 用(产品版本号)
}

/**
 * 项目卡片 — 对齐后端 ProjectDetailResponse /projects/{id} 列表简化版
 * 注意:字典字段已从扁平 (typeName / statusCode) 改成嵌套 (type.code)
 * 防止前端用字典 id 越权指任意条目
 */
export interface ProjectCard {
  id: number
  code: string
  name: string
  customer?: string
  type?: DictRef
  status?: DictRef
  health?: DictRef
  bu?: DictRef // 业务单元
  pl?: DictRef // 产品线(parentId=buId,前端级联用)
  relatedProduct?: DictRef // 关联产品(parentId=plId,version 是产品版本)
  pmUserId?: number
  pmUserName?: string
  planStartDate?: string
  planEndDate?: string
  progressPct: number
  budgetEstimate?: number
  updatedAt?: string
}

/** 项目详情 — 比 ProjectCard 多了 description/background/goals/scope 等长字段 */
export interface ProjectDetail extends ProjectCard {
  description?: string
  background?: string
  goals?: string
  scope?: string
  departmentId?: number
  sponsorUserId?: number
  planWorkdays?: number
  actualStartDate?: string
  actualEndDate?: string
  createdAt?: string
}

/** 项目列表查询参数(对齐后端 ProjectQuery) */
export interface ProjectQuery {
  buId?: number
  plId?: number
  pmUserId?: number
  planStartFrom?: string
  planStartTo?: string
  keyword?: string
}

/** 业务单元 */
export interface BusinessUnit {
  id: number
  code: string
  name: string
  description?: string
  sortOrder: number
  enabled: boolean
}

/** 产品线(parentBu = BusinessUnit) */
export interface ProductLine {
  id: number
  code: string
  name: string
  description?: string
  sortOrder: number
  enabled: boolean
  bu: BusinessUnit
}

/** 关联产品(parentPl = ProductLine,可选 version) */
export interface RelatedProduct {
  id: number
  code: string
  name: string
  description?: string
  version?: string
  sortOrder: number
  enabled: boolean
  pl: ProductLine
}

export interface Milestone {
  id: number
  projectId: number
  name: string
  sequence: number
  planDate: string
  actualDate?: string
  status: { id: number; code: string; name: string; terminal: boolean }
  weight: number
  ownerUserId?: number
  deliverable?: string
  remark?: string
  completedAt?: string
}

export interface Initiation {
  id: number
  code: string
  title: string
  applicantId: number
  departmentId?: number
  background: string
  goals: string
  scope: string
  budgetEstimate?: number
  planWorkdays?: number
  plannedStart?: string
  plannedEnd?: string
  initialRisks?: string
  status: { id: number; code: string; name: string }
  currentStep?: string
  submittedAt?: string
  closedAt?: string
  projectId?: number
}

/** 审批记录 — 对齐后端 ApprovalRecord entity + /initiations/{id}/records */
export interface ApprovalRecord {
  id: number
  initiationId: number
  stepId: number
  approverId: number
  decision: 'APPROVED' | 'REJECTED' | 'SUPPLEMENT'
  comment?: string
  decidedAt: string
  createdAt: string
}

/** 审批决定请求体 */
export interface DecideRequest {
  decision: 'APPROVED' | 'REJECTED' | 'SUPPLEMENT'
  comment?: string
}

// =================================================================
// V3.0 立项全流程增强 (SOW / AI WBS / 资源 / 风险 / 预算)
// =================================================================

/** SOW 文件元数据 — 对齐后端 InitiationSowFile */
export interface SowFile {
  id: number
  initiationId: number
  fileName: string
  fileSize: number
  contentType?: string
  uploadedBy: number
  uploadedAt: string
  downloadUrl?: string // 后端 /sow/{sowId}/download 绝对路径
}

/** AI WBS 工作包草稿项 */
export interface AiWbsWorkPackage {
  name: string
  durationWeeks: number
  ownerRoleCode?: string
  description?: string
}

/** AI WBS 里程碑草稿项 */
export interface AiWbsMilestone {
  name: string
  sequence: number
  targetWeek?: number
  workPackages: AiWbsWorkPackage[]
}

/** AI 风险草稿项 */
export interface AiWbsRisk {
  title: string
  level: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  probability?: number
  impact?: string
  suggestion: string
}

/** AI WBS 单个文件抽取结果(V4.23) */
export interface SowFileExtraction {
  fileId: number
  fileName: string
  contentType?: string
  /** true=抽到文本, false=抽不到(损坏/扫描件/格式不支持) */
  extracted: boolean
  /** 抽到的字符数(0 表示失败) */
  chars: number
  /** 失败原因: extractor_returned_empty / file_missing_on_disk / exception:xxx */
  reason?: string
}

/** AI 生成时的 SOW 来源元信息(V4.23) */
export interface SowSourceMeta {
  usedBodySowText: boolean
  usedPasteText: boolean
  usedFiles: number
  extractedFiles: number
  failedFiles: number
  fileExtractions: SowFileExtraction[]
}

/** AI WBS 草稿 */
export interface AiWbsDraft {
  milestones: AiWbsMilestone[]
  risks: AiWbsRisk[]
  meta?: Record<string, unknown>
  /** 后端 draftId(用于 Step 3 调 /ai-wbs/apply/{draftId} 落库) */
  draftId?: number
  /** V4.23: SOW 来源元信息(哪个文件抽到了/没抽到) */
  sourceMeta?: SowSourceMeta
  /** V4.21: 4 智能体未命中原因 */
  unmatchedAgents?: Array<Record<string, unknown>>
  /** V4.21: 被裁掉的 WP 清单 */
  hallucinationReport?: Array<Record<string, unknown>>
}

/** AI WBS 草稿持久化记录(后端 initiation_ai_wbs_draft) */
export interface AiWbsDraftRecord {
  id: number
  initiationId: number
  draft: AiWbsDraft
  createdAt: string
  appliedAt?: string
}

/** 资源派遣计划 */
export interface ResourcePlan {
  id?: number
  initiationId?: number
  userId?: number
  userName?: string
  roleCode?: string
  roleName?: string
  allocationPct: number
  startDate?: string
  endDate?: string
  planHours?: number
  hourlyRate?: number
  costAmount?: number
  createdAt?: string
}

/** 风险应对 */
export interface RiskResponse {
  id?: number
  initiationId?: number
  riskTitle: string
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  riskSuggestion?: string
  responseAction?: string
  responseCost: number
  status: 'PLANNED' | 'IN_PROGRESS' | 'DONE' | 'CANCELLED'
  ownerId?: number
  ownerName?: string
}

/** 预算快照 (initiation_budget_freeze) */
export interface BudgetFreeze {
  id?: number
  initiationId: number
  frozenBy?: number
  frozenAt?: string
  contractAmount: number
  resourceCost: number
  riskCost: number
  otherCost: number
  totalCost: number
  margin: number
  marginPct: number
  snapshot?: Record<string, unknown>
}

// =================================================================
// V2.3 项目组成员 (project_member)
// =================================================================

/** 项目成员角色字典 — 对齐后端 /dict/member-roles 返回 */
export interface MemberRole {
  id: number
  code: string // PM / ASSISTANT / ARCH / BA / DEV / QA / CFG
  name: string // 项目经理 / 项目助理 / ...
  description?: string
  sortOrder: number
}

/** 项目成员 — 对齐后端 ProjectMemberResponse */
export interface ProjectMember {
  id: number
  projectId: number
  userId?: number // 内部系统用户(可空=外部人员)
  memberName: string // 姓名(内部 user 取 fullName,外部手填)
  external: boolean // TRUE=外部人员
  joinDate: string // 参与开始日期 (YYYY-MM-DD)
  leaveDate?: string // 参与结束日期(可空=仍在项目中)
  allocationPct: number // 投入比例 0-100
  remark?: string
  role: { id: number; code: string; name: string }
}

/** 新增/编辑项目成员 请求体 — 对齐后端 ProjectMemberRequest */
export interface ProjectMemberInput {
  roleCode: string // 必填,字典 code
  userId?: number
  memberName?: string // 内部 user 时可空(后端从 fullName 自动填),外部必填
  external?: boolean
  joinDate: string // 必填 YYYY-MM-DD
  leaveDate?: string
  allocationPct?: number // 默认 100
  remark?: string
}
