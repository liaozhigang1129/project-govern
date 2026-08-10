/**
 * DND 勿扰时段 API(P2 #2)。
 *
 * 用法:
 *   const list = await imQuietHoursApi.list(7)
 *   await imQuietHoursApi.create({ userId: 7, startTime: '22:00', endTime: '08:00' })
 *   await imQuietHoursApi.update(3, { enabled: false })
 *   await imQuietHoursApi.remove(3)
 *
 * 设计:
 *  - 每用户多窗口(午餐 + 深夜)
 *  - HH:mm 24h,end < start 自动视为跨午夜
 *  - 命中任一启用窗口 → IM 推送跳过(邮件仍走)
 */
import api from './client'

export interface ImQuietHours {
  id: number
  userId: number
  startTime: string // "HH:mm"
  endTime: string // "HH:mm"
  timezone: string
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface ImQuietHoursCreateReq {
  userId: number
  startTime: string
  endTime: string
  timezone?: string
}

export interface ImQuietHoursUpdateReq {
  startTime?: string
  endTime?: string
  timezone?: string
  enabled?: boolean
}

export const imQuietHoursApi = {
  async list(userId: number): Promise<ImQuietHours[]> {
    return api.get('/user-im-quiet-hours', { params: { userId } })
  },
  async create(req: ImQuietHoursCreateReq): Promise<ImQuietHours> {
    return api.post('/user-im-quiet-hours', req)
  },
  async update(id: number, req: ImQuietHoursUpdateReq): Promise<ImQuietHours> {
    return api.put(`/user-im-quiet-hours/${id}`, req)
  },
  async remove(id: number): Promise<void> {
    await api.delete(`/user-im-quiet-hours/${id}`)
  },
}
