<template>
  <div class="page-container writer-page">
    <PageHeader title="智能写文档" subtitle="基于知识库参考资料，AI 生成 Markdown 文档" />

    <el-row :gutter="20">
      <el-col :xs="24" :lg="10">
        <el-card class="fk-card writer-form-card" shadow="never">
          <template #header><span class="card-title">生成参数</span></template>
          <el-form label-width="72px">
            <el-form-item label="知识库">
              <KbSelect
                v-model="form.kbId"
                width="100%"
                :auto-default="false"
                clearable
                placeholder="可选，用于引用资料"
              />
            </el-form-item>
            <el-form-item label="主题" required>
              <el-input v-model="form.topic" placeholder="文档主题" maxlength="100" show-word-limit />
            </el-form-item>
            <el-form-item label="大纲">
              <el-input
                v-model="form.outline"
                type="textarea"
                :rows="3"
                placeholder="可选，列出章节结构"
                maxlength="500"
                show-word-limit
              />
            </el-form-item>
            <el-form-item label="风格">
              <el-input v-model="form.style" placeholder="正式、专业" />
            </el-form-item>
            <el-form-item label="字数">
              <el-input-number v-model="form.wordCount" :min="200" :max="5000" style="width:100%" />
            </el-form-item>
            <el-button type="primary" class="generate-btn" :loading="generating" @click="generate">
              生成文档
            </el-button>
          </el-form>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="14">
        <el-card v-if="content || generating" v-loading="generating" class="fk-card writer-result-card" shadow="never">
          <template #header>
            <div class="card-header">
              <el-radio-group v-model="viewMode" size="small">
                <el-radio-button value="preview">预览</el-radio-button>
                <el-radio-button value="source">源码</el-radio-button>
              </el-radio-group>
              <div class="card-header__actions">
                <el-button size="small" @click="copyContent">复制</el-button>
                <el-button
                  v-if="form.kbId"
                  type="primary"
                  size="small"
                  :loading="saveMutation.isPending.value"
                  @click="handleSave"
                >
                  保存到知识库
                </el-button>
              </div>
            </div>
          </template>
          <div v-if="generating && !content" class="generating-hint">
            <span class="streaming-dots"><i /><i /><i /></span>
            正在生成文档...
          </div>
          <MarkdownBody v-else-if="viewMode === 'preview'" :content="content" />
          <pre v-else class="source-view">{{ content }}</pre>
        </el-card>
        <EmptyState v-else variant="writer" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { streamWriter } from '@/api'
import PageHeader from '@/components/PageHeader.vue'
import KbSelect from '@/components/KbSelect.vue'
import { MarkdownBody } from '@/components/async'
import EmptyState from '@/components/EmptyState.vue'
import { useSaveWriterDocumentMutation } from '@/composables/queries/useWriter'
import { ElMessage, ElMessageBox } from 'element-plus'

const generating = ref(false)
const content = ref('')
const viewMode = ref<'preview' | 'source'>('preview')
const saveMutation = useSaveWriterDocumentMutation()

const form = reactive({
  kbId: undefined as number | undefined,
  topic: '',
  outline: '',
  style: '正式、专业',
  wordCount: 800
})

async function generate() {
  if (!form.topic.trim()) {
    ElMessage.warning('请填写文档主题')
    return
  }
  generating.value = true
  content.value = ''
  viewMode.value = 'preview'
  try {
    await streamWriter(form, chunk => {
      content.value += chunk
    })
  } catch (e: unknown) {
    const message = e instanceof Error ? e.message : '生成失败'
    ElMessage.error(message)
  } finally {
    generating.value = false
  }
}

async function copyContent() {
  await navigator.clipboard.writeText(content.value)
  ElMessage.success('已复制到剪贴板')
}

async function handleSave() {
  if (!form.kbId) {
    ElMessage.warning('请选择知识库')
    return
  }
  if (!content.value.trim()) {
    ElMessage.warning('暂无内容可保存')
    return
  }
  const { value } = await ElMessageBox.prompt('请输入文档标题', '保存到知识库', { inputValue: form.topic })
  if (!value?.trim()) return
  try {
    await saveMutation.mutateAsync({
      kbId: form.kbId,
      title: value.trim(),
      content: content.value
    })
    ElMessage.success('已保存并开始索引')
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

.writer-form-card,
.writer-result-card {
  min-height: 420px;
}

.card-title {
  font-weight: 600;
  color: $fk-text-primary;
}

.generate-btn {
  width: 100%;
  height: 40px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.card-header__actions {
  display: flex;
  gap: 8px;
}

.source-view {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.75;
  font-size: 13px;
  font-family: 'Cascadia Code', 'Fira Code', Consolas, monospace;
  background: var(--fk-code-bg);
  color: var(--fk-code-text);
  padding: 16px;
  border-radius: 8px;
  max-height: 520px;
  overflow: auto;
}

.generating-hint {
  display: flex;
  align-items: center;
  gap: 10px;
  color: $fk-text-secondary;
  padding: 40px 0;
  justify-content: center;
}

.streaming-dots {
  display: inline-flex;
  gap: 4px;
}

.streaming-dots i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: $fk-primary;
  animation: dot-bounce 1.2s infinite ease-in-out;
}

.streaming-dots i:nth-child(2) {
  animation-delay: 0.15s;
}

.streaming-dots i:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes dot-bounce {
  0%,
  80%,
  100% {
    opacity: 0.3;
    transform: scale(0.8);
  }
  40% {
    opacity: 1;
    transform: scale(1);
  }
}
</style>
