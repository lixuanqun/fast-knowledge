<template>
  <div class="setup-page">
    <el-card class="setup-card" shadow="always">
      <h1>欢迎使用 Fast Knowledge</h1>
      <p class="desc">请完成初始配置以保障数据安全</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" @submit.prevent="handleSubmit">
        <el-form-item label="实例名称" prop="instanceName">
          <el-input v-model="form.instanceName" placeholder="例如：Acme 知识库" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password />
        </el-form-item>
        <el-button type="primary" :loading="loading" @click="handleSubmit">完成设置</el-button>
      </el-form>
      <div v-if="config" class="privacy-hint">
        <p>数据存储：本地服务器</p>
        <p>向量引擎：{{ config.vectorProvider }}</p>
        <p>Embedding：{{ config.embeddingProvider }}</p>
        <p>大模型：{{ config.llmModel }}（{{ config.llmAllowExternal ? '可外连' : '仅本地' }}）</p>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { completeSetup } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { useConfigStore } from '@/stores/config'
import { useSystemConfigQuery } from '@/composables/queries/useSystemConfig'
import { prefetchAfterLogin, prefetchMainLayout } from '@/router/prefetch'

const router = useRouter()
const authStore = useAuthStore()
const configStore = useConfigStore()
const { data: config } = useSystemConfigQuery()

const loading = ref(false)
const formRef = ref<FormInstance>()

onMounted(() => {
  prefetchMainLayout()
})

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

<style scoped>
.setup-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, #e8f3ff 0%, #f5f7fa 45%, #f0f5ff 100%);
}
.setup-card {
  width: 520px;
  padding: 12px;
}
.desc { color: #888; margin-bottom: 24px; }
.privacy-hint {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #eee;
  font-size: 13px;
  color: #666;
}
</style>
