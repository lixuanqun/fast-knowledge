<template>
  <div class="page-container">
    <PageHeader
      title="大模型配置"
      subtitle="选择 OpenAI 兼容提供商并配置连接参数，保存后立即生效"
    />

    <el-row :gutter="16">
      <el-col :span="14">
        <el-card class="fk-card" shadow="never" header="连接参数">
          <el-form
            ref="formRef"
            v-loading="loading"
            :model="form"
            :rules="rules"
            label-width="120px"
            @submit.prevent
          >
            <el-form-item label="提供商" prop="provider">
              <el-select
                v-model="form.provider"
                placeholder="选择提供商"
                style="width: 100%"
                @change="onProviderChange"
              >
                <el-option
                  v-for="item in llmProviders"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id"
                >
                  <span>{{ item.name }}</span>
                  <el-tag
                    v-if="item.local"
                    size="small"
                    type="success"
                    style="margin-left: 8px"
                  >
                    本地
                  </el-tag>
                </el-option>
              </el-select>
            </el-form-item>

            <el-form-item label="API Base URL" prop="baseUrl">
              <el-input v-model="form.baseUrl" placeholder="https://api.example.com/v1" />
            </el-form-item>

            <el-form-item label="API Key" prop="apiKey">
              <el-input
                v-model="form.apiKey"
                type="password"
                show-password
                :placeholder="apiKeyPlaceholder"
              />
              <p class="field-hint">留空表示不修改已保存的密钥</p>
            </el-form-item>

            <el-form-item label="模型" prop="model">
              <el-input
                v-model="form.model"
                :placeholder="modelPlaceholder"
              />
              <p v-if="selectedProvider?.id === 'volcengine'" class="field-hint">
                火山引擎请填写推理接入点 ID（Endpoint ID）
              </p>
            </el-form-item>

            <el-form-item label="允许外连">
              <el-switch v-model="form.allowExternal" />
              <p class="field-hint">关闭后仅允许访问本地或内网 LLM 端点</p>
            </el-form-item>

            <el-form-item>
              <el-button type="primary" :loading="saving" @click="handleSave">
                保存配置
              </el-button>
              <el-button :loading="testing" @click="handleTest">
                测试连接
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card v-if="currentConfig" class="fk-card" shadow="never" header="当前生效">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="提供商">
              {{ currentConfig.providerName }}（{{ currentConfig.provider }}）
            </el-descriptions-item>
            <el-descriptions-item label="Base URL">{{ currentConfig.baseUrl }}</el-descriptions-item>
            <el-descriptions-item label="模型">{{ currentConfig.model }}</el-descriptions-item>
            <el-descriptions-item label="API Key">
              {{ currentConfig.apiKeyConfigured ? currentConfig.apiKeyMask : '未配置' }}
            </el-descriptions-item>
            <el-descriptions-item label="外连策略">
              {{ currentConfig.allowExternal ? '允许' : '禁止' }}
            </el-descriptions-item>
            <el-descriptions-item label="配置来源">
              {{ currentConfig.configuredInDb ? '数据库（UI 保存）' : '环境变量 / 默认值' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-card v-if="selectedProvider?.docsHint" class="fk-card docs-card" shadow="never" header="文档提示">
          <p class="docs-hint">{{ selectedProvider.docsHint }}</p>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { storeToRefs } from 'pinia'
import PageHeader from '@/components/PageHeader.vue'
import {
  getLlmConfig,
  testLlmConfig,
  updateLlmConfig,
  type LlmConfig,
  type LlmConfigUpdate
} from '@/api/llm-config'
import { useConfigStore } from '@/stores/config'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

const configStore = useConfigStore()
const { llmProviders } = storeToRefs(configStore)

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const currentConfig = ref<LlmConfig | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  provider: 'ollama',
  baseUrl: '',
  apiKey: '',
  model: '',
  allowExternal: true
})

const rules: FormRules = {
  provider: [{ required: true, message: '请选择提供商', trigger: 'change' }],
  baseUrl: [{ required: true, message: '请填写 API Base URL', trigger: 'blur' }],
  model: [{ required: true, message: '请填写模型名称', trigger: 'blur' }]
}

const selectedProvider = computed(() =>
  llmProviders.value.find(p => p.id === form.provider)
)

const apiKeyPlaceholder = computed(() => {
  if (currentConfig.value?.apiKeyConfigured && currentConfig.value.apiKeyMask) {
    return `已配置 ${currentConfig.value.apiKeyMask}，留空不修改`
  }
  return '请输入 API Key（Ollama 可填 ollama）'
})

const modelPlaceholder = computed(() => selectedProvider.value?.defaultModel || '模型名称或 Endpoint ID')

function onProviderChange(providerId: string) {
  const preset = llmProviders.value.find(p => p.id === providerId)
  if (!preset) return
  if (preset.defaultBaseUrl) {
    form.baseUrl = preset.defaultBaseUrl
  }
  if (preset.defaultModel) {
    form.model = preset.defaultModel
  }
}

function buildPayload(): LlmConfigUpdate {
  return {
    provider: form.provider,
    baseUrl: form.baseUrl.trim(),
    apiKey: form.apiKey.trim() || undefined,
    model: form.model.trim(),
    allowExternal: form.allowExternal
  }
}

async function loadConfig() {
  loading.value = true
  try {
    await configStore.fetchLlmProviders()
    const res = await getLlmConfig()
    currentConfig.value = res.data
    form.provider = res.data.provider
    form.baseUrl = res.data.baseUrl
    form.model = res.data.model
    form.allowExternal = res.data.allowExternal
    form.apiKey = ''
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const res = await updateLlmConfig(buildPayload())
    currentConfig.value = res.data
    form.apiKey = ''
    configStore.invalidate()
    await configStore.fetchConfig()
    ElMessage.success('大模型配置已保存并生效')
  } finally {
    saving.value = false
  }
}

async function handleTest() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  testing.value = true
  try {
    const res = await testLlmConfig(buildPayload())
    ElMessage.success(res.data.message || '连接成功')
  } finally {
    testing.value = false
  }
}

onMounted(() => {
  loadConfig()
})
</script>

<style scoped lang="scss">
.fk-card {
  border-radius: 10px;
  border: 1px solid $fk-border;
  background: $fk-card-bg;
}

.docs-card {
  margin-top: 16px;
}

.field-hint,
.docs-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: $fk-text-secondary;
  line-height: 1.5;
}
</style>
