<template>
  <div v-if="sources?.length" class="source-list">
    <h4 class="source-list__title">引用来源</h4>
    <div
      v-for="(s, i) in sources"
      :key="i"
      class="source-card"
      :class="{ 'source-card--clickable': canOpen(s) }"
      @click="handleClick(s)"
    >
      <div class="source-card__header">
        <el-icon><Document /></el-icon>
        <strong>{{ s.documentTitle || '文档' }}</strong>
        <span v-if="s.docNo" class="source-card__badge">{{ s.docNo }}</span>
        <span v-if="s.score != null" class="source-card__score">{{ s.score.toFixed(3) }}</span>
      </div>
      <p v-if="s.section || s.docType" class="source-card__section">
        <span v-if="s.docType">{{ s.docType }}</span>
        <span v-if="s.docType && s.section"> · </span>
        <span v-if="s.section">{{ s.section }}</span>
      </p>
      <div class="source-card__snippet">
        <span class="source-card__snippet-label">引用片段</span>
        <p>{{ s.content }}</p>
      </div>
      <p v-if="canOpen(s)" class="source-card__link-hint">点击查看原文定位</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Document } from '@element-plus/icons-vue'

export interface SourceItem {
  documentId?: number
  chunkId?: number
  documentTitle?: string
  content: string
  score?: number
  section?: string
  docType?: string
  docNo?: string
}

const props = defineProps<{
  sources?: SourceItem[]
  kbId?: number
}>()

const emit = defineEmits<{
  open: [payload: { kbId: number; documentId: number; chunkId?: number }]
}>()

function canOpen(s: SourceItem) {
  return props.kbId != null && s.documentId != null && s.documentId > 0
}

function handleClick(s: SourceItem) {
  if (!canOpen(s)) return
  emit('open', {
    kbId: props.kbId!,
    documentId: s.documentId!,
    chunkId: s.chunkId
  })
}
</script>

<style scoped lang="scss">
.source-list {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px dashed $fk-border;
}

.source-list__title {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: $fk-text-regular;
}

.source-card {
  margin-bottom: 10px;
  padding: 12px 14px;
  border: 1px solid var(--fk-aside-note-border);
  border-radius: 8px;
  background: $fk-primary-light;
}

.source-card--clickable {
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;

  &:hover {
    border-color: $fk-primary;
    box-shadow: 0 0 0 1px rgba(64, 158, 255, 0.12);
  }
}

.source-card__header {
  display: flex;
  align-items: center;
  gap: 8px;
  color: $fk-primary;
  margin-bottom: 6px;
}

.source-card__header strong {
  flex: 1;
  font-size: 14px;
  color: $fk-text-primary;
}

.source-card__badge {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--fk-hit-score-bg);
  color: $fk-text-secondary;
}

.source-card__score {
  font-size: 12px;
  color: $fk-text-secondary;
}

.source-card__section {
  margin: 0 0 8px;
  font-size: 12px;
  color: $fk-text-secondary;
}

.source-card__snippet-label {
  display: block;
  font-size: 12px;
  color: $fk-text-secondary;
  margin-bottom: 4px;
}

.source-card__snippet p {
  margin: 0;
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.7;
  color: $fk-text-regular;
}

.source-card__link-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: $fk-primary;
}
</style>
