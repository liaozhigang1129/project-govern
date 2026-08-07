<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Bell, Box, Calendar, Check, ChatDotRound, DataBoard, DataLine,
  Document, Flag, Histogram, House, List, MagicStick, Menu, Money, Moon,
  OfficeBuilding, Setting, Timer, Tools, TrendCharts, User, UserFilled, Warning,
} from '@element-plus/icons-vue'
import NotificationCenter from '@/components/NotificationCenter.vue'
import { useAuthStore } from '@/stores/auth'
import api, { type MyRolesResponse } from '@/api/client'
import { roleMenuApi } from '@/api/roleMenus'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const active = ref('/')

// ============================================================
// L1-3: 拉一次当前用户的可见菜单 code, 用于过滤左侧菜单
//  失败/未登录 → null (走原 role-based 兜底)
// ============================================================
const myMenuCodes = ref<Set<string> | null>(null)

async function loadMyMenuCodes() {
  if (!auth.token) { myMenuCodes.value = null; return }
  try {
    // 1) 拿到当前用户的全部角色 ID (含主角色 + 兼任)
    const me = await api.get<MyRolesResponse>('/auth/me/roles')
    if (!me.roleIds || me.roleIds.length === 0) {
      myMenuCodes.value = new Set()
      return
    }
    // 2) 拿到这些角色合集下可见的菜单 code
    const codes = await roleMenuApi.myVisibleMenuCodes(me.roleIds)
    myMenuCodes.value = new Set(codes)
  } catch {
    // 后端未起 / 接口 403 → 保留静态菜单兜底
    myMenuCodes.value = null
  }
}

onMounted(async () => {
  auth.restore()
  await loadMyMenuCodes()
})

function logout() {
  auth.logout()
  ElMessage.success('已登出')
  router.push('/login')
}

// 菜单项 — 支持二级 (children)
//   业务合并规则: 工时相关 4 个菜单 (工时周报 / 工时审批 / 人员负载 / 工时费率)
//   合并到 "工时管理" 二级菜单下,方便在 PMO/PM 视角下一次性跳转
//   → 取消顶层的 timesheets/timesheets-approvals/workload/admin-hourly-rates 单项入口
//   → 仍保留路由 /timesheets, /timesheets/approvals, /workload, /admin/hourly-rates
//     (只是入口放到二级,已收藏的书签不受影响)
//
// L1-3 配套: 每个 MenuItem 多一个 `code` 字段 (后端 sys_menu.code),
//   用于和 /api/role-menus/mine 返回的 code 集合做匹配, 决定该项是否展示.
type MenuItem = {
  path?: string
  label: string
  icon?: any
  code?: string                  // L1-3 授权 key (与 sys_menu.code 对齐)
  roles?: string[]
  children?: MenuItem[]
}

const menuItems: MenuItem[] = [
  { path: '/', label: 'Dashboard', icon: House, code: 'DASHBOARD' },
  { path: '/projects', label: '项目', icon: List, code: 'PROJECT_LIST' },
  { path: '/initiations', label: '立项审批', icon: Setting, code: 'INITIATION_LIST' },
  // 工时管理 (二级) — 工时周报 / 工时审批 / 人员负载 / 工时费率 / 考勤同步 / 钉钉请休假 合并
  // V4.30 考勤 & 钉钉请休假统一并入工时管理下,与"工时周报/工时审批"业务关联紧密
  { label: '工时管理', icon: Calendar, code: 'TIMESHEET_MGMT', children: [
      { path: '/timesheets',          label: '工时周报',     icon: Calendar,    code: 'TIMESHEET' },
      { path: '/timesheets/approvals', label: '工时审批',     icon: Check,
        roles: ['PMO_ADMIN', 'ADMIN', 'EXEC'],                        code: 'TIMESHEET_APPROVE' },
      { path: '/workload',            label: '人员负载',     icon: DataLine,    code: 'WORKLOAD' },
      { path: '/admin/hourly-rates',  label: '工时费率',     icon: Money,
        roles: ['PMO_ADMIN', 'ADMIN'],                                  code: 'HOURLY_RATE' },
      { path: '/timesheets/attendance',     label: '考勤同步',   icon: Timer,
        roles: ['PMO_ADMIN', 'ADMIN'],                                  code: 'DINGTALK_ATTENDANCE' },
      { path: '/timesheets/dingtalk-leaves', label: '钉钉请休假', icon: Document,
        roles: ['PMO_ADMIN', 'ADMIN'],                                  code: 'DINGTALK_LEAVE' },
    ] },
  { path: '/gantt', label: '甘特图', icon: Calendar, code: 'GANTT' },
  { path: '/milestones/analysis', label: '里程碑分析', icon: Flag, code: 'MILESTONE_ANALYSIS' },
  { path: '/milestones/ai-advisor', label: 'AI 里程碑预警', icon: MagicStick,
    roles: ['PMO_ADMIN', 'ADMIN', 'EXEC', 'DEPT_LEAD', 'PM'], code: 'MILESTONE_AI' },
  // 成本中心 (二级) — 工时成本核算 + 多维成本看板
  { label: '成本中心', icon: Money, code: 'COST_MGMT', children: [
      { path: '/cost/user-month', label: '工时成本核算', icon: Histogram,
        roles: ['PMO_ADMIN', 'ADMIN', 'EXEC', 'PM', 'DEPT_LEAD'], code: 'COST_USER_MONTH' },
      { path: '/cost/dashboard',  label: '多维成本看板', icon: DataBoard,
        roles: ['PMO_ADMIN', 'ADMIN', 'EXEC', 'PM', 'DEPT_LEAD'], code: 'COST_DASHBOARD' },
    ] },
  // 商机漏斗 (一级)
  { path: '/opportunity-funnel', label: '商机漏斗', icon: TrendCharts,
    roles: ['PMO_ADMIN', 'ADMIN', 'EXEC', 'DEPT_LEAD', 'PM', 'SR', 'AR', 'FR'], code: 'OPPORTUNITY_FUNNEL' },
  // 资源管理 (二级) — 资源管道
  { label: '资源管理', icon: Box, code: 'RESOURCE_MGMT', children: [
      { path: '/resource-pipeline', label: '资源管道', icon: Box, code: 'RESOURCE_PIPELINE' },
    ] },
  { label: '风险管理', icon: Warning, code: 'RISK_MGMT', children: [
      { path: '/risks', label: '风险列表', icon: List, code: 'RISK_LIST' },
      { path: '/risks/matrix', label: '风险矩阵', icon: Histogram, code: 'RISK_MATRIX' },
      { path: '/risks/health', label: '健康度看板', icon: DataLine, code: 'RISK_HEALTH' },
    ] },
  { path: '/im-bindings', label: 'IM 绑定', icon: ChatDotRound, code: 'IM_BINDING' },
  { path: '/im-quiet-hours', label: '勿扰时段', icon: Moon, code: 'IM_QUIET_HOURS' },

  // 系统管理 (二级) — 仅 PMO_ADMIN / ADMIN 能看到
  // V4.30 钉钉请休假已挪到"工时管理"下,这里不再列
  {
    label: '系统管理', icon: Tools, roles: ['PMO_ADMIN', 'ADMIN'],
    code: 'SYSTEM_MGMT',
    children: [
      { path: '/admin/users', label: '用户管理', icon: User,           code: 'USER_MGMT' },
      { path: '/admin/roles', label: '角色管理', icon: UserFilled,     code: 'ROLE_MGMT' },
      { path: '/admin/menus', label: '菜单管理', icon: Menu,           code: 'MENU_MGMT' },
      { path: '/admin/departments', label: '部门管理', icon: OfficeBuilding, code: 'DEPT_MGMT' },
      { path: '/admin/system-config', label: '系统参数', icon: Tools,  code: 'SYS_CONFIG' },
      { path: '/admin/dingtalk-sync-log', label: '同步日志', icon: Document, code: 'DINGTALK_SYNC_LOG' },
      { path: '/audit-logs', label: '审计日志', icon: Document,        code: 'AUDIT_LOG' },
    ],
  },
]

// 按角色 + (可选) L1-3 授权 code 双重过滤
function visibleItem(m: MenuItem, role: string): boolean {
  // 1) 角色兜底 (L1-3 接口未起时仍能跑)
  if (m.roles && !m.roles.includes(role)) return false
  // 2) L1-3 授权: 已加载 myMenuCodes 时, 必须 code 在集合里
  if (myMenuCodes.value && m.code && !myMenuCodes.value.has(m.code)) return false
  // 3) 有子项: 任意一个子项可见, 父项才显示
  if (m.children) return m.children.some(c => visibleItem(c, role))
  return true
}

const visibleMenuItems = computed<MenuItem[]>(() => {
  const role = (auth.user as any)?.role || ''
  return menuItems.filter(m => visibleItem(m, role))
})

// 当前路由是否在某个分组下 (用于 el-sub-menu 默认展开)
const activeTopPath = computed(() => {
  const p = route.path
  if (p.startsWith('/admin/') || p === '/audit-logs') return '/admin'
  // 工时相关路由(任意) → 展开 "工时管理" 分组
  if (p === '/timesheets' || p.startsWith('/timesheets/') ||
      p === '/workload'   || p.startsWith('/workload/') ||
      p === '/admin/hourly-rates') {
    return '工时管理'
  }
  // V4.30 兼容: 老路径 /admin/dingtalk-leaves 仍展开"工时管理"分组
  if (p === '/admin/dingtalk-leaves') {
    return '工时管理'
  }
  return p
})
</script>

<template>
  <el-container v-if="auth.token" style="height: 100vh">
    <el-aside width="220px" style="background: #001529">
      <div style="color: white; padding: 16px; font-size: 18px; font-weight: 600">
        🏗 PMO 治理
      </div>
      <el-menu
        :default-active="route.path"
        :default-openeds="[activeTopPath]"
        background-color="#001529"
        text-color="#bfcbd9"
        active-text-color="#409eff"
        router
      >
        <template v-for="i in visibleMenuItems" :key="i.path ?? i.label">
          <!-- 二级菜单 -->
          <el-sub-menu v-if="i.children?.length" :index="i.path ?? i.label">
            <template #title>
              <el-icon><component :is="i.icon" /></el-icon>
              <span>{{ i.label }}</span>
            </template>
            <el-menu-item
              v-for="c in i.children"
              :key="c.path"
              :index="c.path!"
            >
              <el-icon><component :is="c.icon" /></el-icon>
              <span>{{ c.label }}</span>
            </el-menu-item>
          </el-sub-menu>
          <!-- 一级菜单 -->
          <el-menu-item v-else :index="i.path!">
            <el-icon><component :is="i.icon" /></el-icon>
            <span>{{ i.label }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header
        style="background: white; border-bottom: 1px solid var(--pmo-border);
               display: flex; align-items: center; justify-content: space-between"
      >
        <div style="font-size: 16px">{{ route.meta?.title ?? '' }}</div>
        <div style="display: flex; align-items: center; gap: 12px">
          <NotificationCenter />
          <el-dropdown @command="(c: string) => c === 'logout' && logout()">
          <span style="cursor: pointer; display: flex; align-items: center; gap: 8px">
            <el-icon><User /></el-icon>
            {{ auth.user?.fullName ?? '未登录' }}
            <span style="color: #909399; font-size: 12px">
              ({{ auth.user?.role }})
            </span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main style="padding: 0">
        <RouterView v-slot="{ Component }">
          <transition name="fade">
            <component :is="Component" />
          </transition>
        </RouterView>
      </el-main>
    </el-container>
  </el-container>
  <RouterView v-else />
</template>

<style scoped>
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
/* 二级菜单缩进更明显 */
:deep(.el-menu .el-menu-item) { font-size: 13px; }
</style>
