<script setup lang="ts">
import { onMounted, ref, reactive, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api/client'
import { workloadApi } from '@/api/workload'
import { userApi } from '@/api/users'
import VChart from 'vue-echarts'
import type { GanttBar, GanttResponse } from '@/components/GanttView.vue'
import type { ProjectOverview, BusinessUnit, ProductLine, RelatedProduct, ProjectMember, ProjectMemberInput } from '@/api/client'
import type { AppUser } from '@/api/client'
import WbsTreeTable from "@/components/wbs/WbsTreeTable.vue"
import WbsEditDialog from "@/components/wbs/WbsEditDialog.vue"

const route = useRoute()
const router = useRouter()
const overview = ref<ProjectOverview | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const healthUpdating = ref(false)
const newHealth = ref<string>('GREEN')

/** Tab 状态:basic / milestones / gantt / health / config / members
 *  - 支持 ?tab=config 等 URL 参数直接定位
 *  - 切换时把 tab 写回 URL,方便复制/刷新保留位置
 */
type DetailTab = 'basic' | 'milestones' | 'wbs' | 'plan' | 'gantt' | 'health' | 'etc' | 'burndown' | 'config' | 'members'
const VALID_TABS: DetailTab[] = ['basic', 'milestones', 'wbs', 'plan', 'gantt', 'health', 'etc', 'burndown', 'config', 'members']
function readTabFromRoute(): DetailTab {
  const q = String(route.query.tab ?? '')
  return (VALID_TABS as string[]).includes(q) ? (q as DetailTab) : 'basic'
}
const activeTab = ref<DetailTab>(readTabFromRoute())

// ====== 字典/用户下拉数据(项目配置用) ======
const buList = ref<BusinessUnit[]>([])
const plList = ref<ProductLine[]>([])
const rpList = ref<RelatedProduct[]>([])
const userList = ref<AppUser[]>([])


// ====== ETC + 燃尽图(V2.13) ======
type EvmSnapshot = {
  id: number
  projectId: number
  snapshotDate: string
  version: number
  reason: string
  bac: number
  pv: number
  ev: number
  ac: number
  cpi: number
  spi: number
  eac: number
  etc: number
  vac: number
  createdBy: number
  createdAt: string
}
const evmLoading = ref(false)
const evmSnapshots = ref<EvmSnapshot[]>([])
/** 最新一条 EVM 快照 (null = 该项目还没快照) */
const evmLatest = computed<EvmSnapshot | null>(() =>
  evmSnapshots.value.length ? evmSnapshots.value[evmSnapshots.value.length - 1] : null
)
/** CPI/SPI 状态颜色 (0.8 / 1.0 / 1.2 三档) */
function cpiColor(c: number | undefined | null): string {
  if (c == null) return '#909399'
  if (c < 0.8) return '#F56C6C'
  if (c < 1.0) return '#E6A23C'
  if (c < 1.2) return '#67C23A'
  return '#409EFF'
}

async function loadEvm() {
  if (evmLoading.value || !overview.value) return
  evmLoading.value = true
  try {
    const r = await api.get<EvmSnapshot[]>(`/wbs/snapshots/${overview.value.project.id}/trend`)
    evmSnapshots.value = r ?? []
  } catch {
    evmSnapshots.value = []
  } finally {
    evmLoading.value = false
  }
}

async function triggerEvmSnapshot() {
  if (!overview.value) return
  try {
    await api.post(`/wbs/snapshots/${overview.value.project.id}/trigger?reason=MANUAL-from-UI`)
    ElMessage.success('已触发 EVM 快照')
    await loadEvm()
  } catch (e: any) {
    ElMessage.error('触发失败: ' + (e?.message ?? '未知'))
  }
}

const burndownChartOpt = computed(() => {
  if (evmSnapshots.value.length === 0) return {}
  const data = evmSnapshots.value
  const dates = data.map(s => s.snapshotDate)
  const bac = data.map(s => s.bac)
  const pv = data.map(s => s.pv)
  const ev = data.map(s => s.ev)
  const ac = data.map(s => s.ac)
  return {
    tooltip: { trigger: 'axis' },
    legend: { top: 0, left: 'center' },
    grid: { top: 50, left: 70, right: 30, bottom: 40 },
    xAxis: { type: 'category', data: dates, name: '快照日期' },
    yAxis: { type: 'value', name: '成本/工时 (¥)', axisLabel: { formatter: (v: number) => v >= 1000 ? (v / 1000).toFixed(0) + 'k' : String(v) } },
    series: [
      { name: 'BAC (预算)',     type: 'line', data: bac, step: 'end',  itemStyle: { color: '#909399' }, lineStyle: { type: 'dashed' } },
      { name: 'PV (计划值)',    type: 'line', data: pv,  smooth: true, itemStyle: { color: '#409EFF' } },
      { name: 'EV (挣值)',      type: 'line', data: ev,  smooth: true, itemStyle: { color: '#67C23A' } },
      { name: 'AC (实际成本)',  type: 'line', data: ac,  smooth: true, itemStyle: { color: '#F56C6C' } },
    ],
  }
})


// ====== WBS 分解 + 项目计划(V2.14) ======
type WbsTaskNode = {
  id: number
  projectId: number
  parentId: number | null
  wbsCode: string
  name: string
  taskType: string
  status: 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'BLOCKED' | 'CANCELLED'
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
  predecessorIds: number[]
  deliverable: string | null
  remark: string | null
  children?: WbsTaskNode[]
  depth?: number
  path?: string[]
}
const wbsLoading = ref(false)
const wbsTree = ref<WbsTaskNode[]>([])
/** 控制 WBS 树展开行 (避免 default-expand-all 触发 Vue 3.5 patch bug) */
const defaultExpandedKeys = ref<number[]>([])
/** 监听 wbsTree 变化,  自动展开前 3 个 phase 节点 */
watch(wbsTree, (newTree) => {
  if (newTree.length > 0 && defaultExpandedKeys.value.length === 0) {
    defaultExpandedKeys.value = newTree
      .filter(n => !!n.children && n.children.length > 0)
      .slice(0, 3)
      .map(n => n.id)
  }
}, { immediate: true })

/** 平铺 WBS (含 path 顺序) — 用于项目计划表 */
const wbsFlat = computed<WbsTaskNode[]>(() => {
  const out: WbsTaskNode[] = []
  function walk(n: WbsTaskNode) {
    out.push(n)
    if (n.children?.length) n.children.forEach(walk)
  }
  wbsTree.value.forEach(walk)
  return out
})
/** 关键路径 (is_critical = true) 任务数 */
const wbsCriticalCount = computed(() => wbsFlat.value.filter((t: WbsTaskNode) => t.critical).length)
/** 全部用户 (id -> fullName) — 用于 WBS owner 姓名解析 */
const wbsUserMap = ref<Record<number, string>>({})
/** 全部责任人 (去重, 从 wbs owner 拉) */
const wbsOwners = computed<Record<number, string>>(() => wbsUserMap.value)

/**
 * 把子树节点 id 全部收集起来 (用于"上级任务"防成环 + "前驱任务"防成环)
 * 例: 把"需求调研"的 id 集合 = {需求调研, 调研子项1, 调研子项2}
 */
function collectDescendantIds(rootId: number, tree: WbsTaskNode[] = wbsTree.value): Set<number> {
  const ids = new Set<number>()
  function find(n: WbsTaskNode): WbsTaskNode | null {
    if (n.id === rootId) return n
    for (const c of n.children ?? []) {
      const r = find(c); if (r) return r
    }
    return null
  }
  const node = (() => { for (const r of tree) { const f = find(r); if (f) return f } return null })()
  if (!node) return ids
  ids.add(node.id)
  function walk(n: WbsTaskNode) { (n.children ?? []).forEach(c => { ids.add(c.id); walk(c) }) }
  walk(node)
  return ids
}

/** 上级任务可选列表 — 排除自己和自己的所有子孙 (防成环) */
const wbsParentCandidates = computed<WbsTaskNode[]>(() => {
  const cur = wbsEditDialog.value.form
  if (!cur?.id) return wbsFlat.value
  const blocked = collectDescendantIds(cur.id)
  return wbsFlat.value.filter(t => !blocked.has(t.id))
})

/** 上级任务变更时, 自动按新父重算 wbsCode (仅在用户没手改过的情况下)
 *  注意: 用 wbsCode 前缀扫描而非 parent.children,避免 parent_id 脏数据漏算
 */
function recomputeWbsCodeIfUserUnset() {
  const f = wbsEditDialog.value.form
  if (!f) return
  if (f.parentId == null) {
    // 顶级 — 全局扫无 '.' 的,取最大编号 + 1
    const topCodes = wbsFlat.value
      .filter(t => !t.wbsCode.includes('.'))
      .map(t => parseInt(t.wbsCode, 10) || 0)
    const next = (topCodes.length ? Math.max(...topCodes) : 0) + 1
    f.wbsCode = String(next)
  } else {
    const cur = wbsFlat.value.find(t => t.id === f.parentId)
    if (cur) {
      // 子级 — 全局扫以 "cur.wbsCode." 开头的(直接段),取最大 + 1
      // 这样能漏掉"parent_id=NULL 但 wbsCode=1.0.5"这种脏数据
      const prefix = cur.wbsCode + '.'
      const subCodes = wbsFlat.value
        .filter(t => t.wbsCode.startsWith(prefix))
        .map(t => {
          const tail = t.wbsCode.slice(prefix.length)
          const firstSeg = tail.split('.')[0]
          return parseInt(firstSeg, 10) || 0
        })
      const next = (subCodes.length ? Math.max(...subCodes) : 0) + 1
      f.wbsCode = cur.wbsCode + '.' + next
    }
  }
}

function onParentChange(newParentId: number | null | undefined) {
  const f = wbsEditDialog.value.form
  if (!f) return
  f.parentId = newParentId ?? null
  recomputeWbsCodeIfUserUnset()
}

/** 拉一次用户 options, 索引 id -> fullName */
async function loadWbsUserMap() {
  if (Object.keys(wbsUserMap.value).length > 0) return
  try {
    const users = await userApi.options()
    const m: Record<number, string> = {}
    users.forEach((u: any) => { m[u.id] = u.fullName })
    wbsUserMap.value = m
  } catch { /* ignore */ }
}
function planProgressColor(actual: number, planned: number): string {
  if (planned === 0) return '#909399'
  const ratio = actual / planned
  if (ratio < 0.8) return '#F56C6C'
  if (ratio < 1.0) return '#E6A23C'
  return '#67C23A'
}
function statusType(s: string): 'success' | 'warning' | 'info' | 'danger' | 'primary' {
  return s === 'COMPLETED' ? 'success'
       : s === 'IN_PROGRESS' ? 'warning'
       : s === 'BLOCKED' ? 'danger'
       : s === 'CANCELLED' ? 'info'
       : 'primary'
}
function statusLabel(s: string): string {
  return s === 'NOT_STARTED' ? '未开始'
       : s === 'IN_PROGRESS' ? '进行中'
       : s === 'COMPLETED' ? '已完成'
       : s === 'BLOCKED' ? '阻塞'
       : s === 'CANCELLED' ? '已取消'
       : s
}

async function loadWbs() {
  if (wbsLoading.value || !overview.value) return
  wbsLoading.value = true
  try {
    const r = await api.get<WbsTaskNode[]>(`/wbs/tasks/by-project/${overview.value.project.id}`)
    wbsTree.value = r ?? []
  } catch {
    wbsTree.value = []
  } finally {
    wbsLoading.value = false
  }
  // 用户名映射 (失败不影响 WBS 表格)
  void loadWbsUserMap()
}

// ====== WBS 编辑弹窗 (V2.5 增强) ======
const wbsDialogVisible = ref(true) /* fix-ultimate default-open then close */
const wbsEditDialog = ref({
  visible: false,
  loading: false,
  mode: 'edit' as 'edit' | 'add',
  parentForAdd: null as WbsTaskNode | null,
  form: null as null | {
    id: number
    parentId: number | null
    wbsCode: string
    name: string
    taskType: string
    status: string
    ownerUserId: number | null
    planStartDate: string
    planEndDate: string
    actualStartDate: string
    actualEndDate: string
    planHours: number
    actualHours: number
    progressPct: number
    weight: number
    critical: boolean
    milestone: boolean
    predecessorIds: number[] | null
    deliverable: string
    remark: string
  }
})

function onEditWbs(t: WbsTaskNode) {
  console.log("[onEditWbs] called for", t.wbsCode, t.name, t.id)
  wbsEditDialog.value.mode = "edit"
  wbsEditDialog.value.form = {
    id: t.id,
    parentId: t.parentId,
    wbsCode: t.wbsCode,
    name: t.name,
    taskType: t.taskType,
    status: t.status,
    ownerUserId: t.ownerUserId ?? null,
    planStartDate: t.planStartDate ?? '',
    planEndDate: t.planEndDate ?? '',
    actualStartDate: t.actualStartDate ?? '',
    actualEndDate: t.actualEndDate ?? '',
    planHours: Number(t.planHours ?? 0),
    actualHours: Number(t.actualHours ?? 0),
    progressPct: t.progressPct ?? 0,
    weight: t.weight ?? 1,
    critical: !!t.critical,
    milestone: !!t.milestone,
    predecessorIds: (t.predecessorIds ?? []).slice(),
    deliverable: t.deliverable ?? '',
    remark: t.remark ?? ''
  }
  wbsDialogVisible.value = true
}

async function onSaveWbs() {
  const f = wbsEditDialog.value.form
  if (!f) return
  if (!f.name?.trim()) { ElMessage.error('任务名称不能为空'); return }
  if (f.planStartDate && f.planEndDate && f.planStartDate > f.planEndDate) {
    ElMessage.error('计划开始日期不能晚于结束日期'); return
  }
  if (f.actualStartDate && f.actualEndDate && f.actualStartDate > f.actualEndDate) {
    ElMessage.error('实际开始日期不能晚于结束日期'); return
  }
  if (f.progressPct < 0 || f.progressPct > 100) {
    ElMessage.error('进度必须在 0-100 之间'); return
  }
  wbsEditDialog.value.loading = true
  try {
    await api.post('/wbs/tasks', {
      id: f.id,
      projectId: overview.value!.project.id,
      parentId: f.parentId ?? null,   // 跟随表单(之前写死 null 是 BUG 根因)
      wbsCode: f.wbsCode,
      name: f.name,
      taskType: f.taskType,
      status: f.status,
      ownerUserId: f.ownerUserId,
      planStartDate: f.planStartDate || null,
      planEndDate: f.planEndDate || null,
      actualStartDate: f.actualStartDate || null,
      actualEndDate: f.actualEndDate || null,
      planHours: f.planHours,
      actualHours: f.actualHours,
      progressPct: f.progressPct,
      weight: f.weight,
      critical: f.critical,
      milestone: f.milestone,
      milestoneId: null,
      predecessorIds: f.predecessorIds ?? [],
      deliverable: f.deliverable || null,
      remark: f.remark || null
    })
    ElMessage.success('保存成功')
    wbsDialogVisible.value = false
    await loadWbs()
    if (evmSnapshots.value.length > 0) void loadEvm()
  } catch (e: any) {
    ElMessage.error('保存失败: ' + (e?.response?.data?.message ?? e?.message ?? '未知错误'))
  } finally {
    wbsEditDialog.value.loading = false
  }
}

function onAddWbs(parent: WbsTaskNode | null) {
  let nextCode = ""
  let parentId: number | null = null
  if (parent) {
    parentId = parent.id
    // 用 wbsCode 前缀扫描(避免 parent_id 脏数据漏算)
    const prefix = parent.wbsCode + '.'
    const codes = wbsFlat.value
      .filter(t => t.wbsCode.startsWith(prefix))
      .map(t => {
        const tail = t.wbsCode.slice(prefix.length)
        const firstSeg = tail.split('.')[0]
        return parseInt(firstSeg, 10) || 0
      })
    const next = (codes.length ? Math.max(...codes) : 0) + 1
    nextCode = parent.wbsCode + "." + next
  } else {
    // 顶级 — 全局扫无 '.' 的
    const codes = wbsFlat.value
      .filter(t => !t.wbsCode.includes('.'))
      .map(t => parseInt(t.wbsCode, 10) || 0)
    const next = (codes.length ? Math.max(...codes) : 0) + 1
    nextCode = String(next)
  }
  wbsEditDialog.value.mode = "add"
  wbsEditDialog.value.parentForAdd = parent
  wbsEditDialog.value.form = {
    id: 0,
    parentId,
    wbsCode: nextCode,
    name: "",
    taskType: "EXECUTION",
    status: "NOT_STARTED",
    ownerUserId: null,
    planStartDate: "",
    planEndDate: "",
    actualStartDate: "",
    actualEndDate: "",
    planHours: 0,
    actualHours: 0,
    progressPct: 0,
    weight: 1,
    critical: false,
    milestone: false,
    predecessorIds: [],
    deliverable: "",
    remark: ""
  }
  wbsDialogVisible.value = true
}

async function onDeleteWbs(t: WbsTaskNode) {
  try {
    await ElMessageBox.confirm(
      "确认删除任务 \"" + t.wbsCode + " " + t.name + "\"? 此操作不可撤销 (软删)。",
      "删除 WBS 任务",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消" }
    )
  } catch { return }
  try {
    await api.delete("/wbs/tasks/" + t.id)
    ElMessage.success("删除成功")
    await loadWbs()
    if (evmSnapshots.value.length > 0) void loadEvm()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? e?.message ?? "未知错误")
  }
}

/** 编辑弹窗下拉: 责任人 */
/** 可作为前置的任务 (排除自身和全部后代, 防环) */
const wbsPredecessorOptions = computed(() => {
  if (wbsEditDialog.value.mode !== 'edit') return []
  const me = wbsEditDialog.value.form
  if (!me) return []
  // 收集自身 + 全部后代的 id (防环: 不能选自己或自己的后代)
  const ban = new Set<number>([me.id])
  const findMeAndBanDescendants = (n: any): boolean => {
    if (n.id === me.id) {
      const collect = (x: any) => { (x.children || []).forEach((c: any) => { ban.add(c.id); collect(c) }) }
      collect(n)
      return true
    }
    return (n.children || []).some(findMeAndBanDescendants)
  }
  wbsTree.value.forEach(findMeAndBanDescendants)
  // 平铺, 排除 ban
  const flat: { id: number, code: string, name: string }[] = []
  const walk = (n: any) => {
    if (!ban.has(n.id)) flat.push({ id: n.id, code: n.wbsCode, name: n.name })
    ;(n.children || []).forEach(walk)
  }
  wbsTree.value.forEach(walk)
  return flat
})

const wbsUserOptions = computed<Array<{id: number; label: string}>>(() =>
  userList.value.map((u: any) => ({ id: u.id, label: (u.fullName || u.username) + (u.jobTitle ? ` (${u.jobTitle})` : "") }))
)

// ====== 项目组成员(V2.3) ======
const memberList = ref<ProjectMember[]>([])
const memberDialog = ref({
  visible: false,
  editing: null as ProjectMember | null,  // null=新增,非空=编辑
  form: {
    roleCode: 'DEV',
    userId: undefined as number | undefined,
    memberName: '',
    external: false,
    joinDate: '',
    leaveDate: undefined as string | undefined,
    allocationPct: 100,
    remark: '',
  } as ProjectMemberInput,
  saving: false,
})

// ====== 编辑弹窗 ======
const editDialogVisible = ref(false)
const editSaving = ref(false)
const editForm = ref({
  pmUserId: undefined as number | undefined,
  buId: undefined as number | undefined,
  plId: undefined as number | undefined,
  relatedProductId: undefined as number | undefined,
})
// 级联:选 BU 后,PL 下拉只显示该 BU 下的
const filteredPlList = computed(() => {
  if (!editForm.value.buId) return plList.value
  return plList.value.filter(pl => pl.bu?.id === editForm.value.buId)
})
const filteredRpList = computed(() => {
  if (!editForm.value.plId) return rpList.value
  return rpList.value.filter(rp => rp.pl?.id === editForm.value.plId)
})

async function load() {
  const id = route.params.id
  if (!id) { error.value = '缺少项目 id'; return }
  loading.value = true
  error.value = null
  try {
    overview.value = await api.get(`/projects/${id}/overview`)
    if (overview.value) newHealth.value = overview.value.project.health?.code ?? 'GREEN'
  } catch (e: any) {
    error.value = e?.message ?? '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadDictionaries() {
  // 拉字典 + 用户(并行)
  // 注意:任一失败不阻塞整体;只让用户暂时用不上对应下拉
  const [busR, plsR, rpsR, usersR] = await Promise.allSettled([
    api.get<BusinessUnit[]>('/dict/bus'),
    api.get<ProductLine[]>('/dict/pls'),
    api.get<RelatedProduct[]>('/dict/related-products'),
    api.get<{ id: number; username: string; fullName: string; primaryRoleCode: string; departmentId: number | null }[]>('/users/options'),
  ])
  buList.value  = busR.status  === 'fulfilled' ? (busR.value  ?? []) : []
  plList.value  = plsR.status  === 'fulfilled' ? (plsR.value  ?? []) : []
  rpList.value  = rpsR.status  === 'fulfilled' ? (rpsR.value  ?? []) : []
  userList.value = usersR.status === 'fulfilled' ? (usersR.value ?? []).map(u => ({
    id: u.id,
    username: u.username,
    fullName: u.fullName,
    primaryRole: { id: 0, code: u.primaryRoleCode, name: u.primaryRoleCode },
    departmentId: u.departmentId,
  })) : []
  if (busR.status === 'rejected')  console.warn('加载 BU 字典失败:', busR.reason)
  if (plsR.status === 'rejected')  console.warn('加载 PL 字典失败:', plsR.reason)
  if (rpsR.status === 'rejected')  console.warn('加载关联产品失败:', rpsR.reason)
  if (usersR.status === 'rejected') console.warn('加载用户失败:', usersR.reason)
}

async function changeHealth(code: string) {
  if (!overview.value) return
  healthUpdating.value = true
  try {
    await api.put(`/projects/${overview.value.project.id}`, { healthCode: code })
    await load()
  } catch (e: any) {
    error.value = e?.message ?? '健康度更新失败'
  } finally {
    healthUpdating.value = false
  }
}

function openEditDialog() {
  if (!overview.value) return
  const p = overview.value.project
  editForm.value = {
    pmUserId: p.pmUserId,
    buId: p.bu?.id,
    plId: p.pl?.id,
    relatedProductId: p.relatedProduct?.id,
  }
  editDialogVisible.value = true
}

// 切换 BU 时,清掉 PL/产品的选择(级联联动)
function onBuChange() {
  if (!overview.value) return
  const orig = overview.value.project
  if (editForm.value.buId !== orig.bu?.id) {
    // 改了 BU → 清空 PL / 产品
    editForm.value.plId = undefined
    editForm.value.relatedProductId = undefined
  } else {
    // 切回原值时,PL/RP 也要回到原值
    editForm.value.plId = orig.pl?.id
    editForm.value.relatedProductId = orig.relatedProduct?.id
  }
}
function onPlChange() {
  if (!overview.value) return
  const orig = overview.value.project
  if (editForm.value.plId !== orig.pl?.id) {
    editForm.value.relatedProductId = undefined
  } else {
    editForm.value.relatedProductId = orig.relatedProduct?.id
  }
}

async function saveEdit() {
  if (!overview.value) return
  editSaving.value = true
  try {
    await api.put(`/projects/${overview.value.project.id}`, {
      pmUserId: editForm.value.pmUserId ?? null,
      buId: editForm.value.buId ?? null,
      plId: editForm.value.plId ?? null,
      relatedProductId: editForm.value.relatedProductId ?? null,
    })
    ElMessage.success('项目配置已更新')
    editDialogVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '保存失败')
  } finally {
    editSaving.value = false
  }
}

function goBack() {
  router.push('/projects')
}

// ====== 成员管理(V2.3) ======
async function loadMembers() {
  if (!overview.value) return
  const id = overview.value.project.id
  try {
    memberList.value = await api.get<ProjectMember[]>(`/projects/${id}/members`)
  } catch (e: any) {
    ElMessage.error(e?.message ?? '成员加载失败')
    memberList.value = []
  }
}

function openAddMemberDialog() {
  if (!overview.value) return
  memberDialog.value.editing = null
  memberDialog.value.form = {
    roleCode: 'DEV',
    userId: undefined,
    memberName: '',
    external: false,
    joinDate: overview.value.project.planStartDate ?? new Date().toISOString().slice(0, 10),
    leaveDate: undefined,
    allocationPct: 100,
    remark: '',
  }
  memberDialog.value.visible = true
}

function openEditMemberDialog(m: ProjectMember) {
  memberDialog.value.editing = m
  memberDialog.value.form = {
    roleCode: m.role.code,
    userId: m.userId,
    memberName: m.memberName,
    external: m.external,
    joinDate: m.joinDate,
    leaveDate: m.leaveDate,
    allocationPct: m.allocationPct,
    remark: m.remark ?? '',
  }
  memberDialog.value.visible = true
}

async function saveMember() {
  if (!overview.value) return
  const f = memberDialog.value.form
  if (!f.roleCode) { ElMessage.warning('请选择项目角色'); return }
  if (!f.external && !f.userId) { ElMessage.warning('内部成员请选择系统用户'); return }
  if (f.external && !f.memberName?.trim()) { ElMessage.warning('外部人员请填写姓名'); return }
  if (!f.joinDate) { ElMessage.warning('请填写参与开始日期'); return }
  if (f.leaveDate && f.leaveDate < f.joinDate) { ElMessage.warning('参与结束日期不能早于开始日期'); return }

  memberDialog.value.saving = true
  try {
    const id = overview.value.project.id
    if (memberDialog.value.editing) {
      await api.put(`/projects/${id}/members/${memberDialog.value.editing.id}`, f)
      ElMessage.success('成员已更新')
    } else {
      await api.post(`/projects/${id}/members`, f)
      ElMessage.success('成员已添加')
    }
    memberDialog.value.visible = false
    await loadMembers()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '保存失败')
  } finally {
    memberDialog.value.saving = false
  }
}

async function deleteMember(m: ProjectMember) {
  try {
    await ElMessageBox.confirm(`确定移除成员「${m.memberName}」?`, '提示', { type: 'warning' })
  } catch { return }
  if (!overview.value) return
  try {
    await api.delete(`/projects/${overview.value.project.id}/members/${m.id}`)
    ElMessage.success('成员已移除')
    await loadMembers()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '删除失败')
  }
}

function formatDate(s?: string) {
  return s ?? '—'
}

function formatMoney(v?: number) {
  if (v == null) return '—'
  return '¥' + v.toLocaleString('zh-CN')
}

/** 里程碑状态码 → 中文 */
const MILESTONE_STATUS_NAME: Record<string, string> = {
  PENDING: '未开始',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  DELAYED: '已延期',
}
function statusNameOf(code: string | null | undefined): string {
  if (!code) return '—'
  return MILESTONE_STATUS_NAME[code] ?? code
}

// ===== 迷你甘特图里程碑颜色 (与 GanttView.vue 同色板,保证视觉一致) =====
//   - 与全局甘特图保持同一套 HSL 推导: phaseId → 色相, phase 内 index → 4 个深浅
//   - 单项目通常 5-10 milestone,phase 内 4 槽足够
const PHASE_HUE_PD: Record<number, number> = {
  1: 280, 2: 210, 3: 165, 4: 30, 5: 0, 6: 145, 7: 200,
}
const SLOT_SL_PD: { s: number; l: number }[] = [
  { s: 75, l: 42 }, { s: 65, l: 52 }, { s: 55, l: 62 }, { s: 45, l: 72 },
]
function hslToHexPd(h: number, s: number, l: number): string {
  const sn = s / 100, ln = l / 100
  const c = (1 - Math.abs(2 * ln - 1)) * sn
  const hp = h / 60
  const x = c * (1 - Math.abs((hp % 2) - 1))
  let r = 0, g = 0, b = 0
  if      (hp < 1) [r, g, b] = [c, x, 0]
  else if (hp < 2) [r, g, b] = [x, c, 0]
  else if (hp < 3) [r, g, b] = [0, c, x]
  else if (hp < 4) [r, g, b] = [0, x, c]
  else if (hp < 5) [r, g, b] = [x, 0, c]
  else             [r, g, b] = [c, 0, x]
  const m = ln - c / 2
  const toHex = (v: number) => Math.max(0, Math.min(255, Math.round((v + m) * 255)))
    .toString(16).padStart(2, '0')
  return '#' + toHex(r) + toHex(g) + toHex(b)
}
/** 单项目迷你甘特: 返回该 milestone 的颜色 */
function pdMilestoneColor(m: any): string {
  if (m.phaseId && PHASE_HUE_PD[m.phaseId] != null) {
    // 单项目内 phase 序号 = 同 phase 内顺序; 复用全局 GanttView 的 slot 0
    return hslToHexPd(PHASE_HUE_PD[m.phaseId], 75, 42)
  }
  // fallback: 旧行为按 status
  return m.status === 'COMPLETED' ? '#67c23a'
       : m.status === 'IN_PROGRESS' ? '#e6a23c'
       : m.status === 'DELAYED' ? '#f56c6c'
       : '#909399'
}

const milestoneStats = (() => {
  // 数字样式工具(模板里用,避免重复代码)
  return null
})()

// ====== 甘特图(项目内嵌 mini 版) ======
// 设计说明:
//   这是"项目级" mini 甘特图,只画当前项目自己的 plan/actual 区间和里程碑,
//   所以坐标轴不能用全公司聚合的 rangeFrom/rangeTo(那个会被其他项目撑到 2-3 年宽,
//   把当前项目的 bar 全部挤压到屏幕边缘,看不见)。
//   改用:当前项目自身的 [planStart, planEnd] ± 7d 作为坐标轴,实际缺失时回退 today。
const ganttLoading = ref(false)
/** 当前项目在 mini 视图里的坐标轴(独立于全公司 ganttData) */
const miniGantt = ref<{ rangeFrom: string; rangeTo: string; bar: GanttBar | null }>({
  rangeFrom: '', rangeTo: '', bar: null,
})
/** P3 修复:load() 与 loadGantt() 都在 onMounted 异步触发,
 *  loadGantt 经常先于 overview 回来,被 `if (!overview.value) return` 早退,
 *  此后无重试 → 甘特图一直空。ganttTriggered 标记"已发起",overview 加载完再补一刀 */
const ganttTriggered = ref(false)
// 旧字段保留兼容(给空状态文案用),但实际不再使用
const ganttData = ref<GanttResponse | null>(null)
function projectBar() {
  return miniGantt.value.bar
}
function padIso(iso: string, days: number): string {
  const d = new Date(iso + 'T00:00:00')
  d.setDate(d.getDate() + days)
  return d.toISOString().slice(0, 10)
}
function isoToday(): string {
  return new Date().toISOString().slice(0, 10)
}
function buildMiniAxis(bar: GanttBar): { rangeFrom: string; rangeTo: string } {
  // 优先用 planStart/planEnd 决定坐标轴(±7d padding 让 bar 不贴边)
  // 都没有时,尝试 actualStart/actualEnd;再退化到 today ± 1.5 月
  const planS = bar.planStart
  const planE = bar.planEnd
  if (planS && planE && planS <= planE) {
    return { rangeFrom: padIso(planS, -7), rangeTo: padIso(planE, 7) }
  }
  const actS = bar.actualStart
  const actE = bar.actualEnd
  if (actS && actE && actS <= actE) {
    return { rangeFrom: padIso(actS, -7), rangeTo: padIso(actE, 7) }
  }
  if (planS) {
    return { rangeFrom: padIso(planS, -7), rangeTo: padIso(planS, 105) }
  }
  const today = isoToday()
  return { rangeFrom: padIso(today, -30), rangeTo: padIso(today, 60) }
}
/** P3 修复:对未完成项目,坐标轴扩到 today+30d,
 *  让"今日"指示线在条形右侧可见,不挤在 8-30 日区间内 */
function expandAxisToToday(from: string, to: string): { rangeFrom: string; rangeTo: string } {
  const today = isoToday()
  // 完工:不扩;进行中:至少让 today+30d 落在轴内
  if (today < to) return { rangeFrom: from, rangeTo: today > to ? to : padIso(today, 30) }
  return { rangeFrom: from, rangeTo: to }
}
/** 百分比:date 距 rangeFrom 的天数占坐标轴总宽度的比例 */
function leftPctLocal(date: string | null, rangeFrom: string): number {
  if (!date || !rangeFrom) return 0
  const a = new Date(rangeFrom + 'T00:00:00').getTime()
  const b = new Date(date + 'T00:00:00').getTime()
  if (isNaN(a) || isNaN(b)) return 0
  return Math.max(0, ((b - a) / 86400000))
}
function widthPctLocal(start: string, end: string, rangeFrom: string, rangeTo: string): number {
  if (!start || !end || !rangeFrom || !rangeTo) return 0
  const a = new Date(rangeFrom + 'T00:00:00').getTime()
  const b = new Date(rangeTo   + 'T00:00:00').getTime()
  const s = new Date(start     + 'T00:00:00').getTime()
  const e = new Date(end       + 'T00:00:00').getTime()
  if (isNaN(a) || isNaN(b) || isNaN(s) || isNaN(e)) return 0
  if (e <= s) return 0
  const totalDays = (b - a) / 86400000
  if (totalDays <= 0) return 0
  // 夹紧到坐标轴内
  const visStart = Math.max(s, a)
  const visEnd   = Math.min(e, b)
  if (visEnd <= visStart) return 0
  return ((visEnd - visStart) / 86400000) / totalDays * 100
}
/** 内部状态:用 ratio(0~1)而非 %,避免无限增长 */
function leftPctRatio(date: string | null, rangeFrom: string, rangeTo: string): number {
  if (!date) return 0
  if (!rangeFrom || !rangeTo) return 0
  const a = new Date(rangeFrom + 'T00:00:00').getTime()
  const b = new Date(rangeTo   + 'T00:00:00').getTime()
  const d = new Date(date     + 'T00:00:00').getTime()
  if (isNaN(a) || isNaN(b) || isNaN(d) || b <= a) return 0
  return Math.max(0, Math.min(1, (d - a) / (b - a)))
}
function widthPctRatio(start: string, end: string, rangeFrom: string, rangeTo: string): number {
  if (!start || !end) return 0
  if (!rangeFrom || !rangeTo) return 0
  const a = new Date(rangeFrom + 'T00:00:00').getTime()
  const b = new Date(rangeTo   + 'T00:00:00').getTime()
  const s = new Date(start     + 'T00:00:00').getTime()
  const e = new Date(end       + 'T00:00:00').getTime()
  if (isNaN(a) || isNaN(b) || isNaN(s) || isNaN(e) || b <= a) return 0
  const startRatio = Math.max(0, Math.min(1, (s - a) / (b - a)))
  const endRatio   = Math.max(0, Math.min(1, (e - a) / (b - a)))
  return Math.max(0, endRatio - startRatio) * 100
}
async function loadGantt() {
  if (!overview.value) return
  ganttTriggered.value = true
  ganttLoading.value = true
  try {
    // 仍然调一次全公司接口(为了从里面取出当前项目的那条 bar,顺带校验它确实在聚合里)
    // 但 mini 视图只用其中的 plan/actual/milestones 字段,坐标轴用 buildMiniAxis 重新算
    const data = await workloadApi.gantt({ includeCompleted: false })
    ganttData.value = data
    const pid = overview.value.project.id
    const bar = data.bars.find(b => b.projectId === pid) ?? null
    if (!bar) {
      miniGantt.value = { rangeFrom: '', rangeTo: '', bar: null }
      return
    }
    const axis = buildMiniAxis(bar)
    const axisExpanded = expandAxisToToday(axis.rangeFrom, axis.rangeTo)
    miniGantt.value = { ...axisExpanded, bar }
  } catch {
    ganttData.value = null
    miniGantt.value = { rangeFrom: '', rangeTo: '', bar: null }
  } finally {
    ganttLoading.value = false
  }
}

onMounted(() => {
  load()
  loadDictionaries()
  loadGantt()
  wbsDialogVisible.value = false  // fix-ultimate: close on mount
})
watch(() => route.params.id, () => {
  activeTab.value = readTabFromRoute()  // 切项目时按 URL 重新定位
  load(); loadGantt()
})
// 外部修改 ?tab= 时同步本地 activeTab(例如列表页的"配置"按钮)
watch(() => route.query.tab, (v) => {
  if (!v) return
  const t = String(v)
  if ((VALID_TABS as string[]).includes(t)) activeTab.value = t as DetailTab
})
// 用户切 tab 时把状态写回 URL(便于复制/刷新/分享)
watch(activeTab, (t) => {
  if (route.query.tab === t) return
  router.replace({ query: { ...route.query, tab: t } })
  if (t === 'etc' || t === 'burndown') {
    if (evmSnapshots.value.length === 0) void loadEvm()
  }
  if (t === 'wbs' || t === 'plan') {
    if (wbsTree.value.length === 0) void loadWbs()
  }
})
// overview 加载完后,拉成员;并补一刀 loadGantt(若之前因 race 早退)
watch(overview, (v) => {
  if (!v) return
  loadMembers()
  if (ganttTriggered.value) return       // 已有 gantt 数据,不重复拉
  if (miniGantt.value.bar) return       // mini 视图已有 bar
  void loadGantt()
  // 初始 tab 可能是 wbs/plan, 主动拉一次
  if (activeTab.value === 'wbs' || activeTab.value === 'plan') {
    if (wbsTree.value.length === 0) void loadWbs()
  }
})
</script>

<template>
  <div class="page" v-loading="loading">
    <el-page-header @back="goBack" style="margin-bottom: 16px">
      <template #content>
        <span style="font-size: 18px; font-weight: 600">
          {{ overview?.project.name ?? '项目详情' }}
        </span>
        <el-tag
          v-if="overview?.project.code"
          style="margin-left: 12px"
          effect="plain"
        >{{ overview.project.code }}</el-tag>
      </template>
    </el-page-header>

    <el-alert v-if="error" :title="error" type="error" :closable="false" style="margin-bottom: 16px" />

    <template v-if="overview">
      <el-row :gutter="16" style="margin-bottom: 16px">
        <el-col :span="6">
          <div class="kpi-card kpi-card--blue">
            <div class="kpi-card__label">项目类型</div>
            <div class="kpi-card__value" style="font-size: 18px">
              {{ overview.project.type?.name ?? '—' }}
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="kpi-card kpi-card--green">
            <div class="kpi-card__label">当前状态</div>
            <div class="kpi-card__value" style="font-size: 18px">
              <el-tag v-if="overview.project.status" effect="dark">
                {{ overview.project.status.name }}
              </el-tag>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="kpi-card kpi-card--orange">
            <div class="kpi-card__label">健康度</div>
            <div class="kpi-card__value" style="font-size: 18px">
              <el-tag
                v-if="overview.project.health"
                :color="overview.project.health.colorHex"
                effect="dark"
              >{{ overview.project.health.name }}</el-tag>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="kpi-card kpi-card--red">
            <div class="kpi-card__label">加权进度</div>
            <div class="kpi-card__value">{{ overview.progressPct }}%</div>
          </div>
        </el-col>
      </el-row>

      <el-tabs v-model="activeTab">
        <!-- Tab 1: 基本信息 -->
        <el-tab-pane label="基本信息" name="basic">
          <el-card>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="项目编号">
                {{ overview.project.code }}
              </el-descriptions-item>
              <el-descriptions-item label="客户">
                {{ overview.project.customer ?? '—' }}
              </el-descriptions-item>
              <el-descriptions-item label="业务单元 (BU)">
                <el-tag v-if="overview.project.bu" effect="plain" type="info">
                  {{ overview.project.bu.name }}
                </el-tag>
                <span v-else>—</span>
              </el-descriptions-item>
              <el-descriptions-item label="产品线 (PL)">
                <el-tag v-if="overview.project.pl" effect="plain" type="info">
                  {{ overview.project.pl.name }}
                </el-tag>
                <span v-else>—</span>
              </el-descriptions-item>
              <el-descriptions-item label="关联产品">
                <template v-if="overview.project.relatedProduct">
                  <el-tag effect="plain" type="success">
                    {{ overview.project.relatedProduct.name }}
                    <span v-if="overview.project.relatedProduct.version" style="margin-left: 4px; opacity: .7">
                      v{{ overview.project.relatedProduct.version }}
                    </span>
                  </el-tag>
                </template>
                <span v-else>—</span>
              </el-descriptions-item>
              <el-descriptions-item label="项目经理">
                <span v-if="overview.project.pmUserName">{{ overview.project.pmUserName }}</span>
                <span v-else style="color: #909399">未指定</span>
              </el-descriptions-item>
              <el-descriptions-item label="计划开始">
                {{ formatDate(overview.project.planStartDate) }}
              </el-descriptions-item>
              <el-descriptions-item label="计划结束">
                {{ formatDate(overview.project.planEndDate) }}
              </el-descriptions-item>
              <el-descriptions-item label="实际开始">
                {{ formatDate(overview.project.actualStartDate) }}
              </el-descriptions-item>
              <el-descriptions-item label="实际结束">
                {{ formatDate(overview.project.actualEndDate) }}
              </el-descriptions-item>
              <el-descriptions-item label="预算">
                {{ formatMoney(overview.project.budgetEstimate) }}
              </el-descriptions-item>
              <el-descriptions-item label="计划工时(天)">
                {{ overview.project.planWorkdays ?? '—' }}
              </el-descriptions-item>
              <el-descriptions-item label="项目描述" :span="2">
                {{ overview.project.description ?? '—' }}
              </el-descriptions-item>
              <el-descriptions-item label="背景" :span="2">
                {{ overview.project.background ?? '—' }}
              </el-descriptions-item>
              <el-descriptions-item label="目标" :span="2">
                {{ overview.project.goals ?? '—' }}
              </el-descriptions-item>
              <el-descriptions-item label="范围" :span="2">
                {{ overview.project.scope ?? '—' }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-tab-pane>

        <!-- Tab 2: 里程碑 -->
        <el-tab-pane :label="`里程碑 (${overview.milestones.length})`" name="milestones">
          <el-card>
            <template v-if="overview.milestones.length">
              <el-table :data="overview.milestones" stripe>
                <el-table-column prop="sequence" label="序号" width="70" />
                <el-table-column prop="name" label="名称" />
                <el-table-column label="状态" width="120">
                  <template #default="{ row }">
                    <el-tag v-if="row.status" effect="dark">
                      {{ row.status.name }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="weight" label="权重" width="80" align="center" />
                <el-table-column prop="planDate" label="计划完成" width="130" />
                <el-table-column prop="actualDate" label="实际完成" width="130" />
                <el-table-column prop="ownerUserId" label="负责人" width="100" />
                <el-table-column prop="deliverable" label="交付物" />
                <el-table-column prop="remark" label="备注" />
              </el-table>
            </template>
            <el-empty v-else description="该项目暂无里程碑" />
          </el-card>
        </el-tab-pane>

        <!-- Tab 2.5: WBS 分解 (树形) -->
        <el-tab-pane label="WBS 分解" name="wbs">
          <el-card>
            <template #header>
              <div style="display: flex; justify-content: space-between; align-items: center">
                <el-button size="small" type="primary" plain @click="onAddWbs(null)" style="margin-right: 12px">+ 新增顶级</el-button> <span>WBS 工作分解结构</span>
                <span style="font-size: 12px; color: #909399">
                  {{ wbsTree.length }} 阶段 / {{ wbsFlat.length }} 任务 /
                  <el-tag v-if="wbsCriticalCount" type="danger" size="small">关键 {{ wbsCriticalCount }}</el-tag>
                </span>
              </div>
            </template>

            <el-table
              v-if="wbsTree.length"
              v-loading="wbsLoading"
              :data="wbsFlat"
              row-key="id"
              :tree-props="{ children: 'children' }"
              default-expand-all
              border
              size="small"
            >
              <el-table-column prop="wbsCode" label="WBS" width="100" />
              <el-table-column prop="name" label="任务名称" min-width="200" />
              <el-table-column label="状态" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="关键" width="50" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.critical" type="danger" size="small" effect="dark">★</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="计划起止" width="200" align="center">
                <template #default="{ row }">
                  <span v-if="row.planStartDate">{{ row.planStartDate }} → {{ row.planEndDate }}</span>
                  <span v-else style="color: #c0c4cc">—</span>
                </template>
              </el-table-column>
              <el-table-column label="责任人" width="100" align="center">
                <template #default="{ row }">
                  <span v-if="row.ownerUserId">{{ wbsOwners[row.ownerUserId] ?? '#' + row.ownerUserId }}</span>
                  <span v-else style="color: #c0c4cc">—</span>
                </template>
              </el-table-column>
              <el-table-column label="工时 (实际/计划)" width="140" align="right">
                <template #default="{ row }">
                  <strong>{{ row.actualHours }}</strong> / {{ row.planHours }}h
                </template>
              </el-table-column>
              <el-table-column label="进度" width="160" align="center">
                <template #default="{ row }">
                  <el-progress :percentage="row.progressPct" :stroke-width="10" />
                </template>
              </el-table-column>
              <el-table-column label="交付物" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  <span v-if="row.deliverable" style="font-size: 12px">{{ row.deliverable }}</span>
                  <span v-else style="color: #c0c4cc">—</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="200" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" type="primary" link @click="onAddWbs(row)">+ 子</el-button>
                  <el-button size="small" link @click="onEditWbs(row)">编辑</el-button>
                  <el-button size="small" type="danger" link @click="onDeleteWbs(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>

            <el-empty v-else description="该项目暂无 WBS 任务" :image-size="80" />
          </el-card>
        </el-tab-pane>

        <!-- Tab 2.6: 项目计划 (平铺) -->
        <el-tab-pane label="项目计划" name="plan">
          <el-card>
            <template #header>
              <div style="display: flex; justify-content: space-between; align-items: center">
                <el-button size="small" type="primary" plain @click="onAddWbs(null)" style="margin-right: 12px">+ 新增顶级</el-button> <span>项目计划 (层级结构 / 工作包可向下拆解)</span>
                <el-tag size="small">共 {{ wbsFlat.length }} 任务 / {{ wbsCriticalCount }} 关键路径</el-tag>
              </div>
            </template>

            <el-table
              v-if="wbsTree.length"
              v-loading="wbsLoading"
              :data="wbsTree"
              row-key="id"
              :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
              :expand-row-keys="defaultExpandedKeys"
              border
              stripe
              size="small"
            >
              <el-table-column prop="wbsCode" label="#" width="80" />
              <el-table-column label="层级" width="80" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.children?.length" type="warning" size="small" effect="plain">📦 工作包</el-tag>
                  <span v-else-if="row.taskType === 'SUMMARY'" style="color: #909399; font-size: 11px">汇总</span>
                </template>
              </el-table-column>
              <el-table-column prop="name" label="任务" min-width="200" />
              <el-table-column label="关键" width="50" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.critical" type="danger" size="small">★</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="前驱" width="120" align="center">
                <template #default="{ row }">
                  <span v-if="row.predecessorIds?.length" style="font-size: 11px; display: flex; gap: 2px; flex-wrap: wrap; justify-content: center">
                    <el-tag v-for="pid in row.predecessorIds" :key="pid" size="small" effect="plain">
                      #{{ wbsFlat.find(x => x.id === pid)?.wbsCode ?? pid }}
                    </el-tag>
                  </span>
                  <span v-else style="color: #c0c4cc">—</span>
                </template>
              </el-table-column>
              <el-table-column label="计划起止" width="190" align="center">
                <template #default="{ row }">
                  <span v-if="row.planStartDate">{{ row.planStartDate }} → {{ row.planEndDate }}</span>
                  <span v-else style="color: #c0c4cc">—</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="责任人" width="90" align="center">
                <template #default="{ row }">
                  <span v-if="row.ownerUserId">{{ wbsOwners[row.ownerUserId] ?? '#' + row.ownerUserId }}</span>
                  <span v-else style="color: #c0c4cc">—</span>
                </template>
              </el-table-column>
              <el-table-column label="计划工时" width="80" align="right">
                <template #default="{ row }">{{ row.planHours }}h</template>
              </el-table-column>
              <el-table-column label="实际工时" width="80" align="right">
                <template #default="{ row }">
                  <strong :style="{ color: planProgressColor(row.actualHours, row.planHours) }">{{ row.actualHours }}h</strong>
                </template>
              </el-table-column>
              <el-table-column label="完成度" width="80" align="center">
                <template #default="{ row }">{{ row.progressPct }}%</template>
              </el-table-column>
              <el-table-column label="交付物" min-width="200" show-overflow-tooltip>
                <template #default="{ row }">
                  <span v-if="row.deliverable">{{ row.deliverable }}</span>
                  <span v-else style="color: #c0c4cc">—</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="200" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" type="primary" link @click="onAddWbs(row)">+ 拆解</el-button>
                  <el-button size="small" link @click="onEditWbs(row)">编辑</el-button>
                  <el-button size="small" type="danger" link @click="onDeleteWbs(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>

            <el-empty v-else description="该项目暂无 WBS 任务" :image-size="80" />
          </el-card>
        </el-tab-pane>

        <!-- Tab 3 (原): 甘特图(P1.5 收尾) -->
        <el-tab-pane label="甘特图" name="gantt">
          <el-card v-loading="ganttLoading">
            <template v-if="projectBar() && miniGantt.rangeFrom && miniGantt.rangeTo">
              <div style="margin-bottom: 8px; color: #606266; font-size: 13px">
                项目时间轴: <b>{{ miniGantt.rangeFrom }}</b> → <b>{{ miniGantt.rangeTo }}</b>
                <span style="margin-left: 12px; color: #909399">
                  (基于本项目 plan 区间 ±7d 渲染,不依赖全公司聚合时间窗)
                </span>
                <el-button
                  type="primary"
                  size="small"
                  plain
                  style="margin-left: 12px"
                  @click="router.push(`/projects/${overview.project.id}/wbs`)"
                >
                  <el-icon style="margin-right: 4px"><span>📋</span></el-icon>
                  打开 WBS 分解
                </el-button>
                <el-button
                  type="warning"
                  size="small"
                  plain
                  style="margin-left: 8px"
                  @click="router.push(`/projects/${overview.project.id}/risks`)"
                >
                  <el-icon style="margin-right: 4px"><span>⚠️</span></el-icon>
                  打开风险管理
                </el-button>
              </div>
              <div class="pd-gantt-row">
                <div class="pd-gantt-label">{{ projectBar()?.projectCode }} {{ projectBar()?.projectName }}</div>
                <div class="pd-gantt-timeline">
                  <!-- 计划区间(背景) -->
                  <div
                    v-if="projectBar()?.planStart && projectBar()?.planEnd"
                    class="pd-gantt-bar plan"
                    :style="{
                      left: leftPctRatio(projectBar()!.planStart!, miniGantt.rangeFrom, miniGantt.rangeTo) * 100 + '%',
                      width: widthPctRatio(projectBar()!.planStart!, projectBar()!.planEnd!, miniGantt.rangeFrom, miniGantt.rangeTo) + '%',
                    }"
                    :title="`计划: ${projectBar()!.planStart} ~ ${projectBar()!.planEnd}`"
                  ></div>
                  <!-- 今日竖线(P3 修复:让"今天"位置可见) -->
                  <div
                    v-if="projectBar() && miniGantt.rangeFrom && miniGantt.rangeTo"
                    class="pd-gantt-today"
                    :style="{ left: leftPctRatio(isoToday(), miniGantt.rangeFrom, miniGantt.rangeTo) * 100 + '%' }"
                  ></div>
                  <!-- 实际区间(前景 + 进度)
                       P3 修复:actualEnd 经常为 null(项目进行中),
                       退化为 planEnd 显示,这样 30% 进度条不会被隐藏 -->
                  <div
                    v-if="projectBar()?.actualStart && (projectBar()?.actualEnd || projectBar()?.planEnd)"
                    class="pd-gantt-bar actual"
                    :style="{
                      left: leftPctRatio(projectBar()!.actualStart!, miniGantt.rangeFrom, miniGantt.rangeTo) * 100 + '%',
                      width: widthPctRatio(projectBar()!.actualStart!, (projectBar()!.actualEnd || projectBar()!.planEnd!)!, miniGantt.rangeFrom, miniGantt.rangeTo) + '%',
                      background: (projectBar()!.progressPct ?? 0) >= 80 ? '#67c23a' : (projectBar()!.progressPct ?? 0) >= 50 ? '#409eff' : '#e6a23c',
                    }"
                    :title="`实际: ${projectBar()!.actualStart} ~ ${projectBar()!.actualEnd || '(进行中)'} (${projectBar()?.progressPct ?? 0}%)`"
                  >
                    {{ projectBar()?.progressPct ?? 0 }}%
                  </div>
                  <!-- 里程碑 ▼ (每条用 phaseId 派生色) -->
                  <div
                    v-for="m in projectBar()?.milestones"
                    :key="m.id"
                    class="pd-gantt-milestone"
                    :style="{
                      left: leftPctRatio(m.planDate, miniGantt.rangeFrom, miniGantt.rangeTo) * 100 + '%',
                      color: pdMilestoneColor(m),
                    }"
                    :title="`${m.name} (${m.status}) 计划: ${m.planDate}${m.actualDate ? ' / 实际: ' + m.actualDate : ''}`"
                  >▼</div>
                </div>
              </div>
              <!-- 里程碑图例列表(避免单个 ▼ tooltip 太小) -->
              <el-table
                v-if="projectBar()?.milestones?.length"
                :data="projectBar()!.milestones"
                size="small"
                stripe
                style="margin-top: 16px"
              >
                <el-table-column prop="sequence" label="#" width="60">
                  <template #default="{ $index }">{{ $index + 1 }}</template>
                </el-table-column>
                <el-table-column prop="name" label="里程碑" />
                <el-table-column label="计划日" width="130">
                  <template #default="{ row }">{{ row.planDate }}</template>
                </el-table-column>
                <el-table-column label="实际日" width="130">
                  <template #default="{ row }">{{ row.actualDate ?? '—' }}</template>
                </el-table-column>
                <el-table-column label="状态" width="110">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.status === 'COMPLETED' ? 'success' : row.status === 'IN_PROGRESS' ? 'warning' : 'info'">
                      {{ statusNameOf(row.status) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="weight" label="权重" width="80" align="center" />
              </el-table>
            </template>
            <el-empty v-else description="该项目暂无可绘制的甘特图(没有计划区间或未被纳入聚合)" />
          </el-card>
        </el-tab-pane>

        <!-- Tab 3: 健康度调整 (后续 Tab 4/5/6 已加 ETC + 燃尽图) --> 
        <!-- WBS 编辑弹窗 (任意 WBS / 项目计划 tab 触发) -->
        <!-- WBS 编辑弹窗 (封装到 WbsEditDialog 子组件) -->
        <WbsEditDialog
          v-model="wbsDialogVisible"
          :mode="wbsEditDialog.mode"
          :form="wbsEditDialog.form"
          :owners="wbsOwners"
          :all-tasks="wbsFlat"
          :parent-candidates="wbsParentCandidates"
          :saving="wbsEditDialog.loading"
          @save="onSaveWbs"
          @parent-change="onParentChange"
        />

        <el-tab-pane v-if="overview" label="健康度" name="health">
          <el-card>
            <p style="margin-bottom: 16px; color: #606266">
              当前健康度:
              <el-tag
                v-if="overview.project.health"
                :color="overview.project.health.colorHex"
                effect="dark"
              >{{ overview.project.health.name }} ({{ overview.project.health.code }})</el-tag>
            </p>
            <el-radio-group v-model="newHealth" :disabled="healthUpdating" @change="changeHealth">
              <el-radio-button value="GREEN">正常 🟢</el-radio-button>
              <el-radio-button value="YELLOW">关注 🟡</el-radio-button>
              <el-radio-button value="RED">严重 🔴</el-radio-button>
            </el-radio-group>
            <p style="margin-top: 16px; color: #909399; font-size: 12px">
              切换后自动保存,影响 Dashboard 颜色分布
            </p>
          </el-card>
        </el-tab-pane>

        <!-- Tab 4: ETC (EVM 完工估算) -->
        <el-tab-pane label="ETC" name="etc">
          <el-card>
            <template #header>
              <div style="display: flex; justify-content: space-between; align-items: center">
                <span>完工估算 (Earned Value Management)</span>
                <el-button type="primary" size="small" @click="triggerEvmSnapshot">
                  <el-icon style="margin-right: 4px"><span>↻</span></el-icon>
                  触发快照
                </el-button>
              </div>
            </template>

            <el-alert
              v-if="!evmLatest && !evmLoading"
              title="该项目暂无 EVM 快照,点击右上角触发首次快照"
              type="info"
              :closable="false"
              style="margin-bottom: 16px"
            />

            <el-descriptions v-if="evmLatest" :column="3" border>
              <el-descriptions-item label="快照日期">{{ evmLatest.snapshotDate }}</el-descriptions-item>
              <el-descriptions-item label="版本">v{{ evmLatest.version }}</el-descriptions-item>
              <el-descriptions-item label="原因">{{ evmLatest.reason }}</el-descriptions-item>

              <el-descriptions-item label="BAC (计划预算)">¥ {{ evmLatest.bac.toLocaleString() }}</el-descriptions-item>
              <el-descriptions-item label="PV (计划值)">¥ {{ evmLatest.pv.toLocaleString() }}</el-descriptions-item>
              <el-descriptions-item label="EV (挣值)">¥ {{ evmLatest.ev.toLocaleString() }}</el-descriptions-item>

              <el-descriptions-item label="AC (实际成本)">¥ {{ evmLatest.ac.toLocaleString() }}</el-descriptions-item>
              <el-descriptions-item label="EAC (完工估算)">¥ {{ evmLatest.eac.toLocaleString() }}</el-descriptions-item>
              <el-descriptions-item label="ETC (完工尚需)">
                <strong style="color: #E6A23C">¥ {{ evmLatest.etc.toLocaleString() }}</strong>
              </el-descriptions-item>

              <el-descriptions-item label="VAC (完工偏差)">
                <el-tag :type="evmLatest.vac >= 0 ? 'success' : 'danger'" effect="dark">
                  ¥ {{ evmLatest.vac.toLocaleString() }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="CPI (成本绩效)">
                <el-tag :color="cpiColor(evmLatest.cpi)" effect="dark">
                  {{ evmLatest.cpi.toFixed(3) }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="SPI (进度绩效)">
                <el-tag :color="cpiColor(evmLatest.spi)" effect="dark">
                  {{ evmLatest.spi.toFixed(3) }}
                </el-tag>
              </el-descriptions-item>
            </el-descriptions>

            <el-divider v-if="evmLatest" />
            <div v-if="evmLatest" style="font-size: 12px; color: #909399; line-height: 1.8">
              <p>📌 <strong>CPI ≥ 1</strong>: 实际成本 ≤ 计划 (省)</p>
              <p>📌 <strong>CPI &lt; 1</strong>: 实际成本 > 计划 (超支)</p>
              <p>📌 <strong>SPI ≥ 1</strong>: 进度提前 / 符合计划</p>
              <p>📌 <strong>SPI &lt; 1</strong>: 进度滞后</p>
            </div>
          </el-card>
        </el-tab-pane>

        <!-- Tab 5: 项目燃尽图 -->
        <el-tab-pane label="燃尽图" name="burndown">
          <el-card>
            <template #header>
              <div style="display: flex; justify-content: space-between; align-items: center">
                <span>EVM 燃尽图 (随时间变化)</span>
                <span style="font-size: 12px; color: #909399">共 {{ evmSnapshots.length }} 条快照</span>
              </div>
            </template>

            <v-chart
              v-if="evmSnapshots.length"
              :option="burndownChartOpt"
              style="height: 420px"
            />
            <el-empty
              v-else
              description="该项目暂无 EVM 快照,先去 ETC tab 触发一次"
              :image-size="80"
            />
          </el-card>
        </el-tab-pane>

        <!-- Tab 6: 项目配置 (BU/PL/产品/PM) -->
        <el-tab-pane v-if="overview" label="项目配置" name="config">
          <el-card>
            <template #header>
              <div style="display: flex; justify-content: space-between; align-items: center">
                <span>业务归属 & 项目经理</span>
                <el-button type="primary" size="small" @click="openEditDialog">
                  <el-icon style="margin-right: 4px"><span>✎</span></el-icon>
                  编辑配置
                </el-button>
              </div>
            </template>

            <el-descriptions :column="2" border>
              <el-descriptions-item label="业务单元 (BU)">
                <el-tag v-if="overview.project.bu" effect="plain" type="info">
                  {{ overview.project.bu.name }} ({{ overview.project.bu.code }})
                </el-tag>
                <span v-else style="color: #909399">未设置</span>
              </el-descriptions-item>
              <el-descriptions-item label="产品线 (PL)">
                <el-tag v-if="overview.project.pl" effect="plain" type="info">
                  {{ overview.project.pl.name }} ({{ overview.project.pl.code }})
                </el-tag>
                <span v-else style="color: #909399">未设置</span>
              </el-descriptions-item>
              <el-descriptions-item label="关联产品">
                <template v-if="overview.project.relatedProduct">
                  <el-tag effect="plain" type="success">
                    {{ overview.project.relatedProduct.name }}
                    <span v-if="overview.project.relatedProduct.version" style="margin-left: 4px; opacity: .7">
                      v{{ overview.project.relatedProduct.version }}
                    </span>
                  </el-tag>
                </template>
                <span v-else style="color: #909399">未设置</span>
              </el-descriptions-item>
              <el-descriptions-item label="项目经理">
                <span v-if="overview.project.pmUserName">{{ overview.project.pmUserName }}</span>
                <span v-else style="color: #909399">未指定</span>
              </el-descriptions-item>
            </el-descriptions>

            <el-alert
              type="info"
              :closable="false"
              style="margin-top: 16px"
              title="级联规则"
              description="BU → PL → 关联产品 是父子关系。选择 BU 后,PL 下拉只显示该 BU 下的产品线;选择 PL 后,关联产品下拉只显示该产品线下的产品。"
            />
          </el-card>
        </el-tab-pane>

        <!-- Tab 5: 项目组成员 (V2.3 新增) -->
        <el-tab-pane :label="`项目组成员 (${memberList.length})`" name="members">
          <el-card>
            <template #header>
              <div style="display: flex; justify-content: space-between; align-items: center">
                <span>
                  <el-icon style="margin-right: 4px"><span>👥</span></el-icon>
                  项目组成员
                  <el-tag v-if="memberList.length" type="info" effect="plain" style="margin-left: 8px">
                    {{ memberList.length }} 人
                  </el-tag>
                </span>
                <el-button type="primary" size="small" @click="openAddMemberDialog">
                  <el-icon style="margin-right: 4px"><span>＋</span></el-icon>
                  添加成员
                </el-button>
              </div>
            </template>

            <el-table v-if="memberList.length" :data="memberList" border stripe>
              <el-table-column label="角色" width="120">
                <template #default="{ row }">
                  <el-tag effect="dark">{{ row.role.name }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="姓名" min-width="140">
                <template #default="{ row }">
                  {{ row.memberName }}
                  <el-tag v-if="row.external" type="warning" effect="plain" size="small" style="margin-left: 6px">外部</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="参与开始" prop="joinDate" width="120" />
              <el-table-column label="参与结束" width="120">
                <template #default="{ row }">
                  <span v-if="row.leaveDate">{{ row.leaveDate }}</span>
                  <span v-else style="color: #67c23a">● 仍在项目中</span>
                </template>
              </el-table-column>
              <el-table-column label="投入%" prop="allocationPct" width="80" align="center" />
              <el-table-column label="备注" prop="remark" min-width="120">
                <template #default="{ row }">
                  <span v-if="row.remark" style="color: #606266">{{ row.remark }}</span>
                  <span v-else style="color: #c0c4cc">—</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="140" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button type="primary" size="small" link @click="openEditMemberDialog(row)">编辑</el-button>
                  <el-button type="danger" size="small" link @click="deleteMember(row)">移除</el-button>
                </template>
              </el-table-column>
            </el-table>

            <el-empty v-else description="该项目暂无成员 — 点击右上「添加成员」开始组建项目组" :image-size="100">
              <el-button type="primary" plain @click="openAddMemberDialog">
                <el-icon style="margin-right: 4px"><span>＋</span></el-icon>
                添加第一名成员
              </el-button>
            </el-empty>
          </el-card>
        </el-tab-pane>
      </el-tabs>
    </template>

    <!-- 编辑项目配置 弹窗 -->
    <el-dialog
      v-model="editDialogVisible"
      title="编辑项目配置"
      width="540px"
      :close-on-click-modal="false"
    >
      <el-form label-width="100px" v-loading="editSaving">
        <el-form-item label="业务单元 (BU)">
          <el-select
            v-model="editForm.buId"
            placeholder="请选择业务单元 (BU)"
            clearable
            filterable
            style="width: 100%"
            @change="onBuChange"
          >
            <el-option
              v-for="b in buList"
              :key="b.id"
              :label="`${b.name} (${b.code})`"
              :value="b.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="产品线 (PL)">
          <el-select
            v-model="editForm.plId"
            placeholder="请先选择 BU"
            clearable
            filterable
            :disabled="!editForm.buId"
            style="width: 100%"
            @change="onPlChange"
          >
            <el-option
              v-for="p in filteredPlList"
              :key="p.id"
              :label="`${p.name} (${p.code})`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="关联产品">
          <el-select
            v-model="editForm.relatedProductId"
            placeholder="请先选择 PL"
            clearable
            filterable
            :disabled="!editForm.plId"
            style="width: 100%"
          >
            <el-option
              v-for="r in filteredRpList"
              :key="r.id"
              :label="`${r.name}${r.version ? ' v' + r.version : ''}`"
              :value="r.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="项目经理">
          <el-select
            v-model="editForm.pmUserId"
            placeholder="请选择项目经理"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="u in userList"
              :key="u.id"
              :label="`${u.fullName} (${u.username})`"
              :value="u.id"
            />
          </el-select>
        </el-form-item>

        <el-alert
          type="warning"
          :closable="false"
          title="提示"
          description="保存后,BU/PL/产品的级联关系会在后端二次校验,若数据不一致会返回错误。"
        />
      </el-form>

      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="saveEdit">
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 添加/编辑 项目成员 弹窗 -->
    <el-dialog
      v-model="memberDialog.visible"
      :title="memberDialog.editing ? '编辑成员' : '添加成员'"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form label-width="100px" v-loading="memberDialog.saving">
        <el-form-item label="项目角色" required>
          <el-select v-model="memberDialog.form.roleCode" placeholder="选择角色" style="width: 100%">
            <el-option label="项目经理" value="PM" />
            <el-option label="项目助理" value="ASSISTANT" />
            <el-option label="架构师" value="ARCH" />
            <el-option label="需求分析师" value="BA" />
            <el-option label="开发工程师" value="DEV" />
            <el-option label="测试工程师" value="QA" />
            <el-option label="配置管理员" value="CFG" />
          </el-select>
        </el-form-item>

        <el-form-item label="外部人员">
          <el-switch v-model="memberDialog.form.external" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px">
            客户方/外包人员请开启,只需填姓名
          </span>
        </el-form-item>

        <el-form-item v-if="!memberDialog.form.external" label="系统用户" required>
          <el-select
            v-model="memberDialog.form.userId"
            placeholder="选择系统用户"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="u in userList"
              :key="u.id"
              :label="`${u.fullName} (${u.username})`"
              :value="u.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item v-else label="成员姓名" required>
          <el-input v-model="memberDialog.form.memberName" placeholder="请输入外部人员姓名" maxlength="64" />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="参与开始" required>
              <el-date-picker
                v-model="memberDialog.form.joinDate"
                type="date"
                placeholder="开始日期"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="参与结束">
              <el-date-picker
                v-model="memberDialog.form.leaveDate"
                type="date"
                placeholder="仍在项目中"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="投入比例">
              <el-input-number
                v-model="memberDialog.form.allocationPct"
                :min="0" :max="100"
                controls-position="right"
                style="width: 100%"
              />
              <span style="margin-left: 8px; color: #909399; font-size: 12px">%</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="备注">
          <el-input
            v-model="memberDialog.form.remark"
            type="textarea"
            :rows="2"
            placeholder="选填"
            maxlength="256"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="memberDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="memberDialog.saving" @click="saveMember">
          {{ memberDialog.editing ? '保存' : '添加' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.kpi-card {
  border-radius: 8px;
  padding: 18px 20px;
  color: #fff;
  box-shadow: 0 2px 6px rgba(0,0,0,.05);
}
.kpi-card--blue   { background: linear-gradient(135deg, #409EFF, #2c7be5); }
.kpi-card--green  { background: linear-gradient(135deg, #67C23A, #5daf34); }
.kpi-card--orange { background: linear-gradient(135deg, #E6A23C, #d68910); }
.kpi-card--red    { background: linear-gradient(135deg, #F56C6C, #e04545); }
.kpi-card__label { font-size: 12px; opacity: .85; margin-bottom: 6px; }
.kpi-card__value { font-size: 28px; font-weight: 600; }

.pd-gantt-row { display: flex; align-items: center; min-height: 40px; border-bottom: 1px solid var(--pmo-border); }
.pd-gantt-label { width: 280px; padding: 8px 12px; font-size: 13px; border-right: 1px solid var(--pmo-border); background: #fcfcfc; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.pd-gantt-timeline { position: relative; flex: 1; height: 40px; background: repeating-linear-gradient(to right, transparent 0, transparent 79px, rgba(0,0,0,0.04) 79px, rgba(0,0,0,0.04) 80px); }
.pd-gantt-bar { position: absolute; top: 8px; height: 24px; border-radius: 4px; display: flex; align-items: center; justify-content: flex-end; padding: 0 6px; color: white; font-size: 11px; font-weight: 600; }
.pd-gantt-bar.plan { background: #e6f0ff; border: 1px dashed #909399; opacity: 0.6; }
.pd-gantt-milestone { position: absolute; top: 4px; color: #f56c6c; font-size: 16px; transform: translateX(-50%); z-index: 2; cursor: help; }
/* P3 修复:今日竖线指示器,让进行中项目的"今天"位置一眼可见 */
.pd-gantt-today { position: absolute; top: 0; bottom: 0; width: 2px; background: #f56c6c; opacity: 0.7; z-index: 1; pointer-events: none; }
.pd-gantt-today::before { content: '今日'; position: absolute; top: -16px; left: -12px; font-size: 10px; color: #f56c6c; background: white; padding: 0 2px; border-radius: 2px; }

/* WBS Dialog 手动样式 (方案 A) */
.wbs-dialog-overlay { position: fixed; inset: 0; z-index: 2005; background: rgba(0, 0, 0, 0.5); display: flex; align-items: center; justify-content: center; padding: 16px; backdrop-filter: blur(2px); }
.wbs-dialog {
  background: var(--pmo-card, #ffffff);
  border-radius: 10px;
  width: 820px;
  max-width: 100%;
  max-height: calc(100vh - 32px);
  display: flex;
  flex-direction: column;
  box-shadow: 0 12px 48px 8px rgba(0, 0, 0, 0.18), 0 0 1px rgba(0, 0, 0, 0.08);
  overflow: hidden;
  animation: wbsDlgIn 0.18s ease-out;
}
@keyframes wbsDlgIn {
  from { transform: translateY(-12px) scale(0.98); opacity: 0; }
  to   { transform: translateY(0) scale(1); opacity: 1; }
}
.wbs-dialog__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid var(--pmo-border, #e4e7ed);
  background: linear-gradient(135deg, #f8fbff, #ffffff);
}
.wbs-dialog__title {
  font-size: 17px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}
.wbs-dialog__title::before {
  content: '';
  display: inline-block;
  width: 4px;
  height: 18px;
  background: linear-gradient(180deg, #409EFF, #2c7be5);
  border-radius: 2px;
}
.wbs-dialog__close {
  background: none;
  border: 0;
  font-size: 24px;
  line-height: 1;
  color: #909399;
  cursor: pointer;
  padding: 0 4px;
  transition: color 0.15s, transform 0.15s;
}
.wbs-dialog__close:hover { color: #F56C6C; transform: rotate(90deg); }
.wbs-dialog__body { padding: 20px 24px; overflow: auto; flex: 1 1 auto; background: #fafbfc; }
.wbs-dialog__body .el-form-item { margin-bottom: 18px; }
.wbs-dialog__body .el-form-item__label { font-weight: 500; color: #606266; }
.wbs-dialog__footer {
  padding: 14px 24px;
  border-top: 1px solid var(--pmo-border, #e4e7ed);
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  background: #fcfcfd;
}
.dlg-fade-enter-active, .dlg-fade-leave-active { transition: opacity 0.2s ease; }
.dlg-fade-enter-from, .dlg-fade-leave-to { opacity: 0; }
</style>