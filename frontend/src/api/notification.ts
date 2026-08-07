/**
 * 通知中心 API (P1.5 收尾)
 *   GET  /api/notifications/unread-count
 *   GET  /api/notifications/page?status=&page=&size=
 *   POST /api/notifications/read   { all?: true } | { ids: number[] }
 */
import api from './client'

export type NotificationStatus = 'UNREAD' | 'READ'
export type NotificationCategory =
  | 'INITIATION_SUBMIT'
  | 'INITIATION_DECIDE'
  | 'INITIATION_SUPPLEMENT'
  | 'TIMESHEET_SUBMIT'
  | 'TIMESHEET_DECIDE'
  | 'TIMESHEET_BATCH_APPROVED'
  | 'TIMESHEET_REMINDER'

export interface Notification {
  id: number
  recipientId: number
  category: NotificationCategory
  resourceId: number
  resourceCode: string
  title: string
  content: string
  status: NotificationStatus
  readAt: string | null
  createdAt: string
}

export interface NotificationPage {
  rows: Notification[]
  total: number
  page: number
  size: number
}

export async function fetchUnreadCount(): Promise<{ count: number }> {
  return api.get('/notifications/unread-count')
}

export async function fetchNotificationPage(
  status: NotificationStatus | 'ALL' = 'ALL',
  page = 0,
  size = 20
): Promise<NotificationPage> {
  const params: Record<string, string | number> = { page, size }
  if (status !== 'ALL') params.status = status
  return api.get('/notifications/page', { params })
}

export async function markRead(body: { all?: true } | { ids: number[] }): Promise<number> {
  return api.post('/notifications/read', body)
}

/** 分类中文名(简单映射,够用即可) */
export const CATEGORY_LABEL: Record<NotificationCategory, string> = {
  INITIATION_SUBMIT: '立项提交',
  INITIATION_DECIDE: '审批决定',
  INITIATION_SUPPLEMENT: '补料重提',
  TIMESHEET_SUBMIT: '工时提交',
  TIMESHEET_DECIDE: '工时审批',
  TIMESHEET_BATCH_APPROVED: '工时批量审批',
  TIMESHEET_REMINDER: '工时催办',
}
