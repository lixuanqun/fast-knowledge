<template>
  <div class="page-container writer-page">
    <PageHeader title="智能写文档" subtitle="基于知识库参考资料，AI 生成 Markdown 文档" />

    <el-row :gutter="20">
      <el-col :span="10">
        <el-card class="writer-form-card">
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
              <el-input v-model="form.topic" placeholder="文档主题" />
            </el-form-item>
            <el-form-item label="大纲">
              <el-input v-model="form.outline" type="textarea" :rows="3" placeholder="可选，列出章节结构" />
            </el-form-item>
            <el-form-item label="风格">
              <el-input v-model="form.style" placeholder="正式、专业" />
            </el-form-item>
            <el-form-item label="字数">
              <el-input-number v-model="form.wordCount" :min="200" :max="5000" style="width:100%" />
            </el-form-item>
            <el-button type="primary" :loading="generating" @click="generate">生成文档</el-button>
          </el-form>
        </el-card>
      </el-col>
      <el-col :span="14">
        <el-card v-if="content || generating" v-loading="generating" class="writer-result-card">
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
          <MarkdownBody v-if="viewMode === 'preview'" :content="content" />
          <pre v-else class="source-view">{{ content }}</pre>
        </el-card>
        <EmptyState v-else description="填写主题后点击生成" :image-size="120" />
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

<style scoped>
.writer-form-card,
.writer-result-card {
  border-radius: 10px;
  min-height: 400px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.source-view {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.7;
  font-size: 14px;
  color: #444;
}
.card-header__actions {
  display: flex;
  gap: 8px;
}
</style>
