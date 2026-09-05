<template>
  <div v-if="total > 0" class="table-footer">
    <el-pagination
      v-model:current-page="currentPage"
      v-model:page-size="currentPageSize"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      background
      small
    />
  </div>
</template>

<script setup lang="ts">
defineProps<{ total: number }>()

const currentPage = defineModel<number>('currentPage', { default: 1 })
const currentPageSize = defineModel<number>('pageSize', { default: 10 })
</script>

<style scoped lang="scss">
.table-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>