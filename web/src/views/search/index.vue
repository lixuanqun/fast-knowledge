<template>
  <div class="page-container search-page">
    <!-- 搜索区：未搜索时为居中 Hero，搜索后收起为顶部工具条 -->
    <section class="search-hero" :class="{ 'search-hero--compact': searched }">
      <div class="search-hero__inner">
        <div v-if="!searched" class="search-hero__intro">
          <h2 class="search-hero__title">智能检索</h2>
          <p class="search-hero__subtitle">混合向量 + 关键词检索，快速定位制度、工艺与设备文档</p>
        </div>

        <div class="search-bar">
          <el-input
            v-model="query"
            class="search-bar__input"
            :prefix-icon="SearchIcon"
            placeholder="输入关键词，如：设备保养周期、请假审批流程"
            size="large"
            clearable
            @keyup.enter="handleSearch"
          />
          <el-button
            class="search-bar__btn"
            type="primary"
            size="large"
            :loading="loading"
            @click="handleSearch"
          >
            检索
          </el-button>
        </div>

        <div class="search-filters">
          <div class="search-filters__item">
            <span class="search-filters__label">
              <el-icon><Collection /></el-icon>
              知识库
            </span>
            <KbSelect v-model="kbId" width="190px" placeholder="选择知识库" />
          </div>
          <div class="search-filters__item">
            <span class="search-filters__label">
              <el-icon><Files /></el-icon>
              类型
            </span>
            <el-select v-model="docType" clearable placeholder="全部" :style="{ width: '120px' }">
              <el-option v-for="t in DOC_TYPES" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </div>
          <div class="search-filters__item">
            <span class="search-filters__label">
              <el-icon><List /></el-icon>
              条数
            </span>
            <el-select v-model="topK" :style="{ width: '96px' }">
              <el-option v-for="n in TOP_K_OPTIONS" :key="n" :label="`${n} 条`" :value="n" />
            </el-select>
          </div>
        </div>
      </div>
    </section>

    <!-- 加载骨架：模拟命中卡片形状 -->
    <section v-if="loading" class="results" aria-label="检索中">
      <div v-for="n in 3" :key="n" class="skeleton-hit">
        <div class="skeleton-hit__row">
          <span class="sk sk--tag" />
          <span class="sk sk--title" />
          <span class="sk sk--score" />
        </div>
        <span v-for="m in 3" :key="m" class="sk sk--line" :style="{ width: lineWidth(m) }" />
      </div>
    </section>

    <section v-else-if="searched" class="results">
      <p v-if="hits.length" class="results__summary">
        找到 <strong>{{ hits.length }}</strong> 条与「{{ lastQuery }}」相关的片段
        <span v-if="elapsedMs != null" class="results__took">{{ elapsedMs }} ms</span>
      </p>

      <article
        v-for="(hit, i) in pagedHits"
        :key="hit.chunkId"
        class="hit-card"
        title="点击查看原文定位"
        @click="openHitPreview(hit)"
      >
        <div class="hit-card__body">
          <div class="hit-card__header">
            <el-tag
              v-if="hit.docType"
              :type="docTagType(hit.docType)"
              size="small"
              effect="light"
              round
            >
              {{ docTypeLabel(hit.docType) }}
            </el-tag>
            <h3 class="hit-card__title">{{ hit.documentTitle }}</h3>
            <span class="hit-card__rank">#{{ (page - 1) * pageSize + i + 1 }}</span>
          </div>

          <div v-if="hit.docNo || hit.section || hit.pageNo || hit.anchorType === 'table'" class="hit-card__meta">
            <span v-if="hit.docNo" class="hit-chip">{{ hit.docNo }}</span>
            <span v-if="hit.section" class="hit-chip">{{ hit.section }}</span>
            <span v-if="hit.pageNo" class="hit-chip hit-chip--page">第 {{ hit.pageNo }} 页</span>
            <span v-if="hit.anchorType === 'table'" class="hit-chip hit-chip--table">表格</span>
          </div>

          <p class="hit-card__content">
            <HighlightText :text="hit.content" :query="lastQuery" />
          </p>

          <span class="hit-card__link">
            查看原文
            <el-icon><ArrowRight /></el-icon>
          </span>
        </div>

        <div class="hit-card__score" :title="`相关度 ${hit.score.toFixed(3)}`">
          <span class="hit-card__score-num">{{ scorePercent(hit) }}%</span>
          <span class="hit-card__score-bar">
            <i :style="{ width: `${scorePercent(hit)}%` }" />
          </span>
        </div>
      </article>

      <EmptyState v-if="!hits.length" variant="search-empty" />

      <div v-if="hits.length > pageSize" class="table-footer">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="hits.length"
          layout="prev, pager, next"
          background
        />
      </div>
    </section>

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
import { computed, defineComponent, h, ref } from 'vue'
import { Search as SearchIcon, Collection, Files, List, ArrowRight } from '@element-plus/icons-vue'
import KbSelect from '@/components/KbSelect.vue'
import EmptyState from '@/components/EmptyState.vue'
import { DocumentPreviewDrawer } from '@/components/async'
import { useSearchMutation } from '@/composables/queries/useSearch'
import type { SearchHit } from '@/api/search'
import { DOC_TYPES } from '@/constants'
import { ElMessage } from 'element-plus'

const TOP_K_OPTIONS = [5, 10, 15, 20, 30]

const kbId = ref<number>()
const query = ref('')
const lastQuery = ref('')
const topK = ref(10)
const docType = ref<string>()
const searched = ref(false)
const elapsedMs = ref<number>()
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
  lastQuery.value = query.value.trim()
  const startAt = performance.now()
  try {
    await searchMutation.mutateAsync({
      kbId: kbId.value,
      query: lastQuery.value,
      topK: topK.value,
      docType: docType.value || undefined
    })
    elapsedMs.value = Math.round(performance.now() - startAt)
  } catch {
    /* 错误已由 axios 拦截器提示 */
  }
}

function openHitPreview(hit: SearchHit) {
  previewDocId.value = hit.documentId
  previewChunkId.value = hit.chunkId
  previewVisible.value = true
}

function docTypeLabel(value: string) {
  return DOC_TYPES.find(t => t.value === value)?.label ?? value
}

function docTagType(value: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
    POLICY: 'primary',
    PROCESS: 'success',
    EQUIPMENT: 'warning',
    QUALITY: 'success',
    SAFETY: 'danger',
    FAQ: 'info',
    GENERAL: 'info'
  }
  return map[value] ?? 'info'
}

function scorePercent(hit: SearchHit) {
  return Math.max(1, Math.min(100, Math.round(hit.score * 100)))
}

function lineWidth(m: number) {
  return m === 3 ? '62%' : '100%'
}

/** 关键词高亮：按空白分词，命中片段包 <mark> */
const HighlightText = defineComponent({
  props: {
    text: { type: String, required: true },
    query: { type: String, default: '' }
  },
  setup(props) {
    return () => {
      const terms = props.query
        .trim()
        .split(/\s+/)
        .filter(Boolean)
        .map(t => t.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'))
      if (!terms.length) return h('span', props.text)
      const parts = props.text.split(new RegExp(`(${terms.join('|')})`, 'gi'))
      return h(
        'span',
        parts.map((part, i) => (i % 2 === 1 ? h('mark', { class: 'hit-mark' }, part) : part))
      )
    }
  }
})
</script>

<style scoped lang="scss">
.search-page {
  max-width: 960px;
  margin: 0 auto;
}

/* ---- 搜索 Hero ---- */
.search-hero {
  padding: 48px 0 28px;
  transition: padding 0.25s ease;
}

.search-hero--compact {
  padding: 4px 0 20px;
}

.search-hero__inner {
  text-align: center;
}

.search-hero__intro {
  margin-bottom: 24px;
}

.search-hero__title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: $fk-text-primary;
}

.search-hero__subtitle {
  margin: 10px 0 0;
  font-size: 14px;
  color: $fk-text-secondary;
}

.search-bar {
  display: flex;
  gap: 10px;
  max-width: 720px;
  margin: 0 auto;

  .search-hero--compact & {
    max-width: 100%;
  }
}

.search-bar__input {
  flex: 1;

  :deep(.el-input__wrapper) {
    border-radius: 10px;
    padding-left: 14px;
  }

  :deep(.el-input__inner) {
    height: 42px;
  }
}

.search-bar__btn {
  border-radius: 10px;
  padding: 0 28px;
  height: 42px;
  font-size: 15px;
}

.search-filters {
  display: flex;
  justify-content: center;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 20px;
  margin-top: 16px;

  .search-hero--compact & {
    justify-content: flex-start;
    margin-top: 12px;
  }
}

.search-filters__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.search-filters__label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: $fk-text-secondary;
  white-space: nowrap;

  .el-icon {
    font-size: 14px;
  }
}

/* ---- 结果区 ---- */
.results {
  min-height: 200px;
}

.results__summary {
  margin: 0 0 14px;
  font-size: 13px;
  color: $fk-text-secondary;

  strong {
    color: $fk-primary;
  }
}

.results__took {
  margin-left: 8px;
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 12px;
  background: $fk-surface-muted;
  border: 1px solid $fk-border;
}

.hit-card {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
  padding: 16px 18px;
  border: 1px solid $fk-border;
  border-radius: 12px;
  background: $fk-card-bg;
  box-shadow: $fk-card-shadow;
  cursor: pointer;
  transition: border-color 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;

  &:hover {
    border-color: $fk-primary;
    transform: translateY(-1px);
    box-shadow: 0 12px 28px rgba(64, 158, 255, 0.14);

    .hit-card__link {
      color: $fk-primary;

      .el-icon {
        transform: translateX(3px);
      }
    }
  }
}

.hit-card__body {
  flex: 1;
  min-width: 0;
}

.hit-card__header {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.hit-card__title {
  flex: 1;
  min-width: 0;
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: $fk-text-primary;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hit-card__rank {
  flex-shrink: 0;
  font-size: 12px;
  color: $fk-text-secondary;
  font-variant-numeric: tabular-nums;
}

.hit-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.hit-chip {
  padding: 1px 8px;
  border-radius: 6px;
  font-size: 12px;
  color: $fk-text-secondary;
  background: $fk-surface-muted;
  border: 1px solid $fk-border;
}

.hit-chip--page {
  color: $fk-primary;
  border-color: $fk-primary;
  background: transparent;
}

.hit-chip--table {
  color: var(--fk-warning);
  border-color: var(--fk-warning);
  background: transparent;
}

.hit-card__content {
  margin: 10px 0 0;
  font-size: 14px;
  line-height: 1.75;
  color: $fk-text-regular;
  word-break: break-word;
}

.hit-mark {
  padding: 0 2px;
  border-radius: 3px;
  background: var(--fk-hit-mark-bg);
  color: var(--fk-hit-mark-text);
  font-weight: 600;
}

.hit-card__link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 10px;
  font-size: 13px;
  font-weight: 500;
  color: $fk-text-secondary;
  transition: color 0.2s ease;

  .el-icon {
    transition: transform 0.2s ease;
  }
}

.hit-card__score {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  padding-top: 3px;
}

.hit-card__score-num {
  font-size: 13px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: $fk-primary;
}

.hit-card__score-bar {
  width: 52px;
  height: 4px;
  border-radius: 2px;
  background: $fk-surface-muted;
  border: 1px solid $fk-border;
  overflow: hidden;

  i {
    display: block;
    height: 100%;
    border-radius: 2px;
    background: linear-gradient(90deg, $fk-primary, $fk-primary-hover);
    transition: width 0.4s ease;
  }
}

/* ---- 骨架屏 ---- */
.skeleton-hit {
  margin-bottom: 12px;
  padding: 16px 18px;
  border: 1px solid $fk-border;
  border-radius: 12px;
  background: $fk-card-bg;

  .sk {
    display: block;
    border-radius: 6px;
    background: linear-gradient(
      90deg,
      var(--fk-skeleton-base) 25%,
      var(--fk-skeleton-shine) 50%,
      var(--fk-skeleton-base) 75%
    );
    background-size: 200% 100%;
    animation: sk-shine 1.4s ease infinite;
  }

  &__row {
    display: flex;
    gap: 10px;
    align-items: center;
    margin-bottom: 12px;
  }

  .sk--tag {
    width: 44px;
    height: 20px;
    border-radius: 10px;
  }

  .sk--title {
    flex: 1;
    height: 16px;
  }

  .sk--score {
    width: 52px;
    height: 12px;
  }

  .sk--line {
    height: 12px;
    margin-top: 8px;
  }
}

@keyframes sk-shine {
  from {
    background-position: 200% 0;
  }
  to {
    background-position: -200% 0;
  }
}

.table-footer {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
