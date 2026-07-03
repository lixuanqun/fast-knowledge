<template>
  <div class="page-container">
    <PageHeader title="智能问答" subtitle="基于知识库的单次 RAG 问答，附引用来源" />

    <el-row :gutter="20">
      <el-col :xs="24" :lg="10">
        <el-card class="fk-card qa-form-card" shadow="never">
          <el-form label-width="72px">
            <el-form-item label="知识库">
              <KbSelect v-model="kbId" width="100%" />
            </el-form-item>
            <el-form-item label="问题">
              <el-input
                v-model="question"
                type="textarea"
                :rows="5"
                placeholder="输入你的问题..."
                maxlength="2000"
                show-word-limit
              />
            </el-form-item>
            <el-button type="primary" :loading="loading" class="ask-btn" @click="handleAsk">
              <el-icon class="btn-icon"><ChatDotRound /></el-icon>
              提问
            </el-button>
          </el-form>
          <div class="sample-questions">
            <span class="sample-label">示例问题</span>
            <button
              v-for="q in samples"
              :key="q"
              type="button"
              class="sample-item"
              @click="question = q"
            >
              <el-icon><Opportunity /></el-icon>
              {{ q }}
            </button>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="14">
        <el-card v-if="hasResult || loading" v-loading="loading" class="fk-card qa-result-card" shadow="never">
          <template #header><span class="card-title">回答</span></template>
          <MarkdownBody v-if="answer" :content="answer" />
          <SourceList :sources="sources" :kb-id="kbId" @open="openSourcePreview" />
        </el-card>
        <div v-else class="qa-empty-split">
          <EmptyState variant="qa" />
          <EmptyState variant="qa-result" />
        </div>
      </el-col>
    </el-row>

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
import { MarkdownBody, DocumentPreviewDrawer } from '@/components/async'
import SourceList from '@/components/SourceList.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useAskMutation } from '@/composables/queries/useQa'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Opportunity } from '@element-plus/icons-vue'

const kbId = ref<number>()
const question = ref('')
const previewVisible = ref(false)
const previewDocId = ref<number>()
const previewChunkId = ref<number>()
const askMutation = useAskMutation()

const loading = computed(() => askMutation.isPending.value)
const answer = computed(() => askMutation.data.value?.answer || '')
const sources = computed(() => askMutation.data.value?.sources || [])
const hasResult = computed(() => askMutation.isSuccess.value)

function openSourcePreview(payload: { kbId: number; documentId: number; chunkId?: number }) {
  previewDocId.value = payload.documentId
  previewChunkId.value = payload.chunkId
  previewVisible.value = true
}

const samples = ['这份知识库主要包含哪些内容？', '请总结核心要点。']

async function handleAsk() {
  if (!kbId.value) {
    ElMessage.warning('请选择知识库')
    return
  }
  if (!question.value.trim()) {
    ElMessage.warning('请输入问题')
    return
  }
  try {
    await askMutation.mutateAsync({
      kbId: kbId.value,
      question: question.value.trim()
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

.qa-form-card,
.qa-result-card {
  min-height: 360px;
}

.card-title {
  font-weight: 600;
  color: $fk-text-primary;
}

.ask-btn {
  width: 100%;
  height: 40px;
}

.btn-icon {
  margin-right: 6px;
}

.sample-questions {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px dashed $fk-border;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sample-label {
  font-size: 13px;
  color: $fk-text-secondary;
}

.sample-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--fk-aside-note-border);
  border-radius: 8px;
  background: $fk-primary-light;
  color: $fk-primary;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.sample-item:hover {
  border-color: $fk-primary;
}

.qa-empty-split {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0;
  min-height: 360px;
  border: 1px solid $fk-border;
  border-radius: 10px;
  background: $fk-card-bg;
  overflow: hidden;
}

.qa-empty-split > :deep(.fk-empty) {
  border-right: 1px solid $fk-border;
}

@media (max-width: 992px) {
  .qa-empty-split {
    grid-template-columns: 1fr;
  }

  .qa-empty-split > :deep(.fk-empty) {
    border-right: none;
    border-bottom: 1px solid $fk-border;
  }
}
</style>
