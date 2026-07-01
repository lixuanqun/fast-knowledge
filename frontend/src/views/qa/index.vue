<template>
  <div class="page-container">
    <PageHeader title="智能问答" subtitle="基于知识库的单次 RAG 问答，附引用来源" />

    <el-row :gutter="20">
      <el-col :span="10">
        <el-card class="qa-form-card">
          <el-form label-width="72px">
            <el-form-item label="知识库">
              <KbSelect v-model="kbId" width="100%" />
            </el-form-item>
            <el-form-item label="问题">
              <el-input v-model="question" type="textarea" :rows="5" placeholder="输入你的问题..." />
            </el-form-item>
            <el-button type="primary" :loading="loading" @click="handleAsk">提问</el-button>
          </el-form>
          <div class="sample-questions">
            <span class="sample-label">示例：</span>
            <el-tag
              v-for="q in samples"
              :key="q"
              class="sample-tag"
              effect="plain"
              @click="question = q"
            >{{ q }}</el-tag>
          </div>
        </el-card>
      </el-col>
      <el-col :span="14">
        <el-card v-if="hasResult || loading" v-loading="loading" class="qa-result-card">
          <template #header>回答</template>
          <MarkdownBody v-if="answer" :content="answer" />
          <SourceList :sources="sources" />
        </el-card>
        <EmptyState v-else description="在左侧输入问题开始问答" :image-size="120" />
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

<style scoped>
.qa-form-card, .qa-result-card { border-radius: 10px; min-height: 320px; }
.sample-questions { margin-top: 16px; display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.sample-label { font-size: 13px; color: #888; }
.sample-tag { cursor: pointer; }
</style>
