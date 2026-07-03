<template>
  <div class="page-container">
    <PageHeader title="设置与隐私" subtitle="查看实例配置、外观与数据处理方式" />
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card class="fk-card" shadow="never" header="外观">
          <el-form label-width="88px">
            <el-form-item label="主题模式">
              <el-radio-group v-model="themeMode">
                <el-radio-button value="light">浅色</el-radio-button>
                <el-radio-button value="dark">暗色</el-radio-button>
                <el-radio-button value="system">跟随系统</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <p class="theme-hint">暗色主题对齐设计稿 dark-15 色彩规范</p>
          </el-form>
        </el-card>
        <el-card class="fk-card settings-card" shadow="never" header="实例信息">
          <el-descriptions v-if="config" :column="1" border>
            <el-descriptions-item label="实例名称">{{ config.instanceName }}</el-descriptions-item>
            <el-descriptions-item label="初始设置">
              <el-tag :type="config.setupComplete ? 'success' : 'warning'" size="small">
                {{ config.setupComplete ? '已完成' : '未完成' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
          <el-skeleton v-else :rows="3" animated />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card class="fk-card" shadow="never" header="隐私与数据">
          <el-descriptions v-if="config" :column="1" border>
            <el-descriptions-item label="数据存储">本地服务器（私有化部署）</el-descriptions-item>
            <el-descriptions-item label="向量引擎">{{ config.vectorProvider }}</el-descriptions-item>
            <el-descriptions-item label="Embedding">{{ config.embeddingProvider }}</el-descriptions-item>
            <el-descriptions-item label="大模型">
              {{ config.llmProviderName || config.llmProvider || '—' }} / {{ config.llmModel }}
            </el-descriptions-item>
            <el-descriptions-item label="外连策略">
              {{ config.llmAllowExternal ? '允许调用外部 API' : '仅本地模型' }}
            </el-descriptions-item>
          </el-descriptions>
          <div v-if="config && isAdmin" class="settings-link">
            <el-button type="primary" link @click="router.push('/settings/llm')">
              配置大模型 →
            </el-button>
          </div>
          <el-skeleton v-if="!config" :rows="4" animated />
        </el-card>
      </el-col>
    </el-row>
    <el-card v-if="llmProviders.length" class="fk-card providers-card" shadow="never" header="支持的 LLM 提供商">
      <el-table :data="llmProviders" stripe>
        <el-table-column prop="name" label="名称" width="140" />
        <el-table-column prop="id" label="标识" width="120" />
        <el-table-column prop="defaultModel" label="默认模型" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.local ? 'success' : 'info'" size="small">
              {{ row.local ? '本地' : '云端' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="docsHint" label="说明" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import { useAuthStore } from '@/stores/auth'
import { useConfigStore } from '@/stores/config'
import { useThemeStore, type ThemeMode } from '@/stores/theme'

const router = useRouter()
const authStore = useAuthStore()
const configStore = useConfigStore()
const themeStore = useThemeStore()
const { config, llmProviders } = storeToRefs(configStore)
const { isAdmin } = storeToRefs(authStore)

const themeMode = computed({
  get: () => themeStore.mode,
  set: (val: ThemeMode) => themeStore.setMode(val)
})

onMounted(async () => {
  await configStore.ensureLoaded()
  await configStore.fetchLlmProviders()
})
</script>

<style scoped lang="scss">
.fk-card {
  border-radius: 10px;
  border: 1px solid $fk-border;
  background: $fk-card-bg;
}

.settings-card {
  margin-top: 16px;
}

.providers-card {
  margin-top: 16px;
}

.theme-hint {
  margin: 0 0 0 88px;
  font-size: 12px;
  color: $fk-text-secondary;
}

.settings-link {
  margin-top: 12px;
}
</style>
