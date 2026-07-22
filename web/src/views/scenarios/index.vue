<template>
  <div class="page-container">
    <PageHeader title="场景模板" subtitle="制造 / 国企交付推荐配置与验收清单（企业版）" />
    <el-skeleton v-if="loading" animated :rows="4" />
    <el-row v-else :gutter="16">
      <el-col v-for="item in scenarios" :key="item.id" :xs="24" :md="8">
        <el-card class="fk-card scenario-card" shadow="never">
          <template #header>
            <div class="card-head">
              <span>{{ item.name }}</span>
              <el-tag size="small" type="warning">{{ item.category }}</el-tag>
            </div>
          </template>
          <p class="desc">{{ item.description }}</p>
          <p class="meta">
            建议知识库名：{{ item.suggestedKbName || '—' }}
          </p>
          <p class="meta">
            推荐分块：{{ item.recommendedChunk?.size ?? '—' }} / overlap
            {{ item.recommendedChunk?.overlap ?? '—' }}
          </p>
          <div class="types">
            <el-tag
              v-for="t in item.sampleDocTypes || []"
              :key="t"
              size="small"
              class="type-tag"
            >{{ item.docTypeLabels?.[t] || t }}</el-tag>
          </div>
          <div class="checklist">
            <div class="checklist-title">验收清单</div>
            <ul>
              <li v-for="(c, i) in item.checklist || []" :key="i">{{ c }}</li>
            </ul>
          </div>
          <el-button type="primary" @click="goCreateKb(item)">去创建知识库</el-button>
        </el-card>
      </el-col>
    </el-row>
    <EmptyState v-if="!loading && !scenarios.length" variant="default" description="暂无场景模板" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { listScenarios, type ScenarioTemplate } from '@/api/scenarios'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loading = ref(true)
const scenarios = ref<ScenarioTemplate[]>([])

onMounted(async () => {
  try {
    const res = await listScenarios()
    scenarios.value = res.data || []
  } catch {
    scenarios.value = []
  } finally {
    loading.value = false
  }
})

function goCreateKb(item: ScenarioTemplate) {
  ElMessage.info(`建议创建「${item.suggestedKbName || item.name}」，文档类型优先使用模板推荐值`)
  router.push('/kbs')
}
</script>

<style scoped lang="scss">
.scenario-card {
  margin-bottom: 16px;
  min-height: 360px;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.desc {
  color: $fk-text-secondary;
  font-size: 13px;
  min-height: 40px;
}

.meta {
  font-size: 12px;
  color: $fk-text-secondary;
  margin: 4px 0;
}

.types {
  margin: 10px 0 14px;
}

.type-tag {
  margin-right: 6px;
  margin-bottom: 4px;
}

.checklist-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
}

.checklist ul {
  margin: 0 0 16px;
  padding-left: 18px;
  font-size: 13px;
  color: $fk-text-regular;
}
</style>
