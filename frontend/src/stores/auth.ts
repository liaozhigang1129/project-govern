import { defineStore } from 'pinia'
import { ref } from 'vue'
import api, { type LoginResponse, type UserInfo } from '@/api/client'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const user = ref<UserInfo | null>(null)

  function restore() {
    const raw = localStorage.getItem('user')
    if (raw) user.value = JSON.parse(raw)
  }

  async function login(username: string, password: string) {
    const res = await api.post<LoginResponse>('/auth/login', { username, password })
    const data = res as unknown as LoginResponse
    // P1.5-c: 后端改用 accessToken + refreshToken
    token.value = data.accessToken
    user.value = data.user
    localStorage.setItem('token', data.accessToken)
    localStorage.setItem('user', JSON.stringify(data.user))
  }

  function logout() {
    token.value = null
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  return { token, user, login, logout, restore }
})
