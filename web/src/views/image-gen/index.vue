<template>
  <div class="page-container imagegen-page">
    <PageHeader title="图片生成" subtitle="输入文字描述，AI 生成图片（文生图）" />

    <el-row :gutter="20">
      <el-col :xs="24" :lg="10">
        <el-card class="fk-card" shadow="never">
          <el-form label-width="88px">
            <el-form-item label="描述" required>
              <el-input
                v-model="prompt"
                type="textarea"
                :rows="6"
                placeholder="详细描述你想要的画面，例如：工业设备维保现场，工程师使用平板电脑巡检，科技感插画风格"
                maxlength="1000"
                show-word-limit
              />
            </el-form-item>
            <el-form-item label="尺寸">
              <el-select v-model="size" style="width: 100%">
                <el-option label="1024 × 1024（方形）" value="1024*1024" />
                <el-option label="720 × 1280（竖版）" value="720*1280" />
                <el-option label="1280 × 720（横版）" value="1280*720" />
              </el-select>
            </el-form-item>
            <el-button
              type="primary"
              class="gen-btn"
              :loading="generating"
              :disabled="!prompt.trim()"
              @click="generate"
            >
              <el-icon class="btn-icon"><MagicStick /></el-icon>
              生成图片
            </el-button>
          </el-form>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="14">
        <el-card v-if="generating || imageUrl || error" class="fk-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">生成结果</span>
              <el-button v-if="imageUrl" size="small" tag="a" :href="downloadUrl" download>
                下载图片
              </el-button>
            </div>
          </template>
          <el-alert
            v-if="error"
            type="error"
            :title="error"
            description="请稍后重试，或调整描述后再次生成"
            show-icon
            :closable="false"
            class="gen-error-alert"
          />
          <div v-else-if="generating" class="generating-hint">
            <span class="streaming-dots"><i /><i /><i /></span>
            {{ statusLabel }}
          </div>
          <el-image
            v-else-if="imageUrl"
            :src="imageUrl"
            fit="contain"
            class="gen-preview"
            :preview-src-list="[imageUrl]"
          />
        </el-card>
        <EmptyState v-else variant="writer" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getImageGenTask, submitImageGen, imageGenDownloadUrl } from '@/api'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { MagicStick } from '@element-plus/icons-vue'

const prompt = ref('')
const size = ref('1024*1024')
const generating = ref(false)
const statusLabel = ref('')
const imageUrl = ref('')
const error = ref('')

const downloadUrl = computed(() => (taskId.value ? imageGenDownloadUrl(taskId.value) : ''))
const taskId = ref('')
let timer: ReturnType<typeof setTimeout> | undefined

onBeforeUnmount(() => timer && clearTimeout(timer))

async function generate() {
  if (!prompt.value.trim()) {
    ElMessage.warning('请输入图片描述')
    return
  }
  generating.value = true
  imageUrl.value = ''
  error.value = ''
  statusLabel.value = '提交生成任务...'
  try {
    const submit = await submitImageGen(prompt.value.trim())
    taskId.value = submit.data.taskId
    await poll()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '生成失败'
    generating.value = false
  }
}

async function poll() {
  if (!taskId.value) return
  try {
    const res = await getImageGenTask(taskId.value)
    const task = res.data
    if (task.status === 'SUCCEEDED' && task.imageUrl) {
      imageUrl.value = task.imageUrl
      generating.value = false
      statusLabel.value = ''
      return
    }
    if (task.status === 'FAILED') {
      error.value = '生成任务失败'
      generating.value = false
      return
    }
    statusLabel.value = task.status === 'RUNNING' ? '正在生成图片...' : '排队中...'
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '查询任务失败'
    generating.value = false
    return
  }
  timer = setTimeout(poll, 2500)
}
</script>

<style scoped lang="scss">
.fk-card {
  border-radius: 10px;
  border: 1px solid $fk-border;
}

.gen-btn {
  width: 100%;
  height: 40px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-weight: 600;
}

.gen-preview {
  width: 100%;
  max-height: 480px;
}

.gen-error-alert {
  margin-bottom: 12px;
}
</style>
