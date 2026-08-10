<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api, {
  type BusinessUnit,
  type ProductLine,
  type RelatedProduct,
  type AppUser,
  type ProjectDetail,
  type MemberRole,
  type ProjectMemberInput,
} from '@/api/client'

// ===== 路由 =====
const router = useRouter()

// ===== 字典/用户下拉数据 =====
const typeList = ref<{ id: number; code: string; name: string }[]>([])
const statusList = ref<{ id: number; code: string; name: string }[]>([])
const buList = ref<BusinessUnit[]>([])
const plListAll = ref<ProductLine[]>([])
const rpList = ref<RelatedProduct[]>([])
const userList = ref<AppUser[]>([])
const roleList = ref<MemberRole[]>([])

// ===== Tab 状态 =====
const activeTab = ref('basic')

// ===== 表单数据 =====
const form = reactive({
  // 必填
  code: '',
  name: '',
  typeCode: '',
  statusCode: '',
  // 业务归属 (BU/PL/产品/PM)
  buId: undefined as number | undefined,
  plId: undefined as number | undefined,
  relatedProductId: undefined as number | undefined,
  pmUserId: undefined as number | undefined,
  // 业务描述
  customer: '',
  description: '',
  background: '',
  goals: '',
  scope: '',
  // 时间 + 预算
  planStartDate: undefined as string | undefined,
  planEndDate: undefined as string | undefined,
  actualStartDate: undefined as string | undefined,
  actualEndDate: undefined as string | undefined,
  planWorkdays: undefined as number | undefined,
  budgetEstimate: undefined as number | undefined,
})

// ===== 项目组成员(随项目一起创建) =====
interface MemberRow extends ProjectMemberInput {
  _key: number // 表格行 key(本地生成,无意义)
  _userLabel?: string // 内部用户显示名(只用于表格展示)
}
let memberKeySeq = 1
const members = ref<MemberRow[]>([])

const submitting = ref(false)

// ===== 级联下拉数据 =====
const filteredPlList = computed(() => {
  if (!form.buId) return plListAll.value
  return plListAll.value.filter((pl) => pl.bu?.id === form.buId)
})
const filteredRpList = computed(() => {
  if (!form.plId) return rpList.value
  return rpList.value.filter((rp) => rp.pl?.id === form.plId)
})

// ===== 加载所有字典 + 用户 =====
async function loadDictionaries() {
  try {
    const [types, statuses, bus, pls, rps, users, roles] = await Promise.all([
      api.get<{ id: number; code: string; name: string }[]>('/dict/project-types'),
      api.get<{ id: number; code: string; name: string }[]>('/dict/project-statuses'),
      api.get<BusinessUnit[]>('/dict/bus'),
      api.get<ProductLine[]>('/dict/pls'),
      api.get<RelatedProduct[]>('/dict/related-products'),
      api.get<
        {
          id: number
          username: string
          fullName: string
          primaryRoleCode: string
          departmentId: number | null
        }[]
      >('/users/options'),
      api.get<MemberRole[]>('/dict/member-roles'),
    ])
    typeList.value = types ?? []
    statusList.value = statuses ?? []
    buList.value = bus ?? []
    plListAll.value = pls ?? []
    rpList.value = rps ?? []
    userList.value = (users ?? []).map((u) => ({
      id: u.id,
      username: u.username,
      fullName: u.fullName,
      primaryRole: { id: 0, code: u.primaryRoleCode, name: u.primaryRoleCode },
      departmentId: u.departmentId,
    }))
    roleList.value = roles ?? []
  } catch (e: any) {
    ElMessage.error(e?.message ?? '字典加载失败')
  }
}

// ===== BU/PL/产品 切换时的级联清空 =====
function onBuChange() {
  form.plId = undefined
  form.relatedProductId = undefined
}
function onPlChange() {
  form.relatedProductId = undefined
}

// ===== 表单校验 =====
const formRef = ref()
const rules = {
  code: [
    { required: true, message: '请输入项目编号', trigger: 'blur' },
    { max: 32, message: '编号最长 32 字符', trigger: 'blur' },
  ],
  name: [
    { required: true, message: '请输入项目名称', trigger: 'blur' },
    { max: 128, message: '名称最长 128 字符', trigger: 'blur' },
  ],
  typeCode: [{ required: true, message: '请选择项目类型', trigger: 'change' }],
  statusCode: [{ required: true, message: '请选择项目状态', trigger: 'change' }],
}

function isValidBuPlRpChain(): boolean {
  if (form.plId && !form.buId) {
    ElMessage.warning('选了产品线就必须先选业务单元 (BU)')
    return false
  }
  if (form.relatedProductId && !form.plId) {
    ElMessage.warning('选了关联产品就必须先选产品线 (PL)')
    return false
  }
  return true
}

// ===== 成员行操作 =====
function addMember() {
  members.value.push({
    _key: memberKeySeq++,
    roleCode: 'DEV', // 默认给「开发工程师」,最常见
    external: false,
    allocationPct: 100,
    joinDate: form.planStartDate ?? new Date().toISOString().slice(0, 10),
  } as MemberRow)
}

async function removeMember(row: MemberRow) {
  try {
    await ElMessageBox.confirm(`确定移除「${row.memberName || '该成员'}」?`, '提示', {
      type: 'warning',
    })
  } catch {
    return
  }
  members.value = members.value.filter((m) => m._key !== row._key)
}

function onMemberUserChange(row: MemberRow) {
  if (row.userId) {
    const u = userList.value.find((x) => x.id === row.userId)
    if (u) row._userLabel = `${u.fullName} (${u.username})`
  } else {
    row._userLabel = undefined
  }
}

function validateMembers(): boolean {
  if (members.value.length === 0) return true // 成员是可选的

  // 校验:内部成员必须有 userId,外部必须有 memberName,所有必须有 joinDate
  for (let i = 0; i < members.value.length; i++) {
    const m = members.value[i]
    const idx = i + 1
    if (!m.roleCode) {
      ElMessage.warning(`第 ${idx} 行:请选择项目角色`)
      return false
    }
    if (!m.external && !m.userId) {
      ElMessage.warning(`第 ${idx} 行:内部成员请选择系统用户`)
      return false
    }
    if (m.external && !m.memberName?.trim()) {
      ElMessage.warning(`第 ${idx} 行:外部人员请填写姓名`)
      return false
    }
    if (!m.joinDate) {
      ElMessage.warning(`第 ${idx} 行:请填写参与开始日期`)
      return false
    }
    if (m.leaveDate && m.joinDate && m.leaveDate < m.joinDate) {
      ElMessage.warning(`第 ${idx} 行:参与结束日期不能早于开始日期`)
      return false
    }
  }
  return true
}

// ===== 提交 =====
async function submit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    // 切到第一个 Tab 让用户看到错误
    activeTab.value = 'basic'
    return
  }
  if (!isValidBuPlRpChain()) {
    activeTab.value = 'attribution'
    return
  }

  // 日期校验
  if (form.planStartDate && form.planEndDate && form.planStartDate > form.planEndDate) {
    ElMessage.warning('计划开始日期不能晚于计划结束日期')
    activeTab.value = 'schedule'
    return
  }
  if (form.actualStartDate && form.actualEndDate && form.actualStartDate > form.actualEndDate) {
    ElMessage.warning('实际开始日期不能晚于实际结束日期')
    activeTab.value = 'schedule'
    return
  }

  if (!validateMembers()) {
    activeTab.value = 'members'
    return
  }

  submitting.value = true
  try {
    // 构造 payload — 字段全可选,空值不传(null) 节省后端处理
    const payload: Record<string, any> = {
      code: form.code.trim(),
      name: form.name.trim(),
      typeCode: form.typeCode,
      statusCode: form.statusCode,
    }
    if (form.buId) payload.buId = form.buId
    if (form.plId) payload.plId = form.plId
    if (form.relatedProductId) payload.relatedProductId = form.relatedProductId
    if (form.pmUserId) payload.pmUserId = form.pmUserId
    if (form.customer?.trim()) payload.customer = form.customer.trim()
    if (form.description?.trim()) payload.description = form.description
    if (form.background?.trim()) payload.background = form.background
    if (form.goals?.trim()) payload.goals = form.goals
    if (form.scope?.trim()) payload.scope = form.scope
    if (form.planStartDate) payload.planStartDate = form.planStartDate
    if (form.planEndDate) payload.planEndDate = form.planEndDate
    if (form.actualStartDate) payload.actualStartDate = form.actualStartDate
    if (form.actualEndDate) payload.actualEndDate = form.actualEndDate
    if (form.planWorkdays != null) payload.planWorkdays = form.planWorkdays
    if (form.budgetEstimate != null) payload.budgetEstimate = form.budgetEstimate

    // ===== 项目组成员(随项目一次性创建) =====
    if (members.value.length > 0) {
      payload.members = members.value.map((m) => {
        const out: Record<string, any> = {
          roleCode: m.roleCode,
          joinDate: m.joinDate,
          allocationPct: m.allocationPct ?? 100,
          external: !!m.external,
        }
        if (m.userId) out.userId = m.userId
        if (m.memberName?.trim()) out.memberName = m.memberName.trim()
        if (m.leaveDate) out.leaveDate = m.leaveDate
        if (m.remark?.trim()) out.remark = m.remark
        return out
      })
    }

    const created = await api.post<ProjectDetail>('/projects', payload)
    const memberInfo = members.value.length > 0 ? `,已添加 ${members.value.length} 名成员` : ''
    ElMessage.success(`项目「${created.name}」已创建${memberInfo}`)
    router.push(`/projects/${created.id}`)
  } catch (e: any) {
    ElMessage.error(e?.message ?? '创建失败')
  } finally {
    submitting.value = false
  }
}

function cancel() {
  router.push('/projects')
}

onMounted(loadDictionaries)
</script>

<template>
  <div class="page">
    <el-page-header @back="cancel" style="margin-bottom: 16px">
      <template #content>
        <span style="font-size: 18px; font-weight: 600">新建项目</span>
      </template>
    </el-page-header>

    <el-alert
      type="info"
      :closable="false"
      style="margin-bottom: 16px"
      title="字段说明"
      description="带 * 为必填。BU → PL → 关联产品 为级联关系,后端会做一致性校验。日期格式为 YYYY-MM-DD。表单已拆分为多个 Tab,可分步填写。"
    />

    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" v-loading="submitting">
      <el-tabs v-model="activeTab" type="border-card">
        <!-- ============ Tab 1: 基本信息 ============ -->
        <el-tab-pane name="basic" label="① 基本信息">
          <el-card header="基本信息" style="margin-bottom: 16px">
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="项目编号" prop="code">
                  <el-input v-model="form.code" placeholder="如: P-2025-001" maxlength="32" show-word-limit />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="项目名称" prop="name">
                  <el-input
                    v-model="form.name"
                    placeholder="请输入项目名称"
                    maxlength="128"
                    show-word-limit
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="项目类型" prop="typeCode">
                  <el-select v-model="form.typeCode" placeholder="请选择项目类型" style="width: 100%">
                    <el-option
                      v-for="t in typeList"
                      :key="t.code"
                      :label="`${t.name} (${t.code})`"
                      :value="t.code"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="项目状态" prop="statusCode">
                  <el-select v-model="form.statusCode" placeholder="请选择项目状态" style="width: 100%">
                    <el-option
                      v-for="s in statusList"
                      :key="s.code"
                      :label="`${s.name} (${s.code})`"
                      :value="s.code"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="客户">
                  <el-input v-model="form.customer" placeholder="选填" maxlength="128" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-card>
        </el-tab-pane>

        <!-- ============ Tab 2: 业务归属 & PM ============ -->
        <el-tab-pane name="attribution" label="② 业务归属 & PM">
          <el-card header="业务归属 & 项目经理" style="margin-bottom: 16px">
            <el-alert
              type="warning"
              :closable="false"
              style="margin-bottom: 16px"
              title="级联规则"
              description="BU → PL → 关联产品 是父子关系。切换上层时,下层选择会自动清空。"
            />
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="业务单元 (BU)">
                  <el-select
                    v-model="form.buId"
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
              </el-col>
              <el-col :span="12">
                <el-form-item label="产品线 (PL)">
                  <el-select
                    v-model="form.plId"
                    placeholder="请先选择 BU"
                    clearable
                    filterable
                    :disabled="!form.buId"
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
              </el-col>
              <el-col :span="12">
                <el-form-item label="关联产品">
                  <el-select
                    v-model="form.relatedProductId"
                    placeholder="请先选择 PL"
                    clearable
                    filterable
                    :disabled="!form.plId"
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
              </el-col>
              <el-col :span="12">
                <el-form-item label="项目经理">
                  <el-select
                    v-model="form.pmUserId"
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
              </el-col>
            </el-row>
          </el-card>
        </el-tab-pane>

        <!-- ============ Tab 3: 时间 & 预算 ============ -->
        <el-tab-pane name="schedule" label="③ 时间 & 预算">
          <el-card header="时间 & 预算" style="margin-bottom: 16px">
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="计划开始">
                  <el-date-picker
                    v-model="form.planStartDate"
                    type="date"
                    placeholder="选填"
                    value-format="YYYY-MM-DD"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="计划结束">
                  <el-date-picker
                    v-model="form.planEndDate"
                    type="date"
                    placeholder="选填"
                    value-format="YYYY-MM-DD"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="实际开始">
                  <el-date-picker
                    v-model="form.actualStartDate"
                    type="date"
                    placeholder="选填"
                    value-format="YYYY-MM-DD"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="实际结束">
                  <el-date-picker
                    v-model="form.actualEndDate"
                    type="date"
                    placeholder="选填"
                    value-format="YYYY-MM-DD"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="计划工时(天)">
                  <el-input-number
                    v-model="form.planWorkdays"
                    :min="0"
                    :max="9999"
                    placeholder="选填"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="预算 (元)">
                  <el-input-number
                    v-model="form.budgetEstimate"
                    :min="0"
                    :precision="2"
                    :step="1000"
                    placeholder="选填"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </el-card>
        </el-tab-pane>

        <!-- ============ Tab 4: 业务描述 ============ -->
        <el-tab-pane name="description" label="④ 业务描述">
          <el-card header="业务描述" style="margin-bottom: 16px">
            <el-form-item label="项目描述">
              <el-input
                v-model="form.description"
                type="textarea"
                :rows="3"
                placeholder="项目的简要描述(选填)"
              />
            </el-form-item>
            <el-form-item label="项目背景">
              <el-input
                v-model="form.background"
                type="textarea"
                :rows="3"
                placeholder="为什么要做这个项目(选填)"
              />
            </el-form-item>
            <el-form-item label="项目目标">
              <el-input v-model="form.goals" type="textarea" :rows="3" placeholder="达成什么目标(选填)" />
            </el-form-item>
            <el-form-item label="项目范围">
              <el-input
                v-model="form.scope"
                type="textarea"
                :rows="3"
                placeholder="做什么 / 不做什么(选填)"
              />
            </el-form-item>
          </el-card>
        </el-tab-pane>

        <!-- ============ Tab 5: 项目组成员 ============ -->
        <el-tab-pane name="members" :label="`⑤ 项目组成员 (${members.length})`">
          <el-card>
            <template #header>
              <div style="display: flex; justify-content: space-between; align-items: center">
                <span>
                  <el-icon style="margin-right: 4px"><span>👥</span></el-icon>
                  项目组成员
                  <el-tag v-if="members.length" type="info" effect="plain" style="margin-left: 8px">
                    {{ members.length }} 人
                  </el-tag>
                </span>
                <el-button type="primary" size="small" @click="addMember">
                  <el-icon style="margin-right: 4px"><span>＋</span></el-icon>
                  添加成员
                </el-button>
              </div>
            </template>

            <el-alert
              type="info"
              :closable="false"
              style="margin-bottom: 12px"
              title="角色说明"
              description="支持的角色:项目经理 / 项目助理 / 架构师 / 需求分析师 / 开发工程师 / 测试工程师 / 配置管理员。成员可为系统内部用户(选 userId)或外部人员(勾选外部+填姓名)。"
            />

            <el-table v-if="members.length" :data="members" border size="default" style="width: 100%">
              <el-table-column label="角色" width="160">
                <template #default="{ row }">
                  <el-select v-model="row.roleCode" placeholder="选择角色" size="small" style="width: 100%">
                    <el-option v-for="r in roleList" :key="r.code" :label="r.name" :value="r.code" />
                  </el-select>
                </template>
              </el-table-column>

              <el-table-column label="成员" min-width="220">
                <template #default="{ row }">
                  <div style="display: flex; flex-direction: column; gap: 4px">
                    <el-select
                      v-if="!row.external"
                      v-model="row.userId"
                      placeholder="选择系统用户"
                      filterable
                      size="small"
                      style="width: 100%"
                      @change="onMemberUserChange(row)"
                    >
                      <el-option
                        v-for="u in userList"
                        :key="u.id"
                        :label="`${u.fullName} (${u.username})`"
                        :value="u.id"
                      />
                    </el-select>
                    <el-input v-else v-model="row.memberName" placeholder="外部人员姓名" size="small" />
                    <el-checkbox v-model="row.external" size="small">外部人员</el-checkbox>
                  </div>
                </template>
              </el-table-column>

              <el-table-column label="参与开始" width="170">
                <template #default="{ row }">
                  <el-date-picker
                    v-model="row.joinDate"
                    type="date"
                    placeholder="开始日期"
                    value-format="YYYY-MM-DD"
                    size="small"
                    style="width: 100%"
                  />
                </template>
              </el-table-column>

              <el-table-column label="参与结束" width="170">
                <template #default="{ row }">
                  <el-date-picker
                    v-model="row.leaveDate"
                    type="date"
                    placeholder="仍在项目中"
                    value-format="YYYY-MM-DD"
                    size="small"
                    style="width: 100%"
                  />
                </template>
              </el-table-column>

              <el-table-column label="投入%" width="90" align="center">
                <template #default="{ row }">
                  <el-input-number
                    v-model="row.allocationPct"
                    :min="0"
                    :max="100"
                    size="small"
                    controls-position="right"
                    style="width: 80px"
                  />
                </template>
              </el-table-column>

              <el-table-column label="备注" min-width="140">
                <template #default="{ row }">
                  <el-input v-model="row.remark" placeholder="选填" size="small" />
                </template>
              </el-table-column>

              <el-table-column label="操作" width="70" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button type="danger" size="small" link @click="removeMember(row)">移除</el-button>
                </template>
              </el-table-column>
            </el-table>

            <el-empty v-else description="暂无成员 — 暂未添加任何项目组成员(选填)" :image-size="80">
              <el-button type="primary" plain @click="addMember">
                <el-icon style="margin-right: 4px"><span>＋</span></el-icon>
                添加第一名成员
              </el-button>
            </el-empty>
          </el-card>
        </el-tab-pane>
      </el-tabs>

      <!-- ============ 操作按钮(始终在底部,Tabs 之外) ============ -->
      <div style="margin-top: 16px; padding: 0 16px">
        <el-button type="primary" :loading="submitting" @click="submit">
          创建项目{{ members.length > 0 ? ` (含 ${members.length} 名成员)` : '' }}
        </el-button>
        <el-button @click="cancel">取消</el-button>
        <span style="margin-left: 12px; color: #909399; font-size: 12px">
          必填校验:项目编号 / 项目名称 / 项目类型 / 项目状态
        </span>
      </div>
    </el-form>
  </div>
</template>
