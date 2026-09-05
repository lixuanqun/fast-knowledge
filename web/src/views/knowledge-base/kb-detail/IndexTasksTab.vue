<template>
  <div class="tab-section">
    <p class="tab-section__hint">展示索引失败的任务，可在此重试</p>
    <el-table v-if="tasks.length" :data="tasks" stripe>
      <el-table-column prop="documentId" label="文档ID" width="100" />
      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">
          <IndexStatusTag :status="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="retryCount" label="重试" width="80" />
      <el-table-column prop="errorMsg" label="错误信息" show-overflow-tooltip />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="emit('retry', row.documentId)">重试</el-button>
        </template>
      </el-table-column>
    </el-table>
    <EmptyState v-else variant="tasks" />
  </div>
</template>

<script setup lang="ts">
defineProps<{ tasks: Array<{ documentId: number; status: string; retryCount: number; errorMsg?: string }> }>()

const emit = defineEmits<{
  retry: [documentId: number]
}>()
</script>

<style scoped lang="scss">
.tab-section {
  padding: 4px 0;
}

.tab-section__hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: $fk-text-secondary;
}
</style>