<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    width="720px"
    :close-on-click-modal="false"
    @update:model-value="onClose"
    @open="onOpen"
  >
    <div class="batch-assign">
      <el-alert
        :title="`共 ${sourceUsers.length} 名用户,已选 ${selectedIds.length} 名`"
        type="info"
        :closable="false"
        style="margin-bottom: 12px"
      />

      <el-input
        v-model="filterText"
        placeholder="搜索姓名/账号/手机"
        clearable
        :prefix-icon="Search"
        style="margin-bottom: 12px"
      />

      <div class="user-list" v-loading="loading">
        <el-checkbox-group v-model="selectedIds">
          <el-table :data="filteredUsers" border max-height="380" stripe>
            <el-table-column width="50" label="选">
              <template #default="{ row }">
                <el-checkbox :value="row.id" />
              </template>
            </el-table-column>
            <el-table-column prop="username" label="账号" min-width="100" />
            <el-table-column prop="fullName" label="姓名" min-width="80" />
            <el-table-column label="当前部门" min-width="120">
              <template #default="{ row }">
                <span v-if="row.department?.name">{{ row.department.name }}</span>
                <el-tag v-else size="small" type="warning">未分配</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="phone" label="手机" min-width="120" />
            <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip />
          </el-table>
        </el-checkbox-group>
      </div>
    </div>

    <template #footer>
      <el-button @click="onClose">取消</el-button>
      <el-button
        type="primary"
        :loading="loading"
        :disabled="selectedIds.length === 0"
        @click="onConfirm"
      >
        确认分配 ({{ selectedIds.length }})
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import type { UserListItem } from '@/api/users'

const props = defineProps<{
  visible: boolean
  title: string
  sourceUsers: UserListItem[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'confirm', payload: { selectedIds: number[] }): void
}>()

const selectedIds = ref<number[]>([])
const filterText = ref('')
const loading = ref(false)

const filteredUsers = computed(() => {
  if (!filterText.value) return props.sourceUsers
  const k = filterText.value.toLowerCase()
  return props.sourceUsers.filter((u) =>
    [u.username, u.fullName, u.phone, u.email]
      .filter(Boolean)
      .some((s) => s!.toLowerCase().includes(k))
  )
})

function onOpen() {
  selectedIds.value = []
  filterText.value = ''
}

function onClose() {
  emit('update:visible', false)
}

function onConfirm() {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请至少选择一名用户')
    return
  }
  emit('confirm', { selectedIds: selectedIds.value })
}

watch(() => props.sourceUsers, () => {
  selectedIds.value = []
})
</script>

<style scoped>
.batch-assign { display: flex; flex-direction: column; }
.user-list { flex: 1; }
:deep(.el-checkbox-group) { display: block; }
</style>
