<template>
  <el-drawer
    :model-value="visible"
    :title="doc?.title || '文档详情'"
    size="52%"
    destroy-on-close
    class="doc-preview-drawer"
    @update:model-value="$emit('update:visible', $event)"
  >
    <div v-loading="loading" class="preview-drawer">
      <el-descriptions v-if="doc" :column="2" size="small" border class="meta">
        <el-descriptions-item label="文件名">{{ doc.fileName }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ doc.fileType }}</el-descriptions-item>
        <el-descriptions-item label="大小">{{ formatFileSize(doc.fileSize) }}</el-descriptions-item>
        <el-descriptions-item label="索引状态">
          <IndexStatusTag :status="doc.indexStatus" :chunk-count="doc.chunkCount" />
        </el-descriptions-item>
        <el-descriptions-item label="分块数">{{ doc.chunkCount }}</el-descriptions-item>
        <el-descriptions-item v-if="docCreatedAt" label="上传时间">
          {{ formatDateTime(docCreatedAt) }}
        </el-descriptions-item>
        <el-descriptions-item v-if="doc.docType" label="文档类型">{{ doc.docType }}</el-descriptions-item>
        <el-descriptions-item v-if="doc.docNo" label="文号">{{ doc.docNo }}</el-descriptions-item>
        <el-descriptions-item v-if="doc.department" label="部门">{{ doc.department }}</el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="preview?.highlightSnippet"
        type="info"
        :closable="false"
        show-icon
        class="highlight-alert"
        title="引用定位"
      >
        <pre class="highlight-snippet">{{ preview.highlightSnippet }}</pre>
      </el-alert>

      <el-tabs v-model="tab" class="preview-tabs">
        <el-tab-pane label="内容预览" name="preview">
          <el-alert
            v-if="preview?.truncated"
            type="warning"
            :closable="false"
            show-icon
            class="truncate-alert"
          >
            <template #title>内容过长，仅展示前 20 万字符</template>
          </el-alert>
          <el-tag v-if="preview" size="small" type="info" effect="plain" class="preview-mode-tag">
            {{ preview.previewMode === 'raw' ? '原文' : 'Tika 提取文本' }}
          </el-tag>
          <MarkdownBody v-if="preview && isMarkdown" :content="preview.content" />
          <pre v-else-if="preview" class="text-preview">{{ preview.content }}</pre>
          <EmptyState v-else variant="docs" description="暂无预览内容" />
        </el-tab-pane>
        <el-tab-pane name="chunks">
          <template #label>
            分块列表
            <el-badge v-if="chunks.length" :value="chunks.length" class="chunk-badge" />
          </template>
          <EmptyState
            v-if="!chunks.length && !loading"
            variant="docs"
            description="暂无分块，请等待索引完成"
          />
          <el-collapse v-else v-model="activeChunk" accordion class="chunk-collapse">
            <el-collapse-item
              v-for="chunk in chunks"
              :key="chunk.id"
              :name="chunk.id"
            >
              <template #title>
                <div class="chunk-title">
                  <span class="chunk-title__index">#{{ chunk.chunkIndex + 1 }}</span>
                  <span v-if="chunk.sectionTitle" class="chunk-title__section">{{ chunk.sectionTitle }}</span>
                  <span class="chunk-title__meta">{{ chunk.tokenCount }} 字</span>
                  <span class="chunk-title__preview">{{ chunkPreview(chunk.content) }}</span>
                </div>
              </template>
              <pre :class="['chunk-content', { 'chunk-content--highlight': chunk.id === activeChunkId }]">{{ chunk.content }}</pre>
            </el-collapse-item>
          </el-collapse>
        </el-tab-pane>
      </el-tabs>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import MarkdownBody from '@/components/MarkdownBody.vue'
import IndexStatusTag from '@/components/IndexStatusTag.vue'
import EmptyState from '@/components/EmptyState.vue'
import { formatDateTime, formatFileSize } from '@/utils/format'
import { ElMessage } from 'element-plus'
import {
  useDocumentChunksQuery,
  useDocumentPreviewQuery,
  useDocumentQuery
} from '@/composables/queries/useKbDetail'

const props = defineProps<{
  visible: boolean
  kbId: number
  docId?: number
  highlightChunkId?: number
}>()

defineEmits<{ 'update:visible': [value: boolean] }>()

const tab = ref('preview')
const activeDocId = ref<number>()
const activeChunkId = ref<number>()
const activeChunk = ref<number>()

const enabled = computed(() => props.visible && activeDocId.value != null && activeDocId.value > 0)

const { data: doc, isLoading: docLoading, isError: docError } = useDocumentQuery(
  () => props.kbId,
  activeDocId
)
const { data: preview, isLoading: previewLoading, isError: previewError } = useDocumentPreviewQuery(
  () => props.kbId,
  activeDocId,
  activeChunkId
)
const { data: chunksData, isLoading: chunksLoading, isError: chunksError } = useDocumentChunksQuery(
  () => props.kbId,
  activeDocId
)

const chunks = computed(() => chunksData.value || [])
const loading = computed(
  () =>
    enabled.value &&
    (docLoading.value || previewLoading.value || chunksLoading.value)
)
const hasError = computed(() => docError.value || previewError.value || chunksError.value)

const docCreatedAt = computed(() => {
  const d = doc.value as { createdAt?: string } | undefined
  return d?.createdAt
})

const isMarkdown = computed(() => {
  const type = preview.value?.fileType?.toLowerCase() || doc.value?.fileType?.toLowerCase()
  return type === 'md'
})

function chunkPreview(content: string) {
  const line = content.replace(/\s+/g, ' ').trim()
  return line.length > 48 ? `${line.slice(0, 48)}…` : line
}

watch(
  () => [props.visible, props.docId, props.highlightChunkId] as const,
  ([visible, docId, chunkId]) => {
    if (!visible || !docId) {
      activeDocId.value = undefined
      activeChunkId.value = undefined
      return
    }
    tab.value = chunkId ? 'chunks' : 'preview'
    activeDocId.value = docId
    activeChunkId.value = chunkId
  },
  { immediate: true }
)

watch(chunks, list => {
  if (activeChunkId.value) {
    activeChunk.value = activeChunkId.value
  } else if (list.length) {
    activeChunk.value = list[0].id
  }
})

watch(hasError, failed => {
  if (failed && enabled.value) {
    ElMessage.error('加载文档失败')
  }
})
</script>

<style scoped lang="scss">
.preview-drawer {
  min-height: 200px;
}

.meta {
  margin-bottom: 16px;
}

.preview-tabs {
  margin-top: 8px;
}

.truncate-alert {
  margin-bottom: 12px;
}

.preview-mode-tag {
  margin-bottom: 12px;
}

.text-preview,
.chunk-content {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.75;
  font-size: 14px;
  color: $fk-text-regular;
  max-height: 60vh;
  overflow: auto;
  background: $fk-surface-muted;
  padding: 12px 14px;
  border-radius: 8px;
}

.chunk-badge {
  margin-left: 6px;
}

.chunk-collapse {
  border: none;
}

.chunk-collapse :deep(.el-collapse-item) {
  margin-bottom: 8px;
  border: 1px solid $fk-border;
  border-radius: 8px;
  overflow: hidden;
}

.chunk-collapse :deep(.el-collapse-item.is-active) {
  border-color: $fk-primary;
  box-shadow: 0 0 0 1px rgba(64, 158, 255, 0.15);
}

.chunk-collapse :deep(.el-collapse-item__header) {
  padding: 0 12px;
  height: auto;
  min-height: 44px;
  line-height: 1.5;
}

.chunk-title {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 8px 0;
  font-size: 13px;
}

.chunk-title__index {
  font-weight: 600;
  color: $fk-primary;
  flex-shrink: 0;
}

.chunk-title__meta {
  color: $fk-text-secondary;
  flex-shrink: 0;
}

.chunk-title__preview {
  color: $fk-text-regular;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.chunk-title__section {
  color: $fk-primary;
  flex-shrink: 0;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chunk-content {
  margin: 0 12px 12px;
  max-height: 40vh;
}

.chunk-content--highlight {
  border: 1px solid $fk-primary;
  background: $fk-primary-light;
}

.highlight-alert {
  margin-bottom: 12px;
}

.highlight-snippet {
  margin: 0;
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.6;
}
</style>
