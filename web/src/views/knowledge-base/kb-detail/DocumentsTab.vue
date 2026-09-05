<template>
  <div>
    <el-table v-if="docs.length" :data="pagedDocs" stripe>
      <el-table-column prop="title" label="标题" min-width="160">
        <template #default="{ row }">
          <el-link type="primary" @click="emit('preview', row as KbDocument)">{{ row.title }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="fileName" label="文件名" show-overflow-tooltip />
      <el-table-column prop="docType" label="类型" width="90" show-overflow-tooltip />
      <el-table-column prop="docNo" label="文号" width="120" show-overflow-tooltip />
      <el-table-column prop="fileType" label="格式" width="72" />
      <el-table-column label="大小" width="100">
        <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column label="索引状态" width="120">
        <template #default="{ row }">
          <IndexStatusTag :status="row.indexStatus" />
        </template>
      </el-table-column>
      <el-table-column label="检索" width="100" align="center">
        <template #default="{ row }">
          <el-tag
            size="small"
            effect="light"
            :type="recallTagType(row as KbDocument)"
          >{{ recallLabel(row as KbDocument) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="分块" width="80" align="center">
        <template #default="{ row }">{{ row.chunkCount ?? 0 }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="emit('preview', row as KbDocument)">预览</el-button>
          <el-button link type="primary" @click="emit('metadata', row as KbDocument)">元数据</el-button>
          <el-button link type="primary" @click="emit('reindex', row.id)">重新索引</el-button>
          <el-button link type="danger" @click="emit('delete', row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="docs.length" class="table-footer">
      <el-pagination
        v-model:current-page="docPage"
        v-model:page-size="docPageSize"
        :total="docs.length"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        small
      />
    </div>

    <EmptyState v-if="!docs.length" variant="docs">
      <slot name="upload" />
    </EmptyState>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { KbDocument } from '@/api'
import IndexStatusTag from '@/components/IndexStatusTag.vue'
import EmptyState from '@/components/EmptyState.vue'
import { formatFileSize } from '@/utils/format'

const props = defineProps<{ docs: KbDocument[] }>()

const emit = defineEmits<{
  preview: [doc: KbDocument]
  metadata: [doc: KbDocument]
  reindex: [docId: number]
  delete: [docId: number]
}>()

const docPage = ref(1)
const docPageSize = ref(10)

const pagedDocs = computed(() => {
  const start = (docPage.value - 1) * docPageSize.value
  return props.docs.slice(start, start + docPageSize.value)
})

function recallLabel(doc: KbDocument): string {
  if (doc.enabled === 0) return '已禁用'
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  if (doc.effectiveDate) {
    const effective = new Date(doc.effectiveDate)
    if (effective > today) return '未生效'
  }
  if (doc.expireDate) {
    const expire = new Date(doc.expireDate)
    if (expire < today) return '已过期'
  }
  return '可检索'
}

function recallTagType(doc: KbDocument): 'success' | 'info' | 'warning' | 'danger' {
  const label = recallLabel(doc)
  if (label === '可检索') return 'success'
  if (label === '未生效') return 'warning'
  return 'info'
}
</script>

<style scoped lang="scss">
.table-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>