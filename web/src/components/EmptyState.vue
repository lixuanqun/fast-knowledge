<template>
  <div class="fk-empty">
    <el-empty :description="meta.description" :image-size="resolvedImageSize">
      <p v-if="meta.subdescription" class="fk-empty__sub">{{ meta.subdescription }}</p>
      <slot />
    </el-empty>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

type EmptyVariant =
  | 'default'
  | 'search'
  | 'search-empty'
  | 'qa'
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
  search: { description: '选择知识库并输入关键词开始检索', imageSize: 120 },
  'search-empty': { description: '未找到相关内容，请换关键词试试', imageSize: 120 },
  qa: { description: '在左侧输入问题开始问答', imageSize: 120 },
  chat: {
    description: '发送消息开始对话',
    subdescription: '请输入您的问题，我们将为您提供准确的答案',
    imageSize: 80
  },
  'chat-sessions': {
    description: '暂无会话',
    subdescription: '点击「新对话」开始',
    imageSize: 60
  },
  'chat-empty': {
    description: '暂无消息',
    subdescription: '发送消息开始对话吧',
    imageSize: 80
  },
  writer: {
    description: '在左侧填写主题后生成文档',
    subdescription: 'AI 将基于您的主题和要求，自动生成高质量文档',
    imageSize: 120
  },
  members: {
    description: '暂无成员',
    subdescription: '邀请成员加入项目，共同协作提升效率',
    imageSize: 100
  },
  docs: { description: '暂无文档，点击右上角上传', imageSize: 100 },
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
.fk-empty__sub {
  margin: -8px 0 12px;
  font-size: 13px;
  color: $fk-text-secondary;
  line-height: 1.6;
}
</style>
