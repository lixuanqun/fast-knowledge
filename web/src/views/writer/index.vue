<template>
  <div class="page-container writer-page">
    <PageHeader title="智能写文档" subtitle="基于知识库参考资料，AI 生成 Markdown 文档" />

    <el-row :gutter="20">
      <el-col :xs="24" :lg="10">
        <el-card class="fk-card writer-form-card" shadow="never">
          <el-form label-width="88px">
            <el-form-item label="知识库（可选）">
              <KbSelect
                v-model="form.kbId"
                width="100%"
                :auto-default="false"
                clearable
                placeholder="请选择知识库"
              />
            </el-form-item>
            <el-form-item label="主题" required>
              <el-input v-model="form.topic" placeholder="文档主题" maxlength="100" show-word-limit />
            </el-form-item>
            <el-form-item label="大纲">
              <el-input
                v-model="form.outline"
                type="textarea"
                :rows="8"
                placeholder="可选。留空将由 AI 自动规划章节结构；也可手动列出章节"
                maxlength="2000"
                show-word-limit
              />
            </el-form-item>
            <el-form-item label="风格">
              <el-select v-model="form.style" style="width:100%">
                <el-option label="正式、专业" value="正式、专业" />
                <el-option label="轻松、易懂" value="轻松、易懂" />
                <el-option label="技术、严谨" value="技术、严谨" />
              </el-select>
            </el-form-item>
            <el-form-item label="字数">
              <el-input-number v-model="form.wordCount" :min="200" :max="5000" style="width:100%" />
            </el-form-item>
            <el-button type="primary" class="generate-btn" :loading="generating" @click="generate">
              <el-icon class="btn-icon"><MagicStick /></el-icon>
              生成文档
            </el-button>
          </el-form>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="14">
        <el-card v-if="content || errorMsg || generating" v-loading="generating" class="fk-card writer-result-card" shadow="never">
          <template #header>
            <div class="card-header">
              <div class="preview-tabs">
                <button
                  type="button"
                  :class="['preview-tab', { active: viewMode === 'preview' }]"
                  @click="viewMode = 'preview'"
                >
                  预览
                </button>
                <button
                  type="button"
                  :class="['preview-tab', { active: viewMode === 'source' }]"
                  @click="viewMode = 'source'"
                >
                  源码
                </button>
              </div>
              <div class="card-header__actions">
                <el-button size="small" @click="copyContent">
                  <el-icon class="btn-icon"><DocumentCopy /></el-icon>
                  复制
                </el-button>
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
            <StreamingIndicator :text="stage || '正在生成文档...'" />
          </div>
          <div v-if="generating && stage" class="writer-stage">{{ stage }}</div>
          <el-alert
            v-if="errorMsg"
            type="error"
            :title="errorMsg"
            description="请检查大模型配置与网络连通性后重试"
            show-icon
            :closable="false"
            class="writer-error-alert"
          />
          <MarkdownBody v-else-if="viewMode === 'preview'" :content="content" />
          <pre v-else class="source-view">{{ content }}</pre>
        </el-card>
        <EmptyState v-else variant="writer" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onBeforeUnmount } from 'vue'
import { streamWriter } from '@/api'
import type { WriterStep } from '@/api'
import PageHeader from '@/components/PageHeader.vue'
import KbSelect from '@/components/KbSelect.vue'
import { MarkdownBody } from '@/components/async'
import EmptyState from '@/components/EmptyState.vue'
import StreamingIndicator from '@/components/StreamingIndicator.vue'
import { useSaveWriterDocumentMutation } from '@/composables/queries/useWriter'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DocumentCopy, MagicStick } from '@element-plus/icons-vue'

const generating = ref(false)
const content = ref('')
const stage = ref('')
const errorMsg = ref('')
const viewMode = ref<'preview' | 'source'>('preview')

let writerAbort: AbortController | null = null
onBeforeUnmount(() => {
  writerAbort?.abort()
})

function stageLabel(step: WriterStep): string {
  switch (step.stage) {
    case 'planOutline':
      return '大纲规划中...'
    case 'draftSection':
      return `分节撰写 ${step.sectionIndex ?? 1}/${step.sectionTotal ?? 1}${step.title ? '：' + step.title : ''}`
    case 'cite':
      return '引用整理中...'
    case 'polish':
      return '润色输出中...'
    default:
      return '生成中...'
  }
}
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
  stage.value = ''
  errorMsg.value = ''
  viewMode.value = 'preview'
  try {
    writerAbort = new AbortController()
    await streamWriter(form, chunk => {
      content.value += chunk
    }, step => {
      stage.value = stageLabel(step)
    }, writerAbort.signal)
  } catch (e: unknown) {
    const message = e instanceof Error ? e.message : '生成失败'
    errorMsg.value = message
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
.writer-form-card {
  border-radius: 12px;
  box-shadow: $fk-card-shadow;
}

.fk-card {
  border-radius: 12px;
  box-shadow: $fk-card-shadow;
  border: 1px solid $fk-border;
}

.writer-form-card,
.writer-result-card {
  min-height: 420px;
}

.writer-error-alert {
  margin-bottom: 8px;
}

.writer-stage {
  padding: 4px 8px;
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: 6px;
  display: inline-block;
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

.preview-tabs {
  display: flex;
  gap: 20px;
}

.preview-tab {
  background: none;
  border: none;
  padding: 0 0 6px;
  font-size: 14px;
  color: $fk-text-regular;
  cursor: pointer;
  border-bottom: 2px solid transparent;
}

.preview-tab.active {
  color: $fk-primary;
  font-weight: 600;
  border-bottom-color: $fk-primary;
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



.btn-icon {
  margin-right: 4px;
}
</style>
