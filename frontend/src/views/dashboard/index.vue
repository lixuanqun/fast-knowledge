<template>
  <div class="page-container dashboard-page">
    <PageHeader title="系统概览" subtitle="知识库运行状态与快捷入口" />

    <el-skeleton v-if="loading" :rows="4" animated />
    <template v-else>
      <el-row :gutter="16" class="stat-row">
        <el-col v-for="item in statCards" :key="item.key" :xs="12" :sm="8" :md="4">
          <el-card shadow="hover" class="stat-card" :class="item.className">
            <div class="stat-card__value">{{ stats?.[item.key] ?? 0 }}</div>
            <div class="stat-card__label">{{ item.label }}</div>
          </el-card>
        </el-col>
      </el-row>

      <el-card class="section-card">
        <template #header>快速入口</template>
        <el-space wrap>
          <el-button v-for="link in quickLinks" :key="link.path" :type="link.type" @click="$router.push(link.path)">
            {{ link.label }}
          </el-button>
        </el-space>
      </el-card>

      <el-card class="section-card">
        <template #header>最近操作</template>
        <el-table v-if="audits.length" :data="audits" size="small" stripe>
          <el-table-column prop="action" label="操作" width="140" />
          <el-table-column prop="targetType" label="对象" width="80" />
          <el-table-column prop="detail" label="详情" show-overflow-tooltip />
          <el-table-column label="时间" width="180">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
        <EmptyState v-else description="暂无操作记录" />
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { formatDateTime } from '@/utils/format'
import { useAuditsQuery, useDashboardStatsQuery } from '@/composables/queries/useDashboard'

const { data: stats, isLoading: statsLoading } = useDashboardStatsQuery()
const { data: auditsData, isLoading: auditsLoading } = useAuditsQuery(10)

const loading = computed(() => statsLoading.value || auditsLoading.value)
const audits = computed(() => auditsData.value || [])

const statCards = [
  { key: 'kbCount', label: '知识库', className: '' },
  { key: 'documentCount', label: '文档总数', className: '' },
  { key: 'indexedCount', label: '已索引', className: 'is-success' },
  { key: 'failedCount', label: '索引失败', className: 'is-danger' },
  { key: 'pendingTasks', label: '待处理任务', className: 'is-warning' }
]

const quickLinks = [
  { path: '/kbs', label: '管理知识库', type: 'primary' as const },
  { path: '/search', label: '智能检索', type: 'default' as const },
  { path: '/qa', label: '智能问答', type: 'default' as const },
  { path: '/chat', label: '智能对话', type: 'default' as const },
  { path: '/writer', label: '智能写文档', type: 'default' as const }
]
</script>

<style scoped>
.stat-row { margin-bottom: 16px; }
.stat-card { text-align: center; border-radius: 10px; }
.stat-card__value { font-size: 28px; font-weight: 700; color: #1a1a1a; }
.stat-card__label { margin-top: 6px; font-size: 13px; color: #888; }
.stat-card.is-success .stat-card__value { color: #67c23a; }
.stat-card.is-danger .stat-card__value { color: #f56c6c; }
.stat-card.is-warning .stat-card__value { color: #e6a23c; }
.section-card { margin-top: 16px; border-radius: 10px; }
</style>
