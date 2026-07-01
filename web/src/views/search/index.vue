<template>
  <div class="page-container">
    <PageHeader title="智能检索" subtitle="基于 Lucene 混合检索（向量 + 关键词）" />

    <el-card class="fk-card search-form-card" shadow="never">
      <el-form label-width="90px">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="8">
            <el-form-item label="知识库" required>
              <KbSelect v-model="kbId" width="100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="16">
            <el-form-item label="关键词" required>
              <el-input
                v-model="query"
                placeholder="输入检索内容"
                clearable
                @keyup.enter="handleSearch"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="8">
            <el-form-item label="返回条数">
              <el-input-number v-model="topK" :min="1" :max="30" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="16">
            <el-form-item label=" ">
              <div class="form-actions">
                <el-button :loading="loading" type="primary" @click="handleSearch">
                  <el-icon class="btn-icon"><Search /></el-icon>
                  检索
                </el-button>
                <el-button @click="handleReset">
                  <el-icon class="btn-icon"><Refresh /></el-icon>
                  重置
                </el-button>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <el-card class="fk-card result-card" shadow="never">
      <template #header>
        <div class="result-header">
          <span class="result-title">检索结果</span>
          <span v-if="loading" class="result-status is-loading">搜索中...</span>
          <span v-else-if="searched" class="result-status">
            共找到 {{ hits.length }} 条相关片段
          </span>
        </div>
      </template>

      <div v-if="loading" class="result-loading">
        <el-skeleton :rows="5" animated />
        <p class="loading-hint">
          <el-icon class="is-loading"><Loading /></el-icon>
          搜索中，请稍候...
        </p>
      </div>

      <template v-else-if="searched">
        <el-card v-for="(hit, i) in hits" :key="i" class="fk-card hit-card" shadow="never">
          <div class="hit-card__header">
            <div class="hit-card__title">
              <el-icon><Document /></el-icon>
              <strong>{{ hit.documentTitle }}</strong>
            </div>
            <el-tag size="small" type="warning" effect="plain">
              得分 {{ hit.score.toFixed(3) }}
            </el-tag>
          </div>
          <p class="hit-card__content">{{ hit.content }}</p>
        </el-card>
        <EmptyState v-if="!hits.length" variant="search-empty" />
      </template>

      <EmptyState v-else variant="search" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import KbSelect from '@/components/KbSelect.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useSearchMutation } from '@/composables/queries/useSearch'
import { ElMessage } from 'element-plus'
import { Document, Loading, Refresh, Search } from '@element-plus/icons-vue'

const kbId = ref<number>()
const query = ref('')
const topK = ref(8)
const searched = ref(false)

const searchMutation = useSearchMutation()
const loading = computed(() => searchMutation.isPending.value)
const hits = computed(() => searchMutation.data.value || [])

function handleReset() {
  kbId.value = undefined
  query.value = ''
  topK.value = 8
  searched.value = false
  searchMutation.reset()
}

async function handleSearch() {
  if (!kbId.value) {
    ElMessage.error('检索失败：请选择知识库')
    return
  }
  if (!query.value.trim()) {
    ElMessage.warning('请输入检索内容')
    return
  }
  searched.value = true
  try {
    await searchMutation.mutateAsync({
      kbId: kbId.value,
      query: query.value.trim(),
      topK: topK.value
    })
  } catch {
    /* 错误已由 axios 拦截器提示 */
  }
}
</script>

<style scoped lang="scss">
.fk-card {
  border-radius: 10px;
  border: 1px solid $fk-border;
}

.search-form-card {
  margin-bottom: 16px;
}

.form-actions {
  display: flex;
  gap: 10px;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.result-title {
  font-weight: 600;
  color: $fk-text-primary;
}

.result-status {
  font-size: 13px;
  color: $fk-text-secondary;
}

.result-status.is-loading {
  color: $fk-primary;
}

.result-loading {
  padding: 8px 0 16px;
}

.loading-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin: 20px 0 0;
  color: $fk-text-secondary;
  font-size: 14px;
}

.hit-card {
  margin-bottom: 12px;
}

.hit-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.hit-card__title {
  display: flex;
  align-items: center;
  gap: 6px;
  color: $fk-text-primary;
  min-width: 0;
}

.hit-card__title strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hit-card__title .el-icon {
  color: $fk-primary;
  flex-shrink: 0;
}

.hit-card__content {
  margin: 0;
  white-space: pre-wrap;
  color: $fk-text-regular;
  line-height: 1.75;
  font-size: 14px;
  background: $fk-surface-muted;
  padding: 12px 14px;
  border-radius: 8px;
}

.btn-icon {
  margin-right: 4px;
}
</style>
