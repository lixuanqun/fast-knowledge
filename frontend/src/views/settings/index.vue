<template>
  <div class="page-container">
    <PageHeader title="设置与隐私" subtitle="查看实例配置与数据处理方式" />
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card header="实例信息">
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
        <el-card header="隐私与数据">
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
          <el-skeleton v-else :rows="4" animated />
        </el-card>
      </el-col>
    </el-row>
    <el-card v-if="llmProviders.length" class="providers-card" header="支持的 LLM 提供商">
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
import { onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import PageHeader from '@/components/PageHeader.vue'
import { useConfigStore } from '@/stores/config'

const configStore = useConfigStore()
const { config, llmProviders } = storeToRefs(configStore)

onMounted(async () => {
  await configStore.ensureLoaded()
  await configStore.fetchLlmProviders()
})
</script>

<style scoped>
.providers-card { margin-top: 16px; border-radius: 10px; }
</style>
