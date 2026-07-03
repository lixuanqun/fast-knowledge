<template>
  <div class="fk-empty">
    <EmptyIllustration :variant="variant" :size="resolvedImageSize" />
    <p class="fk-empty__desc">{{ meta.description }}</p>
    <p v-if="meta.subdescription" class="fk-empty__sub">{{ meta.subdescription }}</p>
    <slot />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import EmptyIllustration from '@/components/design/EmptyIllustration.vue'

type EmptyVariant =
  | 'default'
  | 'kbs'
  | 'search'
  | 'search-empty'
  | 'qa'
  | 'qa-result'
  | 'chat'
  | 'chat-sessions'
  | 'chat-empty'
  | 'writer'
  | 'members'
  | 'docs'
  | 'tasks'

const VARIANTS: Record<
  EmptyVariant,
  { description: string; subdescription?: string; imageSize: number }
> = {
  default: { description: '暂无数据', imageSize: 100 },
  kbs: { description: '暂无知识库，点击右上角新建', imageSize: 110 },
  search: { description: '选择知识库并输入关键词开始检索', imageSize: 110 },
  'search-empty': { description: '未找到相关内容，请换关键词试试', imageSize: 110 },
  qa: { description: '在左侧输入问题开始问答', imageSize: 100 },
  'qa-result': { description: '问答结果将显示在这里', imageSize: 100 },
  chat: { description: '发送消息开始对话', imageSize: 90 },
  'chat-sessions': { description: '暂无会话', subdescription: '点击「新对话」开始', imageSize: 80 },
  'chat-empty': { description: '暂无消息', subdescription: '发送消息开始对话吧', imageSize: 90 },
  writer: { description: '在左侧填写主题后生成文档', imageSize: 110 },
  members: { description: '暂无成员', imageSize: 100 },
  docs: { description: '暂无文档，点击右上角上传', imageSize: 110 },
  tasks: { description: '暂无失败任务', imageSize: 100 }
}

const props = withDefaults(
  defineProps<{
    variant?: EmptyVariant
    description?: string
    imageSize?: number
  }>(),
  {
    variant: 'default'
  }
)

const meta = computed(() => {
  const base = VARIANTS[props.variant]
  return {
    description: props.description ?? base.description,
    subdescription: base.subdescription,
    imageSize: props.imageSize ?? base.imageSize
  }
})

const resolvedImageSize = computed(() => props.imageSize ?? meta.value.imageSize)
</script>

<style scoped lang="scss">
.fk-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  text-align: center;
}

.fk-empty__desc {
  margin: 16px 0 0;
  font-size: 14px;
  color: $fk-text-secondary;
}

.fk-empty__sub {
  margin: 8px 0 12px;
  font-size: 13px;
  color: $fk-text-secondary;
  line-height: 1.6;
}
</style>
