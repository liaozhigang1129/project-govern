<script setup lang="ts">
/**
 * 通知中心(铃铛 + 抽屉) — P1.5 收尾 + P2-B SSE 实时推送
 *  - 右上角铃铛:显示未读数 badge
 *  - 打开抽屉:分页 + 状态过滤(全部/未读/已读)
 *  - 点击"全部已读" / 单条"标已读"
 *  - 点击通知项:跳转对应资源 + 标已读
 *  - P2-B: 新增 SSE 实时推送,收到事件 → +1 badge + (若抽屉打开)刷新
 */
import { computed, onMounted, ref, watch } from 'vue'
import { Bell } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  type Notification,
  type NotificationStatus,
  CATEGORY_LABEL,
  fetchNotificationPage,
  fetchUnreadCount,
  markRead,
} from '@/api/notification'
import { useNotificationStream } from '@/api/sse'

const drawerOpen = ref(false)
const filter = ref<NotificationStatus | 'ALL'>('ALL')
const page = ref(0)
const size = 20
const total = ref(0)
const rows = ref<Notification[]>([])
const unread = ref(0)
const loading = ref(false)
const connected = ref(false)

async function refresh() {
  loading.value = true
  try {
    const data = await fetchNotificationPage(filter.value, page.value, size)
    rows.value = data.rows
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function refreshUnread() {
  const { count } = await fetchUnreadCount()
  unread.value = count
}

onMounted(() => {
  refreshUnread()
})

/**
 * P2-B: SSE 实时推送
 *  - onMessage: 收到新通知 → badge +1,如果抽屉打开则刷新列表
 *  - onOpen: 标记已连接(右上角小绿点)
 *  - 失败兜底: 连接断开时降级到 30s 轮询,确保不丢消息
 */
let fallbackPoll: number | null = null
useNotificationStream({
  token: () => localStorage.getItem('token'),
  onConnected: () => {
    connected.value = true
    if (fallbackPoll) {
      clearInterval(fallbackPoll)
      fallbackPoll = null
    }
  },
  onOpen: () => {
    connected.value = true
  },
  onMessage: (n) => {
    // 1) badge +1
    unread.value += 1
    // 2) 顶部小提示
    ElMessage({
      message: `${CATEGORY_LABEL[n.category as keyof typeof CATEGORY_LABEL] ?? '新通知'} · ${n.title}`,
      type: 'info',
      duration: 3000,
      showClose: true,
    })
    // 3) 抽屉打开就刷新列表
    if (drawerOpen.value) {
      refresh()
    }
  },
  onError: () => {
    connected.value = false
    // 降级:起 30s 轮询保底(连接恢复后会被清掉)
    if (!fallbackPoll) {
      fallbackPoll = window.setInterval(refreshUnread, 30_000)
    }
  },
})

watch([drawerOpen, filter, page], () => {
  if (drawerOpen.value) refresh()
})

async function markAll() {
  await markRead({ all: true })
  await Promise.all([refresh(), refreshUnread()])
}

async function markOne(n: Notification) {
  if (n.status === 'READ') return
  await markRead({ ids: [n.id] })
  await Promise.all([refresh(), refreshUnread()])
}

function goResource(n: Notification) {
  // TIMESHEET_* → 跳工时审批中心(收件人是 PMO);其他类型暂不跳
  if (n.category.startsWith('TIMESHEET_')) {
    if (n.category === 'TIMESHEET_SUBMIT') {
      // 审批人收到 → 跳独立审批中心
      window.location.href = '/timesheets/approvals'
    } else if (n.category === 'TIMESHEET_REMINDER') {
      // 提交人收到(催办)→ 跳自己工时
      window.location.href = '/timesheets'
    } else {
      // 提交人收到(APPROVED/REJECTED/BATCH_APPROVED)→ 跳自己工时
      window.location.href = '/timesheets'
    }
    return
  }
  if (n.category.startsWith('INITIATION_')) {
    window.location.href = '/initiations'
  }
}

const badge = computed(() => (unread.value > 99 ? '99+' : String(unread.value)))
const hasUnread = computed(() => unread.value > 0)

function fmt(d: string | null) {
  if (!d) return ''
  const x = new Date(d)
  const now = new Date()
  const diffMs = now.getTime() - x.getTime()
  if (diffMs < 60_000) return '刚刚'
  if (diffMs < 3_600_000) return Math.floor(diffMs / 60_000) + '分钟前'
  if (diffMs < 86_400_000) return Math.floor(diffMs / 3_600_000) + '小时前'
  return x.toLocaleDateString()
}
</script>

<template>
  <div class="notif-wrap">
    <el-tooltip
      :content="connected ? '实时连接已建立' : '正在重连 / 已降级为轮询'"
      placement="bottom"
    >
      <span class="conn-dot" :class="{ on: connected }" />
    </el-tooltip>
    <el-badge :value="badge" :hidden="!hasUnread" :max="99" type="danger">
      <el-button text @click="drawerOpen = true" :icon="Bell" circle />
    </el-badge>
  </div>

  <el-drawer
    v-model="drawerOpen"
    title="通知中心"
    direction="rtl"
    size="420px"
  >
    <div class="notif-toolbar">
      <el-radio-group v-model="filter" size="small">
        <el-radio-button value="ALL">全部</el-radio-button>
        <el-radio-button value="UNREAD">未读 ({{ unread }})</el-radio-button>
        <el-radio-button value="READ">已读</el-radio-button>
      </el-radio-group>
      <el-button
        v-if="hasUnread"
        size="small"
        type="primary"
        text
        @click="markAll"
        :disabled="loading"
      >
        全部已读
      </el-button>
    </div>

    <el-empty v-if="!loading && rows.length === 0" description="暂无通知" />

    <el-skeleton v-else-if="loading" :rows="4" animated />

    <ul v-else class="notif-list">
      <li
        v-for="n in rows"
        :key="n.id"
        :class="['notif-item', n.status === 'UNREAD' ? 'is-unread' : '']"
        @click="markOne(n); goResource(n)"
      >
        <div class="notif-head">
          <el-tag size="small" :type="n.category === 'INITIATION_DECIDE' || n.category === 'TIMESHEET_DECIDE' || n.category === 'TIMESHEET_BATCH_APPROVED' ? 'success' : n.category === 'INITIATION_SUPPLEMENT' || n.category === 'TIMESHEET_SUBMIT' || n.category === 'TIMESHEET_REMINDER' ? 'warning' : 'info'">
            {{ CATEGORY_LABEL[n.category] }}
          </el-tag>
          <span class="notif-code">{{ n.resourceCode }}</span>
          <span class="notif-time">{{ fmt(n.createdAt) }}</span>
        </div>
        <div class="notif-title">{{ n.title }}</div>
        <div class="notif-content">{{ n.content }}</div>
      </li>
    </ul>
  </el-drawer>
</template>

<style scoped>
.notif-wrap {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.conn-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #c0c4cc;   /* 灰:未连接 / 降级中 */
  margin-right: 2px;
  transition: background 0.2s;
}
.conn-dot.on { background: #67c23a; }   /* 绿:SSE 已建立 */
.notif-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.notif-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.notif-item {
  padding: 12px 8px;
  border-bottom: 1px solid var(--pmo-border);
  cursor: pointer;
  transition: background 0.15s;
}
.notif-item:hover { background: #f5f7fa; }
.notif-item.is-unread { background: #ecf5ff; }
.notif-item.is-unread:hover { background: #d9ecff; }
.notif-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
  font-size: 12px;
  color: #909399;
}
.notif-code {
  font-family: monospace;
  color: #606266;
}
.notif-time { margin-left: auto; }
.notif-title { font-weight: 600; font-size: 13px; margin: 4px 0; }
.notif-content { font-size: 12px; color: #606266; line-height: 1.4; }
</style>
