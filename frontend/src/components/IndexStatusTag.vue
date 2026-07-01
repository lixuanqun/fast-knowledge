<template>
  <el-tag :type="meta.type" size="small">{{ meta.label }}</el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { indexStatusMeta } from '@/utils/format'

const props = defineProps<{ status: string }>()
const meta = computed(() => indexStatusMeta(props.status))
</script>
