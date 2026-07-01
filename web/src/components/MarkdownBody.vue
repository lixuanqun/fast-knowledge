<template>
  <div class="markdown-body" v-html="html" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const props = defineProps<{ content: string }>()

marked.setOptions({ breaks: true, gfm: true })

const html = computed(() => {
  if (!props.content) return ''
  const raw = marked.parse(props.content) as string
  return DOMPurify.sanitize(raw)
})
</script>

<style scoped>
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  margin: 16px 0 8px;
  line-height: 1.4;
}
.markdown-body :deep(p) { margin: 8px 0; line-height: 1.7; }
.markdown-body :deep(ul),
.markdown-body :deep(ol) { padding-left: 1.5em; margin: 8px 0; }
.markdown-body :deep(code) {
  background: #f4f4f5;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 0.9em;
}
.markdown-body :deep(pre) {
  background: #f4f4f5;
  padding: 12px;
  border-radius: 8px;
  overflow-x: auto;
}
.markdown-body :deep(blockquote) {
  margin: 8px 0;
  padding-left: 12px;
  border-left: 3px solid #409eff;
  color: #666;
}
</style>
