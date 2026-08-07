<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const form = reactive({ username: 'admin', password: 'pmo123' })
const loading = ref(false)
const router = useRouter()
const auth = useAuthStore()

async function submit() {
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    ElMessage.success(`欢迎,${auth.user?.fullName}`)
    router.push('/')
  } catch (e: any) {
    ElMessage.error(e.message ?? '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div
    style="height: 100vh; display: flex; align-items: center; justify-content: center;
           background: linear-gradient(135deg, #667eea, #764ba2)"
  >
    <el-card style="width: 380px; border-radius: 12px">
      <h2 style="margin: 0 0 24px; text-align: center">🏗 PMO 治理系统</h2>
      <el-form :model="form" label-width="0" @keyup.enter="submit">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" size="large" />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            size="large"
            show-password
          />
        </el-form-item>
        <el-button
          type="primary"
          size="large"
          :loading="loading"
          style="width: 100%"
          @click="submit"
        >
          登录
        </el-button>
        <p style="margin-top: 16px; font-size: 12px; color: #909399; text-align: center">
          演示账号: admin / pmo123<br />
          其他: pm_zhang, lead_wu, vp_chen
        </p>
      </el-form>
    </el-card>
  </div>
</template>
