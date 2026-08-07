<script setup lang="ts">
/**
 * P0-A.1 工时费率管理 (admin)
 *  上半 = 6 角色档默认价
 *  下半 = HourlyRate 全表 + CSV
 *  入口: /admin/hourly-rates
 */
import { onMounted, ref, computed } from "vue"
import { ElMessage, ElMessageBox } from "element-plus"
import {
  Refresh, Search, Edit, Delete, Lock, Plus, Upload, Download, Money,
} from "@element-plus/icons-vue"
import {
  costApi,
  type RoleCostDefaultItem,
  type HourlyRateItem,
  type HourlyRateUpsertBody,
} from "@/api/cost"

const YUAN = "¥"

// ===== 上半: 6 角色档默认价 =====
const roleDefaults = ref<RoleCostDefaultItem[]>([])
const rdLoading = ref(false)
const rdDialog = ref({
  visible: false,
  row: null as RoleCostDefaultItem | null,
  rate: 0,
  submitting: false,
})

async function loadRoleDefaults() {
  rdLoading.value = true
  try {
    roleDefaults.value = await costApi.listRoleDefaults()
  } catch (e: any) {
    ElMessage.error(e?.message ?? "加载角色档失败")
  } finally { rdLoading.value = false }
}

function editRoleDefault(row: RoleCostDefaultItem) {
  rdDialog.value.row = row
  rdDialog.value.rate = row.rate
  rdDialog.value.visible = true
}

async function saveRoleDefault() {
  const r = rdDialog.value.row
  if (!r) return
  if (!rdDialog.value.rate || rdDialog.value.rate <= 0) {
    ElMessage.warning("时薪必须 > 0"); return
  }
  rdDialog.value.submitting = true
  try {
    const updated = await costApi.updateRoleDefault({ code: r.code, rate: rdDialog.value.rate })
    const i = roleDefaults.value.findIndex(x => x.code === r.code)
    if (i >= 0) roleDefaults.value[i] = updated
    ElMessage.success(`已更新 ${r.code} -> ${YUAN}${rdDialog.value.rate}/h`)
    rdDialog.value.visible = false
  } catch (e: any) {
    ElMessage.error(e?.message ?? "保存失败")
  } finally { rdDialog.value.submitting = false }
}

// ===== 下半: HourlyRate =====
const rates = ref<HourlyRateItem[]>([])
const hrLoading = ref(false)
const searchKw = ref("")
const filterUserId = ref<number | null>(null)

const filtered = computed(() => {
  let list = rates.value
  if (filterUserId.value) list = list.filter(x => x.userId === filterUserId.value)
  const kw = searchKw.value.trim().toLowerCase()
  if (kw) {
    list = list.filter(x =>
      (x.roleCode || "").toLowerCase().includes(kw) ||
      (x.userName || "").toLowerCase().includes(kw) ||
      (x.remark || "").toLowerCase().includes(kw),
    )
  }
  return list
})

async function loadRates() {
  hrLoading.value = true
  try {
    rates.value = await costApi.listHourlyRates(
      filterUserId.value ? { userId: filterUserId.value } : {},
    )
  } catch (e: any) {
    ElMessage.error(e?.message ?? "加载费率失败")
  } finally { hrLoading.value = false }
}

const dlg = ref({
  visible: false,
  mode: "create" as "create" | "edit",
  id: 0 as number | null,
  submitting: false,
  form: {
    roleCode: "DEV",
    userId: null as number | null,
    rate: 0,
    effectiveMonth: "",
    endMonth: "",
    remark: "",
  } as HourlyRateUpsertBody,
})

function startCreate() {
  const now = new Date()
  dlg.value.mode = "create"
  dlg.value.id = null
  dlg.value.form = {
    roleCode: "DEV",
    userId: null,
    rate: 0,
    effectiveMonth: `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`,
    endMonth: "",
    remark: "",
  }
  dlg.value.visible = true
}

function startEdit(row: HourlyRateItem) {
  dlg.value.mode = "edit"
  dlg.value.id = row.id
  dlg.value.form = {
    roleCode: row.roleCode,
    userId: row.userId,
    rate: row.rate,
    effectiveMonth: row.effectiveMonth,
    endMonth: row.endMonth ?? "",
    remark: row.remark ?? "",
  }
  dlg.value.visible = true
}

async function save() {
  const f = dlg.value.form
  if (!f.roleCode) { ElMessage.warning("角色必填"); return }
  if (!f.rate || f.rate <= 0) { ElMessage.warning("时薪必须 > 0"); return }
  if (!f.effectiveMonth) { ElMessage.warning("生效月份必填"); return }
  dlg.value.submitting = true
  try {
    const body: HourlyRateUpsertBody = {
      roleCode: f.roleCode,
      userId: f.userId || null,
      rate: f.rate,
      effectiveMonth: f.effectiveMonth,
      endMonth: f.endMonth || null,
      remark: f.remark || undefined,
    }
    if (dlg.value.mode === "create") {
      await costApi.createHourlyRate(body)
      ElMessage.success("已新建")
    } else if (dlg.value.id) {
      await costApi.updateHourlyRate(dlg.value.id, body)
      ElMessage.success("已更新")
    }
    dlg.value.visible = false
    await loadRates()
  } catch (e: any) {
    ElMessage.error(e?.message ?? "保存失败")
  } finally { dlg.value.submitting = false }
}

async function closeRow(row: HourlyRateItem) {
  const now = new Date()
  const atMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`
  try {
    await ElMessageBox.confirm(
      `关停 id=${row.id} (${row.roleCode}${row.userName ? "/" + row.userName : ""})?`,
      "关停", { type: "warning" },
    )
    await costApi.closeHourlyRate(row.id, atMonth)
    ElMessage.success("已关停")
    await loadRates()
  } catch (e: any) {
    if (e !== "cancel" && e?.message) ElMessage.error(e.message)
  }
}

async function deleteRow(row: HourlyRateItem) {
  try {
    await ElMessageBox.confirm(`删除未生效的费率 id=${row.id}?`, "删除", { type: "warning" })
    await costApi.deleteHourlyRate(row.id)
    ElMessage.success("已删除")
    await loadRates()
  } catch (e: any) {
    if (e !== "cancel" && e?.message) ElMessage.error(e.message)
  }
}

const csvInput = ref<HTMLInputElement | null>(null)
const csvLoading = ref(false)

async function downloadTemplate() {
  try {
    const csv = await costApi.downloadCsvTemplate()
    const blob = new Blob([csv], { type: "text/csv;charset=utf-8" })
    const url = URL.createObjectURL(blob)
    const a = document.createElement("a")
    a.href = url
    a.download = `hourly_rate_template_${Date.now()}.csv`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e: any) {
    ElMessage.error(e?.message ?? "下载模板失败")
  }
}

function triggerUpload() { csvInput.value?.click() }

async function onCsvPicked(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  csvLoading.value = true
  try {
    const fd = new FormData()
    fd.append("file", file)
    const res = await costApi.importCsv(fd)
    if (res.failCount === 0) {
      ElMessage.success(`成功导入 ${res.okCount} 行`)
    } else {
      ElMessage.warning(`成功 ${res.okCount} 行,失败 ${res.failCount} 行`)
      console.warn("[Cost CSV import errors]", res.errors)
      await ElMessageBox.alert(res.errors.slice(0, 20).join("\n"), "CSV 部分行失败", {
        type: "warning", confirmButtonText: "我知道了",
      })
    }
    await loadRates()
  } catch (e: any) {
    ElMessage.error(e?.message ?? "上传失败")
  } finally {
    csvLoading.value = false
    target.value = ""
  }
}

function fmtRange(row: HourlyRateItem): string {
  return row.endMonth ? `${row.effectiveMonth} ~ ${row.endMonth}` : `${row.effectiveMonth} 起`
}

function sourceTag(row: HourlyRateItem): { type: any; label: string } {
  if (row.userId) return { type: "danger", label: "USER_OVERRIDE" }
  return { type: "warning", label: "ROLE_OVERRIDE" }
}

function scopeLabel(row: HourlyRateItem): string {
  return row.userId ? `${row.userName ?? "?"} (id=${row.userId})` : "全员"
}

onMounted(() => {
  loadRoleDefaults()
  loadRates()
})
</script>
<template>
  <div style="padding: 16px">
    <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px">
      <h2 style="margin: 0">工时费率</h2>
      <el-button :icon="Refresh" @click="loadRoleDefaults(); loadRates()" plain>刷新</el-button>
    </div>

    <el-card shadow="never" style="margin-bottom: 16px">
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between">
          <span style="font-weight: 600">角色档默认时薪 (兜底第 3 级)</span>
          <el-tag size="small" type="info">优先级:单人 override &gt; 角色档调价 &gt; 这里</el-tag>
        </div>
      </template>
      <el-table v-loading="rdLoading" :data="roleDefaults" border stripe style="width: 100%" empty-text="无">
        <el-table-column prop="sortOrder" label="序" width="60" />
        <el-table-column label="角色代码" width="180">
          <template #default="{ row }">
            <code style="background: #f0f4f8; padding: 2px 6px; border-radius: 4px; font-size: 12px">{{ row.code }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="角色名" min-width="160" />
        <el-table-column label="默认时薪 (元/h)" width="180">
          <template #default="{ row }">
            <span style="color: #67c23a; font-weight: 600">{{ YUAN }}{{ row.rate.toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button :icon="Edit" size="small" type="primary" link @click="editRoleDefault(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between">
          <span style="font-weight: 600">时段费率 (单人 override + 角色档调价)</span>
          <div style="display: flex; gap: 8px">
            <el-button :icon="Download" size="small" @click="downloadTemplate">模板</el-button>
            <el-button :icon="Upload" size="small" type="success" :loading="csvLoading" @click="triggerUpload">导入 CSV</el-button>
            <input ref="csvInput" type="file" accept=".csv" style="display: none" @change="onCsvPicked" />
            <el-button :icon="Plus" size="small" type="primary" @click="startCreate">新建</el-button>
          </div>
        </div>
      </template>

      <div style="display: flex; gap: 8px; margin-bottom: 12px">
        <el-input v-model="searchKw" placeholder="搜索 角色 / 姓名 / 备注" :prefix-icon="Search" clearable style="width: 280px" />
        <el-input-number v-model="filterUserId" placeholder="按 userId 过滤" :min="1" controls-position="right" style="width: 200px" clearable />
        <el-button @click="filterUserId = null; searchKw = ''">清空过滤</el-button>
      </div>

      <el-alert type="info" :closable="false" style="margin-bottom: 12px">
        4 级兜底链:<b>USER_OVERRIDE &gt; ROLE_OVERRIDE &gt; ROLE_COST_DEFAULT &gt; USER_DEFAULT &gt; 0</b>。
        同一 (userId+roleCode+effMonth) 在 CSV 中二次出现会被合并取最后一行。
      </el-alert>

      <el-table
        v-loading="hrLoading"
        :data="filtered"
        border
        stripe
        style="width: 100%"
        empty-text="无费率记录"
        :default-sort="{ prop: 'effectiveMonth', order: 'descending' }"
      >
        <el-table-column prop="effectiveMonth" label="生效月份" width="120" sortable />
        <el-table-column label="区间" width="220">
          <template #default="{ row }">{{ fmtRange(row) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="160">
          <template #default="{ row }">
            <el-tag :type="sourceTag(row).type" size="small">{{ sourceTag(row).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <code style="background: #f0f4f8; padding: 2px 6px; border-radius: 4px; font-size: 12px">{{ row.roleCode }}</code>
          </template>
        </el-table-column>
        <el-table-column label="作用范围" min-width="160">
          <template #default="{ row }">{{ scopeLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="时薪" width="120">
          <template #default="{ row }">
            <span style="color: #67c23a; font-weight: 600">{{ YUAN }}{{ row.rate.toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button :icon="Edit" size="small" type="primary" link @click="startEdit(row)">编辑</el-button>
            <el-button :icon="Lock" size="small" type="warning" link @click="closeRow(row)">关停</el-button>
            <el-button :icon="Delete" size="small" type="danger" link @click="deleteRow(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="rdDialog.visible" :title="'编辑角色档 ' + (rdDialog.row?.code || '')" width="480px">
      <el-form v-if="rdDialog.row" label-width="100px">
        <el-form-item label="角色">
          <code style="background: #f0f4f8; padding: 4px 8px; border-radius: 4px">{{ rdDialog.row.code }}</code>
          <span style="margin-left: 8px; color: #909399">{{ rdDialog.row.name }}</span>
        </el-form-item>
        <el-form-item label="默认时薪">
          <el-input-number v-model="rdDialog.rate" :min="0.01" :precision="2" :step="10" style="width: 200px" />
          <span style="margin-left: 8px; color: #909399">元/小时</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rdDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="rdDialog.submitting" :icon="Money" @click="saveRoleDefault">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="dlg.visible"
      :title="dlg.mode === 'create' ? '新建费率' : '编辑费率 #' + dlg.id"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form label-width="100px">
        <el-form-item label="角色">
          <el-input v-model="dlg.form.roleCode" placeholder="例如 DEV / PM / TEST" />
        </el-form-item>
        <el-form-item label="单人 override">
          <el-input-number v-model="dlg.form.userId" :min="1" controls-position="right" placeholder="留空 = 角色档调价" style="width: 200px" clearable />
          <span style="margin-left: 8px; color: #909399">为空 = 角色档</span>
        </el-form-item>
        <el-form-item label="时薪 (元/h)">
          <el-input-number v-model="dlg.form.rate" :min="0.01" :precision="2" :step="10" style="width: 200px" />
        </el-form-item>
        <el-form-item label="生效月份">
          <el-input v-model="dlg.form.effectiveMonth" placeholder="YYYY-MM" />
        </el-form-item>
        <el-form-item label="失效月份">
          <el-input v-model="dlg.form.endMonth" placeholder="YYYY-MM, 留空 = 仍生效" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dlg.form.remark" placeholder="例如 6月全员调薪 5%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="dlg.submitting" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
code { font-family: 'SF Mono', Menlo, Consolas, monospace; }
</style>
