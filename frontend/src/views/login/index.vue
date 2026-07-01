<template>
  <div class="login-page">
    <div class="login-bg" />
    <el-card class="login-card" shadow="always">
      <div class="login-brand">
        <h1 class="brand-title">Fast Knowledge 快速知识库</h1>
        <p class="brand-desc">面向中小企业的开源私有化知识库</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            show-password
            :prefix-icon="Lock"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-button class="login-btn" type="primary" :loading="loading" @click="handleLogin">
          登录
        </el-button>
      </el-form>
      <p class="hint">默认账号 admin / admin123（首次登录须修改密码）</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { prefetchAfterLogin, prefetchMainLayout, prefetchView } from '@/router/prefetch'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ username: 'admin', password: 'admin123' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

onMounted(() => {
  prefetchMainLayout()
  prefetchView('/dashboard')
})

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const data = await authStore.login(form.username, form.password)
    const target = data.mustChangePassword ? '/setup' : '/dashboard'
    prefetchAfterLogin(target)
    ElMessage.success('登录成功')
    router.push(target)
  } catch (e: unknown) {
    const message = e instanceof Error ? e.message : '登录失败'
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}
.login-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(160deg, #e8f3ff 0%, #f5f7fa 45%, #f0f5ff 100%);
}
.login-card {
  width: 420px;
  position: relative;
  z-index: 1;
  border-radius: 12px;
  padding: 8px 4px 4px;
}
.login-brand { text-align: center; margin-bottom: 28px; }
.brand-desc { margin: 8px 0 0; color: #888; font-size: 14px; }
.login-btn { width: 100%; margin-top: 8px; }
.hint { color: #999; font-size: 12px; text-align: center; margin-top: 16px; }
</style>
