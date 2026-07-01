<template>
  <el-tag :type="meta.type" effect="light" size="small" class="index-status-tag">
    <el-icon v-if="meta.spinning" class="is-loading"><Loading /></el-icon>
    <el-icon v-else><component :is="iconComponent" /></el-icon>
    <span>{{ meta.label }}</span>
    <span v-if="showChunks" class="chunk-extra">· {{ chunkCount }} chunks</span>
  </el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CircleCheck, CircleClose, Clock, Loading } from '@element-plus/icons-vue'
import { indexStatusMeta } from '@/utils/format'

const ICON_MAP = {
  Clock,
  Loading,
  CircleCheck,
  CircleClose
} as const

const props = defineProps<{
  status: string
  chunkCount?: number
}>()

const meta = computed(() => indexStatusMeta(props.status))
const iconComponent = computed(() => ICON_MAP[meta.value.icon])

const showChunks = computed(
  () => props.status === 'INDEXED' && props.chunkCount != null && props.chunkCount > 0
)
</script>

<style scoped>
.index-status-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
}

.index-status-tag .el-icon {
  font-size: 13px;
}

.chunk-extra {
  font-weight: 400;
  opacity: 0.9;
}
</style>
