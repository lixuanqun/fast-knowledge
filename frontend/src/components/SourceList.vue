<template>
  <div v-if="sources?.length" class="source-list">
    <h4 class="source-list__title">引用来源</h4>
    <el-collapse>
      <el-collapse-item
        v-for="(s, i) in sources"
        :key="i"
        :title="`${s.documentTitle || '文档'} · 相关度 ${(s.score ?? 0).toFixed(3)}`"
      >
        <p class="source-list__content">{{ s.content }}</p>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
export interface SourceItem {
  documentTitle?: string
  content: string
  score?: number
}

defineProps<{ sources?: SourceItem[] }>()
</script>

<style scoped>
.source-list { margin-top: 16px; }
.source-list__title { margin: 0 0 8px; font-size: 14px; color: #666; }
.source-list__content { margin: 0; white-space: pre-wrap; color: #666; font-size: 13px; line-height: 1.6; }
</style>
