<template>
  <div class="department-tree">
    <el-input
      v-model="keyword"
      placeholder="搜索部门"
      clearable
      size="small"
      :prefix-icon="Search"
      class="search-input"
    />
    <div class="tree-scroll">
      <el-tree
        ref="treeRef"
        :data="filteredTree"
        :props="treeProps"
        node-key="id"
        :highlight-current="true"
        :default-expand-all="expandAll"
        :expand-on-click-node="false"
        :draggable="draggable"
        :allow-drop="allowDrop"
        :allow-drag="allowDrag"
        empty-text="暂无部门"
        @node-click="onNodeClick"
        @node-drop="onNodeDrop"
      >
        <template #default="{ data }">
          <span
            class="tree-node"
            :class="{ droppable: true }"
            @dragover.prevent="onNodeDragOver"
            @drop.prevent="onNodeExternalDrop($event, data)"
          >
            <el-icon class="node-icon"><OfficeBuilding /></el-icon>
            <span class="node-name" :class="{ disabled: !data.enabled }">{{ data.name }}</span>
            <el-badge
              v-if="showMemberCount"
              :value="data.memberCountTotal ?? data.memberCount ?? 0"
              :max="9999"
              class="node-badge"
            />
            <el-tag v-if="data.dingtalkDeptId" size="small" type="info" effect="plain" class="node-tag">
              DT:{{ data.dingtalkDeptId }}
            </el-tag>
            <el-tag v-else-if="data.orphaned" size="small" type="warning" effect="plain" class="node-tag">
              未挂树
            </el-tag>
          </span>
        </template>
      </el-tree>
    </div>
    <div v-if="$slots.footer" class="tree-footer">
      <slot name="footer" />
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 部门树组件 (V4.14 增强版)
 * - 支持搜索过滤
 * - 支持 memberCount 显示
 * - 支持拖拽用户到部门
 * - 支持 dingtalk 标签
 */
import { computed, ref, watch } from 'vue'
import { Search, OfficeBuilding } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { DepartmentNode } from '@/api/departments'

interface Props {
  data: DepartmentNode[]
  showMemberCount?: boolean
  draggable?: boolean
  expandAll?: boolean
  /** 被拖入 (drop) 校验 */
  validateDrop?: (dept: DepartmentNode) => boolean
}

const props = withDefaults(defineProps<Props>(), {
  showMemberCount: true,
  draggable: false,
  expandAll: false,
  validateDrop: undefined,
})

const emit = defineEmits<{
  (e: 'select', dept: DepartmentNode | null): void
  (e: 'drop', payload: { userId: number; toDept: DepartmentNode }): void
  (e: 'move', payload: { deptId: number; newParentId: number | null }): void
}>()

const keyword = ref('')
const treeRef = ref<any>(null)
const treeProps = {
  children: 'children',
  label: 'name',
  value: 'id',
}

// 树过滤 - 命中节点 + 父链全展开
const filteredTree = computed(() => {
  if (!keyword.value.trim()) return props.data
  return filterTree(props.data, keyword.value.trim().toLowerCase())
})

function filterTree(nodes: DepartmentNode[], kw: string): DepartmentNode[] {
  const out: DepartmentNode[] = []
  for (const n of nodes) {
    const matchSelf =
      n.name.toLowerCase().includes(kw) ||
      (n.code || '').toLowerCase().includes(kw) ||
      String(n.dingtalkDeptId ?? '').includes(kw)
    const matchedChildren = n.children ? filterTree(n.children, kw) : []
    if (matchSelf || matchedChildren.length) {
      out.push({ ...n, children: matchedChildren })
    }
  }
  return out
}

function onNodeClick(data: DepartmentNode) {
  emit('select', data)
}

// 拖拽支持 (预留)
// - 如果是用户拖到部门: 业务事件 (drop with userId)
// - 如果是部门拖到部门: 移动部门 (move with newParentId)
function allowDrag(_node: any) {
  // 部门自身可拖, 用户通过外部 draggable=true 的元素拖入
  return true
}

function allowDrop(_draggingNode: any, _dropNode: any, _type: any) {
  // 简化: 任意位置可放入, 业务校验由 emit 处理
  if (props.validateDrop && _dropNode?.data) {
    return props.validateDrop(_dropNode.data as DepartmentNode)
  }
  return true
}

function onNodeDrop(node: any, target: any, _position: any, _event: any) {
  // element-plus tree 的 drop 事件:
  // node.data 是被拖的节点
  // target.data 是目标节点
  // 这里处理的是"部门移动", 不是"用户到部门"
  emit('move', {
    deptId: node.data.id,
    newParentId: target.data ? target.data.id : null,
  })
}

/** 暴露给父组件: 接收用户拖入 */
function handleExternalDrop(userId: number, deptId: number) {
  const node = findNode(props.data, deptId)
  if (!node) {
    ElMessage.error('目标部门不存在')
    return
  }
  if (props.validateDrop && !props.validateDrop(node)) {
    return
  }
  emit('drop', { userId, toDept: node })
}

function findNode(nodes: DepartmentNode[], id: number): DepartmentNode | null {
  for (const n of nodes) {
    if (n.id === id) return n
    if (n.children) {
      const found = findNode(n.children, id)
      if (found) return found
    }
  }
  return null
}

function onNodeExternalDrop(e: DragEvent, data: DepartmentNode) {
  const userId = Number(e.dataTransfer?.getData('application/x-user-id') || 0)
  if (userId > 0) {
    handleExternalDrop(userId, data.id)
  }
}

function onNodeDragOver(e: DragEvent) {
  if (e.dataTransfer) {
    e.dataTransfer.dropEffect = 'move'
  }
}

defineExpose({ handleExternalDrop, treeRef })
</script>

<style scoped>
.department-tree {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 400px;
}
.search-input {
  margin-bottom: 8px;
}
.tree-scroll {
  flex: 1;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 6px;
  background: var(--el-fill-color-blank);
}
.tree-node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  font-size: 13px;
  padding-right: 8px;
}
.node-icon {
  color: var(--el-color-primary);
  font-size: 14px;
}
.node-name {
  font-weight: 500;
}
.node-name.disabled {
  color: var(--el-text-color-placeholder);
  text-decoration: line-through;
}
.node-badge {
  margin-left: auto;
}
.node-badge :deep(.el-badge__content) {
  background: var(--el-color-success-light-7);
  color: #fff;
}
.node-tag {
  font-size: 10px;
  height: 18px;
  padding: 0 4px;
  margin-left: 4px;
}
.tree-footer {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
}

/* el-tree 节点 hover 时整行可点 */
:deep(.el-tree-node__content):hover {
  background: var(--el-color-primary-light-9) !important;
}
:deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: var(--el-color-primary-light-8) !important;
  font-weight: 600;
}
</style>
