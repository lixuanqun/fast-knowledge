<template>
  <div class="setup-page">
    <div class="setup-wave" aria-hidden="true" />
    <el-card class="setup-card" shadow="never">
      <div class="setup-logo">
        <span class="setup-logo__icon">
          <el-icon :size="28"><Lightning /></el-icon>
        </span>
      </div>
      <h1 class="setup-title">欢迎使用 Fast Knowledge</h1>
      <p class="desc">请完成初始配置以保障数据安全</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="handleSubmit">
        <el-form-item label="实例名称" prop="instanceName">
          <el-input v-model="form.instanceName" placeholder="例如：Acme 知识库" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password />
        </el-form-item>
        <el-button class="submit-btn" type="primary" :loading="loading" @click="handleSubmit">
          完成设置
        </el-button>
      </el-form>
      <div v-if="config" class="privacy-hint">
        <el-icon class="privacy-hint__icon"><InfoFilled /></el-icon>
        <div class="privacy-hint__text">
          <p>数据存储：本地服务器</p>
          <p>向量引擎：{{ config.vectorProvider }}</p>
          <p>Embedding：{{ config.embeddingProvider }}</p>
          <p>大模型：{{ config.llmModel }}（{{ config.llmAllowExternal ? '可外连' : '仅本地' }}）</p>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { completeSetup } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { useConfigStore } from '@/stores/config'
import { prefetchAfterLogin, prefetchMainLayout } from '@/router/prefetch'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { InfoFilled, Lightning } from '@element-plus/icons-vue'

const router = useRouter()
const authStore = useAuthStore()
const configStore = useConfigStore()
const { config } = storeToRefs(configStore)

const loading = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  instanceName: 'Fast Knowledge',
  newPassword: '',
  confirmPassword: ''
})

const rules: FormRules = {
  instanceName: [{ required: true, message: '请输入实例名称', trigger: 'blur' }],
  newPassword: [{ required: true, min: 6, message: '密码至少 6 位', trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (_r, v, cb) => (v === form.newPassword ? cb() : cb(new Error('两次密码不一致'))),
      trigger: 'blur'
    }
  ]
}

onMounted(() => {
  prefetchMainLayout()
  configStore.fetchConfig()
})

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await completeSetup(form.instanceName, form.newPassword)
    authStore.clearMustChangePassword()
    configStore.invalidate()
    await configStore.fetchConfig()
    prefetchAfterLogin('/dashboard')
    ElMessage.success('初始设置完成')
    router.replace('/dashboard')
  } catch (e: unknown) {
    const message = e instanceof Error ? e.message : '设置失败'
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.setup-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background: $fk-login-gradient;
}

.setup-wave {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 180px;
  background:
    radial-gradient(ellipse 80% 100% at 50% 100%, rgba(64, 158, 255, 0.08), transparent 70%);
}

.setup-card {
  width: 480px;
  position: relative;
  z-index: 1;
  border-radius: $fk-card-radius;
  border: 1px solid $fk-border;
  box-shadow: $fk-card-shadow;
  padding: 16px 20px 20px;
  background: $fk-card-bg;
}

.setup-logo {
  display: flex;
  justify-content: center;
  margin-bottom: 16px;
}

.setup-logo__icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  background: $fk-primary;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.setup-title {
  margin: 0;
  text-align: center;
  font-size: 22px;
  font-weight: 700;
  color: $fk-text-primary;
}

.desc {
  color: $fk-text-secondary;
  margin: 10px 0 24px;
  text-align: center;
  font-size: 14px;
}

.submit-btn {
  width: 100%;
  height: 42px;
  margin-top: 8px;
}

.privacy-hint {
  margin-top: 24px;
  padding: 14px 16px;
  border: 1px solid $fk-border;
  border-radius: 8px;
  background: $fk-surface-muted;
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.privacy-hint__icon {
  color: $fk-primary;
  margin-top: 2px;
  flex-shrink: 0;
}

.privacy-hint__text p {
  margin: 0 0 6px;
  font-size: 13px;
  color: $fk-text-regular;
  line-height: 1.5;
}

.privacy-hint__text p:last-child {
  margin-bottom: 0;
}
</style>
