import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { roleMenuApi } from '@/api/roleMenus'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('@/views/Login.vue'), meta: { title: '登录' } },
    {
      path: '/',
      component: () => import('@/views/Dashboard.vue'),
      meta: { title: 'Dashboard' },
    },
    {
      path: '/projects',
      component: () => import('@/views/Projects.vue'),
      meta: { title: '项目列表' },
    },
    {
      path: '/projects/new',
      component: () => import('@/views/ProjectCreate.vue'),
      meta: { title: '新建项目' },
    },
    {
      path: '/projects/:id(\\d+)',
      component: () => import('@/views/ProjectDetail.vue'),
      meta: { title: '项目详情' },
    },
    {
      path: '/initiations',
      component: () => import('@/views/Initiations.vue'),
      meta: { title: '立项审批' },
    },
    {
      path: '/audit-logs',
      component: () => import('@/views/AuditLog.vue'),
      meta: { title: '审计日志', roles: ['PMO_ADMIN', 'ADMIN'] },
    },
    {
      path: '/timesheets',
      component: () => import('@/views/Timesheets.vue'),
      meta: { title: '工时周报' },
    },
    {
      path: '/timesheets/approvals',
      component: () => import('@/views/TimesheetApprovals.vue'),
      meta: { title: '工时审批', roles: ['PMO_ADMIN', 'ADMIN', 'EXEC'] },
    },
    {
      // V4.30: 钉钉考勤同步 (限 PMO_ADMIN / ADMIN)
      // 菜单项在"工时管理"二级菜单下,见 App.vue menuItems
      path: '/timesheets/attendance',
      component: () => import('@/views/DingTalkAttendance.vue'),
      meta: { title: '钉钉考勤同步', roles: ['PMO_ADMIN', 'ADMIN'] },
    },
    {
      // V4.30: 钉钉请休假 (从 /admin/dingtalk-leaves 挪过来,放到"工时管理"下)
      path: '/timesheets/dingtalk-leaves',
      component: () => import('@/views/DingTalkLeaves.vue'),
      meta: { title: '钉钉请休假', roles: ['PMO_ADMIN', 'ADMIN'] },
    },
    {
      // V4.30 兼容: 老路径 /admin/dingtalk-leaves 重定向到新路径
      //   → 保留已收藏书签 + 老链接(邮件/IM 消息)可用
      path: '/admin/dingtalk-leaves',
      redirect: '/timesheets/dingtalk-leaves',
    },
    {
      // V4.33 兼容: 老路径 /admin/dingtalk/attendance 重定向到新路径
      //   → 保留已收藏书签 + 老链接可用
      path: '/admin/dingtalk/attendance',
      redirect: '/timesheets/attendance',
    },
    {
      path: '/workload',
      component: () => import('@/views/Workload.vue'),
      meta: { title: '人员负载' },
    },
    {
      path: '/resource-pipeline',
      component: () => import('@/views/ResourcePipelineView.vue'),
      meta: { title: '资源管道' },
    },
    {
      path: '/opportunity-funnel',
      component: () => import('@/views/OpportunityFunnelView.vue'),
      meta: { title: '商机漏斗' },
    },
    {
      path: '/gantt',
      component: () => import('@/views/Gantt.vue'),
      meta: { title: '甘特图' },
    },
    {
      path: '/im-bindings',
      component: () => import('@/views/ImBindings.vue'),
      meta: { title: 'IM 绑定' },
    },
    {
      path: '/im-quiet-hours',
      component: () => import('@/views/ImQuietHours.vue'),
      meta: { title: '勿扰时段' },
    },
    {
      path: '/projects/:id(\\d+)/wbs',
      component: () => import('@/views/WbsView.vue'),
      meta: { title: 'WBS 工作分解' },
    },
    {
      path: '/projects/:id(\\d+)/assignments',
      component: () => import('@/views/WbsAssignmentsView.vue'),
      meta: { title: '资源分配矩阵' },
    },
    {
      path: '/projects/:id(\\d+)/risks',
      component: () => import('@/views/RiskView.vue'),
      meta: { title: '风险管理' },
    },
    {
      // PD-3-12: 全局风险入口 (不带 projectId, 走选择器路由到具体项目)
      path: '/risks',
      component: () => import('@/views/RiskView.vue'),
      meta: { title: '风险管理' },
    },
    {
      // PD-3-12: 全局风险入口 (带 projectId, 直接渲染该项目)
      path: '/risks/:id(\\d+)',
      component: () => import('@/views/RiskView.vue'),
      meta: { title: '风险管理' },
    },
    {
      // P1-里程碑分析: 全局里程碑分析 (主视图 + 下钻)
      path: '/milestones/analysis',
      component: () => import('@/views/MilestoneAnalysis.vue'),
      meta: { title: '里程碑分析', roles: ['PMO_ADMIN', 'ADMIN', 'EXEC', 'DEPT_LEAD', 'PM'] },
    },
    {
      // P5-里程碑 AI 预警: 规则引擎 v1.0, 5 维信号 + 评分 + 一键落地为风险
      path: '/milestones/ai-advisor',
      component: () => import('@/views/MilestoneAiAdvisorView.vue'),
      meta: { title: 'AI 里程碑预警', roles: ['PMO_ADMIN', 'ADMIN', 'EXEC', 'DEPT_LEAD', 'PM'] },
    },
    {
      path: '/risks/matrix',
      component: () => import('@/views/RiskMatrixView.vue'),
      meta: { title: '风险矩阵' },
    },
    {
      path: '/risks/health',
      component: () => import('@/views/RiskHealthView.vue'),
      meta: { title: '风险健康度' },
    },
    {
      // L1-1: 用户管理 (仅 PMO_ADMIN / ADMIN)
      path: '/admin/users',
      component: () => import('@/views/admin/Users.vue'),
      meta: { title: '用户管理', roles: ['PMO_ADMIN', 'ADMIN'] },
    },
    {
      // V4.14: 按组织查看用户 (双栏, 拖拽分配)
      path: '/admin/users/org',
      component: () => import('@/views/admin/UserOrgView.vue'),
      meta: { title: '用户管理 · 按组织', roles: ['PMO_ADMIN', 'ADMIN'] },
    },
    {
      // L1-2: 角色管理 (仅 PMO_ADMIN / ADMIN)
      path: '/admin/roles',
      component: () => import('@/views/admin/Roles.vue'),
      meta: { title: '角色管理', roles: ['PMO_ADMIN', 'ADMIN'] },
    },
    {
      // L1-3: 菜单管理 (仅 PMO_ADMIN / ADMIN)
      path: '/admin/menus',
      component: () => import('@/views/admin/Menus.vue'),
      meta: { title: '菜单管理', roles: ['PMO_ADMIN', 'ADMIN'] },
    },
    {
      // L1-3: 部门管理 (仅 PMO_ADMIN / ADMIN)
      path: '/admin/departments',
      component: () => import('@/views/admin/Departments.vue'),
      meta: { title: '部门管理', roles: ['PMO_ADMIN', 'ADMIN'] },
    },
    {
      // P1-4: 系统参数 (仅 PMO_ADMIN / ADMIN)
      path: '/admin/system-config',
      component: () => import('@/views/admin/SystemConfig.vue'),
      meta: { title: '系统参数', roles: ['PMO_ADMIN', 'ADMIN'] },
    },
    {
      // P0-A.1: 工时费率管理 (仅 PMO_ADMIN / ADMIN)
      path: '/admin/hourly-rates',
      component: () => import('@/views/admin/HourlyRateAdmin.vue'),
      meta: { title: '工时费率', roles: ['PMO_ADMIN', 'ADMIN'] },
    },
    {
      // 钉钉通讯录同步日志 (仅 PMO_ADMIN / ADMIN)
      // 详见 DingTalkSyncLog.vue — 支持 ?logId=xx 跳转聚焦
      path: '/admin/dingtalk-sync-log',
      component: () => import('@/views/admin/DingTalkSyncLog.vue'),
      meta: { title: '同步日志', roles: ['PMO_ADMIN', 'ADMIN'] },
    },
    {
      // P0-A.1: 工时→成本 月度核算 (F1 主验收页)
      // PMO_ADMIN / EXEC / PM / DEPT_LEAD 都可看 (财务 / PMO 视角)
      path: '/cost/user-month',
      component: () => import('@/views/CostUserMonth.vue'),
      meta: { title: '工时成本核算', roles: ['PMO_ADMIN', 'ADMIN', 'EXEC', 'PM', 'DEPT_LEAD'] },
    },
    {
      // T4 多维成本核算 (F2 价值核心: 财务/PMO 视角)
      path: '/cost/dashboard',
      component: () => import('@/views/CostDashboard.vue'),
      meta: { title: '多维成本看板', roles: ['PMO_ADMIN', 'ADMIN', 'EXEC', 'PM', 'DEPT_LEAD', 'FINANCE'] },
    },
    {
      // V5.1+ / WP-M5-02: 预警看板 + 列表 (F4 告警模块)
      path: '/alerts',
      component: () => import('@/views/AlertList.vue'),
      meta: {
        title: '告警列表',
        roles: ['PMO_ADMIN', 'ADMIN', 'FINANCE', 'EXEC'],
      },
    },
    {
      path: '/alerts/dashboard',
      component: () => import('@/views/AlertDashboard.vue'),
      meta: {
        title: '告警看板',
        roles: ['PMO_ADMIN', 'ADMIN', 'FINANCE', 'EXEC'],
      },
    },
    {
      // V5.0 / WP-M4-03: 财务-成本 3-way match 对账 (F3 主验收页)
      path: '/finance/reconciliation',
      component: () => import('@/views/ReconciliationList.vue'),
      meta: {
        title: '财务对账',
        roles: ['PMO_ADMIN', 'ADMIN', 'FINANCE', 'EXEC'],
      },
    },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!auth.token && to.path !== '/login') return { path: '/login' }
  if (auth.token && to.path === '/login') return { path: '/' }
  // 角色门:meta.roles 限定,无 token 不在主路由(已在前面返回)
  const required = to.meta?.roles as string[] | undefined
  if (required && auth.user) {
    const userRole = (auth.user as any).role || ''
    if (!required.includes(userRole)) return { path: '/' }
  }
})

export default router
