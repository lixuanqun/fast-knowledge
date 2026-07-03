<template>
  <div class="page-container">
    <PageHeader title="智能检索" subtitle="基于 Lucene 混合检索（向量 + 关键词）" />

    <el-card class="fk-card search-form-card" shadow="never">
      <el-form label-position="top" class="search-form">
        <div class="search-form__row">
          <el-form-item label="知识库" required class="search-form__item">
            <KbSelect v-model="kbId" width="100%" />
          </el-form-item>
          <el-form-item label="关键词" required class="search-form__item search-form__item--grow">
            <el-input
              v-model="query"
              placeholder="输入检索内容"
              clearable
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="返回条数" class="search-form__item search-form__item--narrow">
            <el-input-number v-model="topK" :min="1" :max="30" style="width:100%" />
          </el-form-item>
          <el-form-item label="文档类型" class="search-form__item">
            <el-select v-model="docType" clearable placeholder="全部" style="width:100%">
              <el-option label="制度" value="制度" />
              <el-option label="工艺" value="工艺" />
              <el-option label="设备" value="设备" />
              <el-option label="标准" value="标准" />
              <el-option label="其他" value="其他" />
            </el-select>
          </el-form-item>
          <el-form-item label=" " class="search-form__item search-form__item--btn">
            <el-button :loading="loading" type="primary" @click="handleSearch">检索</el-button>
          </el-form-item>
        </div>
      </el-form>
    </el-card>

    <template v-if="loading">
      <div class="result-loading">
        <el-skeleton :rows="5" animated />
        <p class="loading-hint">
          <el-icon class="is-loading"><Loading /></el-icon>
          搜索中，请稍候...
        </p>
      </div>
    </template>

    <template v-else-if="searched">
      <p v-if="hits.length" class="result-summary">共找到 {{ hits.length }} 条相关片段</p>
      <el-card v-for="(hit, i) in pagedHits" :key="i" class="fk-card hit-card" shadow="never">
        <div class="hit-card__header">
          <div class="hit-card__title">
            <el-icon><Document /></el-icon>
            <strong>{{ hit.documentTitle }}</strong>
          </div>
          <span class="hit-score">{{ hit.score.toFixed(3) }}</span>
        </div>
        <p v-if="hit.section || hit.docNo" class="hit-card__meta">
          <span v-if="hit.docType">{{ hit.docType }}</span>
          <span v-if="hit.docNo"> · {{ hit.docNo }}</span>
          <span v-if="hit.section"> · {{ hit.section }}</span>
        </p>
        <p class="hit-card__content">{{ hit.content }}</p>
        <el-button
          v-if="kbId && hit.documentId"
          link
          type="primary"
          class="hit-card__link"
          @click="openHitPreview(hit)"
        >
          查看原文
        </el-button>
      </el-card>
      <EmptyState v-if="!hits.length" variant="search-empty" />
      <div v-if="hits.length > pageSize" class="table-footer">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="hits.length"
          layout="prev, pager, next"
          background
          small
        />
      </div>
    </template>

    <EmptyState v-else variant="search" />

    <DocumentPreviewDrawer
      v-model:visible="previewVisible"
      :kb-id="kbId!"
      :doc-id="previewDocId"
      :highlight-chunk-id="previewChunkId"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import KbSelect from '@/components/KbSelect.vue'
import EmptyState from '@/components/EmptyState.vue'
import { DocumentPreviewDrawer } from '@/components/async'
import { useSearchMutation } from '@/composables/queries/useSearch'
import type { SearchHit } from '@/api/search'
import { ElMessage } from 'element-plus'
import { Document, Loading } from '@element-plus/icons-vue'

const kbId = ref<number>()
const query = ref('')
const topK = ref(8)
const docType = ref<string>()
const searched = ref(false)
const page = ref(1)
const pageSize = 10
const previewVisible = ref(false)
const previewDocId = ref<number>()
const previewChunkId = ref<number>()

const searchMutation = useSearchMutation()
const loading = computed(() => searchMutation.isPending.value)
const hits = computed(() => searchMutation.data.value || [])
const pagedHits = computed(() => {
  const start = (page.value - 1) * pageSize
  return hits.value.slice(start, start + pageSize)
})

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
  page.value = 1
  try {
    await searchMutation.mutateAsync({
      kbId: kbId.value,
      query: query.value.trim(),
      topK: topK.value,
      docType: docType.value || undefined
    })
  } catch {
    /* 错误已由 axios 拦截器提示 */
  }
}

function openHitPreview(hit: SearchHit) {
  previewDocId.value = hit.documentId
  previewChunkId.value = hit.chunkId
  previewVisible.value = true
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

.search-form__row {
  display: flex;
  gap: 16px;
  align-items: flex-end;
  flex-wrap: wrap;
}

.search-form__item {
  margin-bottom: 0;
  min-width: 160px;
}

.search-form__item--grow {
  flex: 1;
  min-width: 200px;
}

.search-form__item--narrow {
  width: 120px;
}

.search-form__item--btn {
  min-width: auto;
}

.result-summary {
  margin: 0 0 12px;
  font-size: 13px;
  color: $fk-text-secondary;
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

.hit-score {
  flex-shrink: 0;
  padding: 2px 10px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  color: $fk-primary;
  background: var(--fk-hit-score-bg);
}

.hit-card__meta {
  margin: 0 0 8px;
  font-size: 12px;
  color: $fk-text-secondary;
}

.hit-card__link {
  margin-top: 8px;
  padding: 0;
}

.hit-card__content {
  margin: 0;
  white-space: pre-wrap;
  color: $fk-text-regular;
  line-height: 1.75;
  font-size: 14px;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
