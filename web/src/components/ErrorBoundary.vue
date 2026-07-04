<template>
  <div v-if="error" class="error-boundary">
    <el-result icon="error" title="页面异常" :sub-title="error.message || '未知错误'">
      <template #extra>
        <el-button type="primary" @click="reset">重新加载</el-button>
      </template>
    </el-result>
  </div>
  <slot v-else />
</template>

<script setup lang="ts">
import { ref, onErrorCaptured } from 'vue'

const error = ref<Error | null>(null)

onErrorCaptured((err) => {
  error.value = err
  console.error('[ErrorBoundary]', err)
  return false // 阻止向上冒泡
})

function reset() {
  error.value = null
}
</script>

<style scoped>
.error-boundary {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 360px;
  padding: 40px;
}
</style>
