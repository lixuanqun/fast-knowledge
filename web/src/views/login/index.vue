<template>
  <div class="login-page">
    <LoginHero />
    <el-card class="login-card" shadow="never">
      <div class="login-brand">
        <h1 class="brand-title">Fast Knowledge 快速知识库</h1>
        <p class="brand-desc">面向中小企业的开源私有化知识库</p>
      </div>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        size="large"
        @submit.prevent="handleLogin"
      >
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="admin" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
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
        <el-button
          v-if="ldapEnabled"
          class="login-btn alt"
          :loading="ldapLoading"
          @click="handleLdapLogin"
        >
          LDAP 登录
        </el-button>
        <el-button v-if="oidcEnabled" class="login-btn alt" @click="handleOidcLogin">
          企业 SSO 登录
        </el-button>
      </el-form>
      <p class="hint">默认账号 admin / admin123（首次登录须修改密码）</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useConfigStore } from '@/stores/config'
import { initTheme } from '@/stores/theme'
import { prefetchAfterLogin, prefetchMainLayout, prefetchView } from '@/router/prefetch'
import LoginHero from '@/components/design/LoginHero.vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { getOidcAuthorizeUrl, ldapLogin } from '@/api/auth'

const router = useRouter()
const authStore = useAuthStore()
const configStore = useConfigStore()
const loading = ref(false)
const ldapLoading = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ username: '', password: '' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const ldapEnabled = computed(() => !!configStore.config?.ldapEnabled)
const oidcEnabled = computed(() => !!configStore.config?.oidcEnabled)

onMounted(async () => {
  initTheme()
  prefetchMainLayout()
  prefetchView('/dashboard')
  try {
    await configStore.fetchConfig()
  } catch {
    /* 登录页允许离线展示 */
  }
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

async function handleLdapLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  ldapLoading.value = true
  try {
    const res = await ldapLogin(form.username, form.password)
    const data = res.data
    authStore.applySession(data)
    const target = data.mustChangePassword ? '/setup' : '/dashboard'
    prefetchAfterLogin(target)
    ElMessage.success('LDAP 登录成功')
    router.push(target)
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : 'LDAP 登录失败')
  } finally {
    ldapLoading.value = false
  }
}

async function handleOidcLogin() {
  try {
    const res = await getOidcAuthorizeUrl()
    window.location.href = res.data.authorizationUrl
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '无法启动 SSO')
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
  padding: 28px 32px 24px;
  background: $fk-card-bg;

  :deep(.el-input__wrapper) {
    background: $fk-surface-muted;
  }
}

.login-brand {
  text-align: center;
  margin-bottom: 24px;
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
  margin-top: 4px;
  height: 42px;
  font-size: 15px;

  &.alt {
    margin-top: 10px;
  }
}

.hint {
  color: $fk-text-secondary;
  font-size: 12px;
  text-align: center;
  margin: 18px 0 0;
}
</style>
