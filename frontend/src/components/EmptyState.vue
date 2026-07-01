<template>
  <el-empty :description="description" :image-size="imageSize">
    <slot />
  </el-empty>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  description?: string
  imageSize?: number
}>(), {
  description: '暂无数据',
  imageSize: 100
})
</script>
