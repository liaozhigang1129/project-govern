<script setup lang="ts">
import { onMounted, ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api, {
  type ProjectCard,
  type BusinessUnit,
  type ProductLine,
  type RelatedProduct,
  type AppUser,
  type ProjectQuery,
} from '@/api/client'

const projects = ref<ProjectCard[]>([])
const loading = ref(false)
const router = useRouter()

// ====== 查询表单 ======
const queryForm = ref<ProjectQuery>({
  buId: undefined,
  plId: undefined,
  pmUserId: undefined,
  planStartFrom: undefined,
  planStartTo: undefined,
  keyword: undefined,
})

// ====== 字典/用户下拉数据 ======
const buList = ref<BusinessUnit[]>([])
const plListAll = ref<ProductLine[]>([])
const rpList = ref<RelatedProduct[]>([])
const userList = ref<AppUser[]>([])

// 级联:选 BU 后,PL 下拉只显示该 BU 下的
const plListFiltered = computed(() => {
  if (!queryForm.value.buId) return plListAll.value
  return plListAll.value.filter((pl) => pl.bu?.id === queryForm.value.buId)
})

// 切换 BU 时清 PL 过滤
watch(
  () => queryForm.value.buId,
  (newBu, oldBu) => {
    if (newBu !== oldBu) {
      queryForm.value.plId = undefined
    }
  },
)

async function loadDictionaries() {
  try {
    const [bus, pls, rps, users] = await Promise.all([
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
    ])
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
  } catch {
    // 非关键路径,静默处理
  }
}

async function load() {
  loading.value = true
  try {
    const params: Record<string, string> = {}
    Object.entries(queryForm.value).forEach(([k, v]) => {
      if (v != null && v !== '') params[k] = String(v)
    })
    projects.value = await api.get<ProjectCard[]>('/projects', { params })
  } catch (e: any) {
    ElMessage.error(e?.message ?? '加载项目列表失败')
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  load()
}

function handleReset() {
  queryForm.value = {
    buId: undefined,
    plId: undefined,
    pmUserId: undefined,
    planStartFrom: undefined,
    planStartTo: undefined,
    keyword: undefined,
  }
  load()
}

function goDetail(p: ProjectCard) {
  router.push({ path: `/projects/${p.id}` })
}

function goConfig(p: ProjectCard) {
  router.push({ path: `/projects/${p.id}`, query: { tab: 'config' } })
}

function goCreate() {
  router.push('/projects/new')
}

// --- 删除项目 ---
const deleteLoadingId = ref<number | null>(null)
async function confirmDelete(p: ProjectCard) {
  await ElMessageBox.confirm(
    `确认删除「${p.code} · ${p.name}」?\n此操作为软删除,历史数据仍可在数据库中查询。`,
    '删除项目',
    {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger',
    },
  ).catch(() => null)
  deleteLoadingId.value = p.id
  try {
    await api.delete(`/projects/${p.id}`)
    ElMessage.success(`已删除「${p.code}」`)
    load()
  } finally {
    deleteLoadingId.value = null
  }
}

onMounted(() => {
  loadDictionaries()
  load()
})
</script>

<template>
  <div class="page">
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>项目列表</span>
          <div>
            <el-button type="primary" @click="goCreate">+ 新建项目</el-button>
            <el-button @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <!-- 查询条件 -->
      <el-form :model="queryForm" inline label-width="auto" class="project-query-form">
        <el-form-item label="关键字">
          <el-input
            v-model="queryForm.keyword"
            placeholder="编号 / 名称"
            clearable
            style="width: 180px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>

        <el-form-item label="业务单元 (BU)">
          <el-select v-model="queryForm.buId" placeholder="全部 BU" clearable filterable style="width: 160px">
            <el-option v-for="b in buList" :key="b.id" :label="b.name" :value="b.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="产品线 (PL)">
          <el-select
            v-model="queryForm.plId"
            :placeholder="queryForm.buId ? '全部 PL' : '请先选 BU'"
            clearable
            filterable
            :disabled="!queryForm.buId"
            style="width: 160px"
          >
            <el-option v-for="p in plListFiltered" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="项目经理">
          <el-select
            v-model="queryForm.pmUserId"
            placeholder="全部 PM"
            clearable
            filterable
            style="width: 180px"
          >
            <el-option v-for="u in userList" :key="u.id" :label="u.fullName" :value="u.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="开始时间">
          <el-date-picker
            v-model="queryForm.planStartFrom"
            type="date"
            placeholder="起始日期"
            value-format="YYYY-MM-DD"
            style="width: 150px"
          />
          <span style="margin: 0 4px; color: #909399">至</span>
          <el-date-picker
            v-model="queryForm.planStartTo"
            type="date"
            placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 150px"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 项目列表 -->
      <el-table v-loading="loading" :data="projects" stripe @row-click="goDetail" style="cursor: pointer">
        <el-table-column prop="code" label="编号" width="160" />
        <el-table-column prop="name" label="名称" min-width="200" />
        <el-table-column label="BU" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.bu" effect="plain" type="info" size="small">
              {{ row.bu.name }}
            </el-tag>
            <span v-else style="color: #c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column label="PL" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.pl" effect="plain" type="info" size="small">
              {{ row.pl.name }}
            </el-tag>
            <span v-else style="color: #c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column label="关联产品" width="140">
          <template #default="{ row }">
            <el-tag v-if="row.relatedProduct" effect="plain" type="success" size="small">
              {{ row.relatedProduct.name }}
            </el-tag>
            <span v-else style="color: #c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column label="项目经理" width="110">
          <template #default="{ row }">
            <span v-if="row.pmUserName">{{ row.pmUserName }}</span>
            <span v-else style="color: #c0c4cc">未指定</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            {{ row.type?.name ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status?.code === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status?.name ?? '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="健康度" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.health" :color="row.health.colorHex" effect="dark">
              {{ row.health.name }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="160">
          <template #default="{ row }">
            <el-progress :percentage="row.progressPct" :status="row.progressPct >= 100 ? 'success' : ''" />
          </template>
        </el-table-column>
        <el-table-column prop="planEndDate" label="计划结束" width="120" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click.stop="goConfig(row)">配置</el-button>
            <el-button
              size="small"
              link
              type="danger"
              :loading="deleteLoadingId === row.id"
              @click.stop="confirmDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && projects.length === 0" description="暂无项目" />
    </el-card>
  </div>
</template>

<style scoped>
.project-query-form {
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--pmo-border, #ebeef5);
}
.project-query-form .el-form-item {
  margin-bottom: 8px;
}
</style>
