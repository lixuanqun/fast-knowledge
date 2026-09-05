<template>
  <div v-loading="loading ?? false" class="tab-section">
    <div class="wiki-toolbar">
      <p class="tab-section__hint">
        文档索引后编译为 Wiki（默认草稿）。发布后进入问答双路召回；「目录」问法优先命中 index。
      </p>
      <div class="wiki-toolbar__actions">
        <el-select v-model="statusFilter" clearable placeholder="全部状态" style="width:140px">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
        </el-select>
        <el-button :loading="rebuilding" @click="emit('rebuildIndex')">重建目录</el-button>
      </div>
    </div>
    <el-table v-if="filteredPages.length" :data="filteredPages" stripe>
      <el-table-column prop="title" label="标题" min-width="180">
        <template #default="{ row }">
          <el-link type="primary" @click="emit('view', row as WikiPage)">{{ row.title }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="slug" label="Slug" width="160" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 'PUBLISHED' ? 'success' : 'info'">
            {{ row.status === 'PUBLISHED' ? '已发布' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="180">
        <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.slug !== 'index' && row.status !== 'PUBLISHED'"
            link
            type="primary"
            :loading="actionId === row.id"
            @click="emit('publish', row as WikiPage)"
          >发布</el-button>
          <el-button
            v-if="row.slug !== 'index' && row.status === 'PUBLISHED'"
            link
            type="warning"
            :loading="actionId === row.id"
            @click="emit('reject', row as WikiPage)"
          >下架</el-button>
          <el-button link type="primary" @click="emit('view', row as WikiPage)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>
    <EmptyState v-else variant="docs" description="暂无 Wiki 页面，上传文档并完成索引后将自动生成" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { WikiPage } from '@/api'
import EmptyState from '@/components/EmptyState.vue'
import { formatDateTime } from '@/utils/format'

const props = defineProps<{
  pages: WikiPage[]
  loading?: boolean
  actionId?: number
  rebuilding?: boolean
}>()

const emit = defineEmits<{
  view: [page: WikiPage]
  publish: [page: WikiPage]
  reject: [page: WikiPage]
  rebuildIndex: []
}>()

const statusFilter = ref('')
const filteredPages = computed(() => {
  if (!statusFilter.value) return props.pages
  return props.pages.filter(p => p.status === statusFilter.value)
})
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

.wiki-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.wiki-toolbar__actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>