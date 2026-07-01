<template>
  <div v-if="sources?.length" class="source-list">
    <div class="source-list__header">
      <el-icon><Document /></el-icon>
      <h4 class="source-list__title">引用来源（{{ sources.length }}）</h4>
    </div>
    <el-collapse v-model="activeNames" class="source-collapse">
      <el-collapse-item
        v-for="(s, i) in sources"
        :key="i"
        :name="i"
        :title="`${s.documentTitle || '文档'} · 相关度 ${(s.score ?? 0).toFixed(3)}`"
      >
        <p class="source-list__content">{{ s.content }}</p>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Document } from '@element-plus/icons-vue'

export interface SourceItem {
  documentTitle?: string
  content: string
  score?: number
}

const props = defineProps<{ sources?: SourceItem[] }>()

const activeNames = ref<number[]>([0])

watch(
  () => props.sources,
  list => {
    activeNames.value = list?.length ? [0] : []
  },
  { immediate: true }
)
</script>

<style scoped lang="scss">
.source-list {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px dashed $fk-border;
}

.source-list__header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  color: $fk-text-regular;
}

.source-list__title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: $fk-text-regular;
}

.source-collapse {
  border: none;
}

.source-collapse :deep(.el-collapse-item__header) {
  font-size: 13px;
  color: $fk-text-primary;
}

.source-collapse :deep(.el-collapse-item.is-active .el-collapse-item__header) {
  color: $fk-primary;
}

.source-list__content {
  margin: 0;
  white-space: pre-wrap;
  color: $fk-text-regular;
  font-size: 13px;
  line-height: 1.7;
  background: #fafafa;
  padding: 10px 12px;
  border-radius: 6px;
  border-left: 3px solid $fk-primary;
}
</style>
