<template>
  <div class="login-page">
    <LoginHero />
    <el-card class="login-card" shadow="never">
      <div class="login-brand">
        <h1 class="brand-title">Fast Knowledge 快速知识库</h1>
        <p class="brand-desc">高效管理知识 · 智能检索 · 团队协作</p>
      </div>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        size="large"
        label-position="top"
        @submit.prevent="handleLogin"
      >
        <el-form-item label="用户名" prop="username" required>
          <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item label="密码" prop="password" required>
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
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
import LoginHero from '@/components/design/LoginHero.vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ username: '', password: '' })
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
    const message = e instanceof Error ? e.message : '账号或密码错误'
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background: $fk-login-gradient;
}

.login-card {
  width: 420px;
  position: relative;
  z-index: 1;
  border-radius: $fk-card-radius;
  border: 1px solid $fk-border;
  box-shadow: $fk-card-shadow;
  padding: 12px 16px 8px;
}

.login-brand {
  text-align: center;
  margin-bottom: 28px;
}

.brand-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: $fk-primary;
  line-height: 1.4;
}

.brand-desc {
  margin: 10px 0 0;
  color: $fk-text-secondary;
  font-size: 14px;
}

.login-btn {
  width: 100%;
  margin-top: 8px;
  height: 42px;
  font-size: 15px;
}

.hint {
  color: $fk-text-secondary;
  font-size: 12px;
  text-align: center;
  margin: 18px 0 8px;
}
</style>
