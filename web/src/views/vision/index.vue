<template>
  <div class="page-container vision-page">
    <PageHeader title="图片问答" subtitle="上传图片并向视觉模型提问，AI 识别图片内容并回答" />

    <el-row :gutter="20">
      <el-col :xs="24" :lg="10">
        <el-card class="fk-card vision-form-card" shadow="never">
          <el-form label-width="88px">
            <el-form-item label="图片" required>
              <el-upload
                class="vision-upload"
                drag
                :auto-upload="false"
                :limit="1"
                accept="image/jpeg,image/png,image/webp,image/bmp"
                :on-change="onFileChange"
                :on-remove="() => (file = undefined)"
              >
                <el-icon class="upload-icon"><UploadFilled /></el-icon>
                <div class="el-upload__text">拖拽图片到此处，或<em>点击上传</em></div>
                <template #tip>
                  <div class="el-upload__tip">支持 JPG/PNG/WEBP/BMP，不超过 {{ maxImageMb }}MB</div>
                </template>
              </el-upload>
            </el-form-item>
            <el-form-item label="问题" required>
              <el-input
                v-model="question"
                type="textarea"
                :rows="4"
                placeholder="例如：这张图里有什么？请描述关键细节"
                maxlength="2000"
                show-word-limit
              />
            </el-form-item>
            <el-button
              type="primary"
              class="ask-btn"
              :loading="loading"
              :disabled="!file || !question.trim()"
              @click="ask"
            >
              <el-icon class="btn-icon"><Picture /></el-icon>
              提问
            </el-button>
          </el-form>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="14">
        <el-card v-if="imageUrl" class="fk-card" shadow="never">
          <template #header><span class="card-title">图片预览</span></template>
          <el-image :src="imageUrl" fit="contain" class="vision-preview" />
        </el-card>
        <el-card v-if="answer || error || loading" class="fk-card vision-answer-card" shadow="never">
          <template #header><span class="card-title">回答</span></template>
          <el-alert
            v-if="error"
            type="error"
            :title="error"
            description="请检查大模型配置与网络连通性后重试"
            show-icon
            :closable="false"
            class="vision-error-alert"
          />
          <div v-else-if="loading && !answer" class="generating-hint">
            <StreamingIndicator text="视觉模型识别中..." />
          </div>
          <MarkdownBody v-else-if="answer" :content="answer" />
        </el-card>
        <EmptyState v-else variant="qa" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadFile } from 'element-plus'
import { askAboutImage } from '@/api'
import { MarkdownBody } from '@/components/async'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import StreamingIndicator from '@/components/StreamingIndicator.vue'
import { Picture, UploadFilled } from '@element-plus/icons-vue'

const file = ref<File>()
const imageUrl = ref('')
const question = ref('')
const answer = ref('')
const error = ref('')
const loading = ref(false)
const maxImageMb = 10

function onFileChange(uploadFile: UploadFile) {
  const raw = uploadFile.raw
  if (!raw) return
  if (raw.size > maxImageMb * 1024 * 1024) {
    ElMessage.warning(`图片超过 ${maxImageMb}MB，请压缩后上传`)
    return
  }
  file.value = raw
  imageUrl.value = URL.createObjectURL(raw)
}

async function ask() {
  if (!file.value) {
    ElMessage.warning('请先上传图片')
    return
  }
  if (!question.value.trim()) {
    ElMessage.warning('请输入问题')
    return
  }
  loading.value = true
  answer.value = ''
  error.value = ''
  try {
    const res = await askAboutImage(file.value, question.value.trim())
    answer.value = res.data
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '图片问答失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.image-chat-card {
  border-radius: 12px;
  box-shadow: $fk-card-shadow;
}


.vision-upload {
  width: 100%;
}

.upload-icon {
  font-size: 32px;
  color: var(--el-color-primary);
}

.ask-btn {
  width: 100%;
  height: 40px;
}

.vision-preview {
  width: 100%;
  max-height: 320px;
}

.vision-error-alert {
  margin-bottom: 12px;
}

.card-title {
  font-weight: 600;
}
</style>
