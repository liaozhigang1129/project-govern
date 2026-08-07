/**
 * IM 绑定管理 API(P2-A + V2 用户自助)。
 *
 * 用法:
 *   const list = await imBindingApi.list()              // 我的
 *   const all  = await imBindingApi.list({ userId: 1 }) // admin 限定
 *   await imBindingApi.create({ userId, channel, externalUserId })
 *   await imBindingApi.update(id, { externalUserId, enabled })
 *   await imBindingApi.remove(id)
 *
 * RBAC:
 *  - create / delete: PMO_ADMIN/ADMIN
 *  - list / get / update: 任意已登录(自己) / admin(全部)
 */
import api from './client'

export type ImChannel = 'wechat_work' | 'dingtalk' | 'feishu'

export interface ImBinding {
  id: number
  userId: number
  channel: ImChannel
  externalUserId: string
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface ImBindingCreateReq {
  userId: number
  channel: ImChannel
  externalUserId: string
}

export interface ImBindingUpdateReq {
  externalUserId?: string
  enabled?: boolean
}

export const CHANNEL_LABEL: Record<ImChannel, string> = {
  wechat_work: '企业微信',
  dingtalk: '钉钉',
  feishu: '飞书',
}

/** 简化的"用户摘要" — admin 选择被绑用户时使用(从 /users 拉) */
export interface UserLite {
  id: number
  username: string
  fullName: string
  email?: string
  primaryRole?: { id: number; code: string; name: string }
}

export const imBindingApi = {
  /** 列表。userId 不传 = 自己的,传 = admin 限定 */
  async list(params?: { userId?: number }): Promise<ImBinding[]> {
    return api.get('/user-im-bindings', { params })
  },
  async get(id: number): Promise<ImBinding> {
    return api.get(`/user-im-bindings/${id}`)
  },
  async create(req: ImBindingCreateReq): Promise<ImBinding> {
    return api.post('/user-im-bindings', req)
  },
  async update(id: number, req: ImBindingUpdateReq): Promise<ImBinding> {
    return api.put(`/user-im-bindings/${id}`, req)
  },
  async remove(id: number): Promise<void> {
    await api.delete(`/user-im-bindings/${id}`)
  },
}
