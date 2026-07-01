<template>
  <div class="page-container">
    <PageHeader title="智能问答" subtitle="基于知识库的单次 RAG 问答，附引用来源" />

    <el-row :gutter="20">
      <el-col :xs="24" :lg="10">
        <el-card class="fk-card qa-form-card" shadow="never">
          <template #header><span class="card-title">提问</span></template>
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
                maxlength="500"
                show-word-limit
              />
            </el-form-item>
            <el-button type="primary" :loading="loading" class="ask-btn" @click="handleAsk">
              提问
            </el-button>
          </el-form>
          <div class="sample-questions">
            <span class="sample-label">示例问题：</span>
            <el-tag
              v-for="q in samples"
              :key="q"
              class="sample-tag"
              effect="plain"
              round
              @click="question = q"
            >
              {{ q }}
            </el-tag>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="14">
        <el-card v-if="hasResult || loading" v-loading="loading" class="fk-card qa-result-card" shadow="never">
          <template #header>
            <div class="result-header">
              <span class="card-title">回答</span>
              <el-tag v-if="hasResult" size="small" type="success" effect="plain">已完成</el-tag>
            </div>
          </template>
          <MarkdownBody v-if="answer" :content="answer" />
          <SourceList :sources="sources" />
        </el-card>
        <EmptyState v-else variant="qa" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import KbSelect from '@/components/KbSelect.vue'
import { MarkdownBody } from '@/components/async'
import SourceList from '@/components/SourceList.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useAskMutation } from '@/composables/queries/useQa'
import { ElMessage } from 'element-plus'

const kbId = ref<number>()
const question = ref('')
const askMutation = useAskMutation()

const loading = computed(() => askMutation.isPending.value)
const answer = computed(() => askMutation.data.value?.answer || '')
const sources = computed(() => askMutation.data.value?.sources || [])
const hasResult = computed(() => askMutation.isSuccess.value)

const samples = ['这份知识库主要包含哪些内容？', '请总结核心要点', '有哪些操作流程说明？']

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

.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.ask-btn {
  width: 100%;
  height: 40px;
}

.sample-questions {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px dashed $fk-border;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.sample-label {
  font-size: 13px;
  color: $fk-text-secondary;
}

.sample-tag {
  cursor: pointer;
}
</style>
