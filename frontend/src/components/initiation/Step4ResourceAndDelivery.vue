<template>
  <!-- Step 4 — 交付策略 + 资源派遣计划 -->
  <el-row :gutter="16">
    <el-col :span="14">
      <el-card shadow="never">
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center">
            <span style="font-weight: 600">👥 资源派遣计划 (从系统选人)</span>
            <el-button type="primary" :icon="Plus" size="small" @click="addRow">添加</el-button>
          </div>
        </template>

        <el-alert
          type="info"
          :closable="false"
          style="margin-bottom: 12px"
          title="从「人员」下拉选择系统用户:userId/fullName/部门角色/时薪自动带过来;也支持「按角色」批量派遣(留空人员)。"
        />

        <el-table :data="rows" size="small" border>
          <!-- ① 选人(联动系统人员) -->
          <el-table-column label="人员 (系统)" min-width="220">
            <template #default="{ row }">
              <el-select
                v-model="row.userId"
                placeholder="搜索姓名/工号…"
                filterable
                clearable
                remote
                :remote-method="searchUsers"
                :loading="userLoading"
                style="width: 100%"
                size="small"
                @change="onUserPicked(row)"
              >
                <el-option
                  v-for="u in userOptions"
                  :key="u.id"
                  :value="u.id"
                  :label="`${u.fullName} (${u.username})${u.jobTitle ? ' · ' + u.jobTitle : ''}`"
                >
                  <div style="display: flex; justify-content: space-between; gap: 8px">
                    <span>
                      {{ u.fullName }}
                      <small style="color: #909399">{{ u.username }}</small>
                    </span>
                    <small style="color: #909399">
                      {{ u.primaryRoleCode || '—' }}
                      <span v-if="u.defaultHourlyRate">· ¥{{ u.defaultHourlyRate }}/h</span>
                    </small>
                  </div>
                </el-option>
              </el-select>
            </template>
          </el-table-column>

          <!-- ② 角色代码(按角色批量派遣时手动填) -->
          <el-table-column label="角色代码" width="120">
            <template #default="{ row }">
              <el-input
                v-model="row.roleCode"
                size="small"
                placeholder="PM/DEV/QA"
                :disabled="!!row.userId"
              />
            </template>
          </el-table-column>

          <!-- ③ 投入% -->
          <el-table-column label="投入%" width="100">
            <template #default="{ row }">
              <el-input-number
                v-model="row.allocationPct"
                :min="0"
                :max="100"
                :step="10"
                size="small"
                :controls="false"
                style="width: 100%"
              />
            </template>
          </el-table-column>

          <!-- ④ 计划工时 -->
          <el-table-column label="计划工时" width="110">
            <template #default="{ row }">
              <el-input-number
                v-model="row.planHours"
                :min="0"
                :step="8"
                size="small"
                :controls="false"
                style="width: 100%"
              />
            </template>
          </el-table-column>

          <!-- ⑤ 时薪(自动带,允许手改) -->
          <el-table-column label="时薪(¥/h)" width="110">
            <template #default="{ row }">
              <el-input-number
                v-model="row.hourlyRate"
                :min="0"
                :step="20"
                size="small"
                :controls="false"
                style="width: 100%"
              />
            </template>
          </el-table-column>

          <!-- ⑥ 起止 -->
          <el-table-column label="起止" width="220">
            <template #default="{ row }">
              <el-date-picker
                v-model="row.range"
                type="daterange"
                size="small"
                value-format="YYYY-MM-DD"
                start-placeholder="开始"
                end-placeholder="结束"
                style="width: 100%"
                @change="onRangeChange(row, $event)"
              />
            </template>
          </el-table-column>

          <!-- ⑦ 小计 (工时 × 时薪 × 投入%) -->
          <el-table-column label="小计(¥)" width="120" align="right">
            <template #default="{ row }">
              <span style="color: #e6a23c; font-weight: 600">
                {{ formatCost(row.costAmount) }}
              </span>
            </template>
          </el-table-column>

          <el-table-column label="" width="60" align="center">
            <template #default="{ $index }">
              <el-button size="small" link type="danger" :icon="Delete" @click="rows.splice($index, 1)" />
            </template>
          </el-table-column>
        </el-table>

        <div style="margin-top: 12px; color: #909399; font-size: 12px">
          <el-icon><InfoFilled /></el-icon>
          小计 = 计划工时 × 时薪 × 投入%。选人后会自动带入:姓名 / 角色代码 / 时薪。
        </div>
      </el-card>
    </el-col>

    <el-col :span="10">
      <el-card shadow="never" style="position: sticky; top: 16px">
        <template #header>
          <span style="font-weight: 600">💰 成本汇总(自动联动)</span>
        </template>

        <!-- 合同金额:从立项表自动拉 + 允许同步更新到立项 -->
        <div class="cost-row highlight">
          <span>📄 合同金额 (联动立项)</span>
          <div style="display: flex; align-items: center; gap: 6px">
            <el-input-number
              :model-value="contractAmount"
              @update:model-value="onContractChange"
              :min="0"
              :step="10000"
              :precision="2"
              :controls="false"
              size="small"
              style="width: 160px"
            />
            <el-tooltip content="修改后会同步到立项草稿,Step 6 自动重算毛利" placement="top">
              <el-icon style="color: #409eff"><Link /></el-icon>
            </el-tooltip>
          </div>
        </div>

        <el-divider style="margin: 12px 0" />

        <div class="cost-row">
          <span>👥 资源派遣小计 (Step 4)</span>
          <span class="cost-val" style="color: #e6a23c">{{ formatCost(totalResource) }}</span>
        </div>
        <div class="cost-row">
          <span>⚠️ 风险应对小计 (Step 5)</span>
          <span class="cost-val" style="color: #e6a23c">{{ formatCost(riskCost) }}</span>
        </div>
        <div class="cost-row">
          <span>📦 其他成本(差旅/采购)</span>
          <el-input-number
            v-model="otherCost"
            :min="0"
            :step="1000"
            :precision="2"
            size="small"
            :controls="false"
            style="width: 160px"
          />
        </div>

        <el-divider />

        <div class="cost-row total">
          <span>总成本</span>
          <span class="cost-val" style="color: #f56c6c">{{ formatCost(totalCost) }}</span>
        </div>
        <div class="cost-row total">
          <span>毛利</span>
          <span class="cost-val" :style="{ color: margin >= 0 ? '#67C23A' : '#F56C6C' }">
            {{ formatCost(margin) }} ({{ marginPct.toFixed(2) }}%)
          </span>
        </div>

        <el-progress
          :percentage="Math.max(0, Math.min(100, marginPct))"
          :stroke-width="10"
          :color="margin >= 0 ? '#67C23A' : '#F56C6C'"
          style="margin: 10px 0"
        />

        <el-alert
          v-if="!consistencyOk"
          type="warning"
          :closable="false"
          style="margin-top: 12px"
          title="资源计划与 WBS 计划不一致:请确保资源覆盖到所有里程碑时间窗"
        />
        <el-alert
          v-else
          type="success"
          :closable="false"
          style="margin-top: 12px"
          title="资源计划 ✓ 与 WBS 时间窗一致"
        />

        <el-button
          type="success"
          :icon="Check"
          style="margin-top: 12px; width: 100%"
          :loading="saving"
          @click="saveAll"
        >
          保存资源计划
        </el-button>
      </el-card>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
import { computed, ref, watch, onMounted } from 'vue'
import { Plus, Delete, Check, Link, InfoFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import api, { type ResourcePlan, type AppUser } from '@/api/client'

const props = defineProps<{
  initiationId: number
  contractAmount: number // 从立项表联动过来
  riskCost: number // 从 Step 5 联动过来
  planRange: { start?: string; end?: string } | null
}>()
const emit = defineEmits<{
  'total-cost': [n: number]
  'contract-amount': [n: number]
}>()

interface UserOption extends AppUser {
  defaultHourlyRate?: number
  primaryRoleCode?: string
}

// ================== 选人下拉 ==================
const userOptions = ref<UserOption[]>([])
const userLoading = ref(false)
let searchSeq = 0

async function loadAllUsers() {
  // 首次打开:拉全部精简列表 (max 100)
  userLoading.value = true
  try {
    const list = await api.get<UserOption[]>('/users/options')
    userOptions.value = (list as any[]).map((u) => ({
      id: u.id,
      username: u.username,
      fullName: u.fullName,
      primaryRoleCode: u.primaryRoleCode,
      primaryRole: u.primaryRole,
      defaultHourlyRate: u.defaultHourlyRate ?? 0,
    }))
  } catch (e: any) {
    ElMessage.warning('拉取系统人员失败: ' + e.message)
  } finally {
    userLoading.value = false
  }
}

async function searchUsers(q: string) {
  // 远程搜索:走分页接口
  const seq = ++searchSeq
  userLoading.value = true
  try {
    const page = await api.get<any>(`/users?keyword=${encodeURIComponent(q)}&size=50&page=0`)
    if (seq !== searchSeq) return // 旧请求忽略
    userOptions.value = (page.content ?? []).map((u: any) => ({
      id: u.id,
      username: u.username,
      fullName: u.fullName,
      primaryRoleCode: u.primaryRoleCode,
      primaryRole: { id: u.primaryRoleId, code: u.primaryRoleCode, name: u.primaryRoleName },
      defaultHourlyRate: u.defaultHourlyRate ?? 0,
      jobTitle: u.jobTitle,
    }))
  } catch {
    /* 静默 */
  } finally {
    userLoading.value = false
  }
}

function onUserPicked(row: any) {
  // 联动:userName / roleCode / hourlyRate
  const u = userOptions.value.find((x) => x.id === row.userId)
  if (!u) return
  row.userName = u.fullName
  if (u.primaryRoleCode && !row.roleCode) {
    row.roleCode = u.primaryRoleCode
  }
  if ((!row.hourlyRate || row.hourlyRate === 0) && u.defaultHourlyRate) {
    row.hourlyRate = u.defaultHourlyRate
  }
  // 兜底时薪:选不出 user.defaultHourlyRate 时,按 role 默认 200
  if (!row.hourlyRate) row.hourlyRate = 200
}

// ================== 表单数据 ==================
interface RowEx extends ResourcePlan {
  range?: [string, string]
  hourlyRate?: number
}
const rows = ref<RowEx[]>([])
const otherCost = ref(0)
const saving = ref(false)

// 合同金额(本组件也允许改)
const localContract = ref(props.contractAmount ?? 0)
watch(
  () => props.contractAmount,
  (v) => {
    if (v !== localContract.value) localContract.value = v ?? 0
  },
  { immediate: true },
)

function onContractChange(v: number | undefined) {
  localContract.value = v ?? 0
  emit('contract-amount', localContract.value)
  // 立即同步到后端立项表,避免 Step 6 拿到旧值
  api
    .put(`/initiations/${props.initiationId}`, { contractAmount: localContract.value })
    .catch((e: any) => ElMessage.warning('合同金额同步到立项失败: ' + e.message))
}

const totalResource = computed(() => rows.value.reduce((a, r) => a + ((r as any).costAmount ?? 0), 0))
const totalCost = computed(() => totalResource.value + (props.riskCost ?? 0) + (otherCost.value ?? 0))
const margin = computed(() => (localContract.value ?? 0) - totalCost.value)
const marginPct = computed(() => (localContract.value ? (margin.value / localContract.value) * 100 : 0))

const consistencyOk = computed(() => {
  if (!props.planRange?.start || !props.planRange?.end) return true
  return rows.value.every((r) => {
    if (!r.startDate || !r.endDate) return false
    return r.startDate <= props.planRange!.end! && r.endDate >= props.planRange!.start!
  })
})

// 把 costAmount 实时算出来 + 把总成本抛给 Step 6
function recalcCosts() {
  for (const r of rows.value as any[]) {
    const hours = Number(r.planHours ?? 0)
    const rate = Number(r.hourlyRate ?? 0)
    const pct = Number(r.allocationPct ?? 0)
    r.costAmount = Math.round(hours * rate * (pct / 100) * 100) / 100
  }
  emit('total-cost', totalResource.value)
}
watch(rows, recalcCosts, { deep: true, immediate: true })
watch(totalResource, (v) => emit('total-cost', v))

function formatCost(v: number | undefined) {
  if (!v) return '0.00'
  return v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function onRangeChange(row: RowEx, val: [string, string] | null) {
  if (val && val.length === 2) {
    row.startDate = val[0]
    row.endDate = val[1]
  }
}

function addRow() {
  rows.value.push({
    userId: undefined,
    userName: '',
    roleCode: 'DEV',
    allocationPct: 100,
    planHours: 160,
    hourlyRate: 200,
    range: undefined,
  } as any)
}

async function loadExisting() {
  await loadAllUsers()
  try {
    const list = await api.get<ResourcePlan[]>(`/initiations/${props.initiationId}/resource-plans`)
    rows.value = list.map((r: any) => ({
      ...r,
      range: r.startDate && r.endDate ? ([r.startDate, r.endDate] as [string, string]) : undefined,
    }))
    recalcCosts()
  } catch {
    /* first time */
  }
}

async function saveAll() {
  saving.value = true
  try {
    // 1) 同步合同金额到立项
    if (localContract.value !== props.contractAmount) {
      await api
        .put(`/initiations/${props.initiationId}`, { contractAmount: localContract.value })
        .catch((e: any) => ElMessage.warning('合同金额同步失败: ' + e.message))
    }
    // 2) 全量清空(后端新加的 DELETE 无参端点)
    await api
      .delete(`/initiations/${props.initiationId}/resource-plans`)
      .catch((e: any) => console.warn('[Step4] 全量清空失败:', e.message))
    // 3) 写新的
    for (const r of rows.value as any[]) {
      if (!r.userId && !r.roleCode) continue
      await api.post(`/initiations/${props.initiationId}/resource-plans`, {
        userId: r.userId || null,
        userName: r.userName || null,
        roleCode: r.roleCode || null,
        allocationPct: r.allocationPct,
        planHours: r.planHours,
        hourlyRate: r.hourlyRate,
        startDate: r.startDate,
        endDate: r.endDate,
      })
    }
    ElMessage.success(`资源计划已保存 ${rows.value.length} 条 · 合同金额已同步`)
    emit('contract-amount', localContract.value)
  } catch (e: any) {
    ElMessage.error('保存失败: ' + e.message)
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadAllUsers()
})
defineExpose({ loadExisting })
</script>

<style scoped>
.cost-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  font-size: 14px;
  color: #606266;
}
.cost-row.highlight {
  background: #f0f9ff;
  padding: 8px 10px;
  border-radius: 6px;
  margin-bottom: 4px;
}
.cost-row.total {
  font-weight: 600;
  font-size: 15px;
  color: #303133;
}
.cost-val {
  font-family: 'Roboto Mono', monospace;
  color: #303133;
}
</style>
