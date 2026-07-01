<template>
  <el-drawer
    :model-value="visible"
    :title="doc?.title || '文档详情'"
    size="52%"
    destroy-on-close
    @update:model-value="$emit('update:visible', $event)"
  >
    <div v-loading="loading" class="preview-drawer">
      <el-descriptions v-if="doc" :column="2" size="small" border class="meta">
        <el-descriptions-item label="文件名">{{ doc.fileName }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ doc.fileType }}</el-descriptions-item>
        <el-descriptions-item label="大小">{{ formatFileSize(doc.fileSize) }}</el-descriptions-item>
        <el-descriptions-item label="索引状态">
          <IndexStatusTag :status="doc.indexStatus" />
        </el-descriptions-item>
        <el-descriptions-item label="分块数">{{ doc.chunkCount }}</el-descriptions-item>
      </el-descriptions>

      <el-tabs v-model="tab" class="preview-tabs">
        <el-tab-pane label="内容预览" name="preview">
          <el-alert
            v-if="preview?.truncated"
            type="warning"
            :closable="false"
            show-icon
            title="内容过长，仅展示前 20 万字符"
            style="margin-bottom:12px"
          />
          <el-tag v-if="preview" size="small" type="info" style="margin-bottom:12px">
            {{ preview.previewMode === 'raw' ? '原文' : 'Tika 提取文本' }}
          </el-tag>
          <MarkdownBody v-if="preview && isMarkdown" :content="preview.content" />
          <pre v-else-if="preview" class="text-preview">{{ preview.content }}</pre>
          <EmptyState v-else description="暂无预览内容" />
        </el-tab-pane>
        <el-tab-pane name="chunks">
          <template #label>
            分块列表
            <el-badge v-if="chunks.length" :value="chunks.length" class="chunk-badge" />
          </template>
          <el-empty v-if="!chunks.length && !loading" description="暂无分块，请等待索引完成" />
          <el-collapse v-else accordion>
            <el-collapse-item
              v-for="chunk in chunks"
              :key="chunk.id"
              :title="`#${chunk.chunkIndex + 1} · ${chunk.tokenCount} 字`"
            >
              <pre class="chunk-content">{{ chunk.content }}</pre>
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
import { formatFileSize } from '@/utils/format'
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
}>()

defineEmits<{ 'update:visible': [value: boolean] }>()

const tab = ref('preview')
const activeDocId = ref<number>()

const enabled = computed(() => props.visible && activeDocId.value != null && activeDocId.value > 0)

const { data: doc, isLoading: docLoading, isError: docError } = useDocumentQuery(
  () => props.kbId,
  activeDocId
)
const { data: preview, isLoading: previewLoading, isError: previewError } = useDocumentPreviewQuery(
  () => props.kbId,
  activeDocId
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

const isMarkdown = computed(() => {
  const type = preview.value?.fileType?.toLowerCase() || doc.value?.fileType?.toLowerCase()
  return type === 'md'
})

watch(
  () => [props.visible, props.docId] as const,
  ([visible, docId]) => {
    if (!visible || !docId) {
      activeDocId.value = undefined
      return
    }
    tab.value = 'preview'
    activeDocId.value = docId
  },
  { immediate: true }
)

watch(hasError, failed => {
  if (failed && enabled.value) {
    ElMessage.error('加载文档失败')
  }
})
</script>

<style scoped>
.preview-drawer { min-height: 200px; }
.meta { margin-bottom: 16px; }
.preview-tabs { margin-top: 8px; }
.text-preview, .chunk-content {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.7;
  font-size: 14px;
  color: #444;
  max-height: 60vh;
  overflow: auto;
}
.chunk-badge { margin-left: 6px; }
</style>
