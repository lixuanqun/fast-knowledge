<template>
  <div class="callback-page">
    <el-result v-if="error" icon="error" title="登录失败" :sub-title="error">
      <template #extra>
        <el-button type="primary" @click="router.replace('/login')">返回登录</el-button>
      </template>
    </el-result>
    <p v-else class="loading">正在完成登录…</p>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import type { LoginResult } from '@/api/auth'
import { getCurrentUser } from '@/api/auth'
import { setToken } from '@/utils/auth'
import { prefetchAfterLogin } from '@/router/prefetch'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const error = ref('')

onMounted(async () => {
  const token = route.query.token as string | undefined
  if (!token) {
    error.value = '未收到登录令牌'
    return
  }
  try {
    setToken(token)
    const res = await getCurrentUser()
    const user: LoginResult = {
      token,
      userId: res.data.id,
      username: res.data.username,
      displayName: res.data.displayName,
      role: res.data.role,
      mustChangePassword: !!res.data.mustChangePassword
    }
    authStore.applySession(user)
    const target = user.mustChangePassword ? '/setup' : '/dashboard'
    prefetchAfterLogin(target)
    router.replace(target)
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '无法获取用户信息'
  }
})
</script>

<style scoped lang="scss">
.callback-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
}
.loading {
  color: $fk-text-secondary;
}
</style>
