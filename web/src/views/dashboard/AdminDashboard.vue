<template>
  <div>
    <PageHeader title="系统概览" subtitle="知识库运行状态与快捷入口" />

    <el-skeleton v-if="loading" animated>
      <template #template>
        <el-row :gutter="16" class="stat-row">
          <el-col v-for="i in 5" :key="i" :span="4">
            <el-skeleton-item variant="rect" style="height:120px;border-radius:10px" />
          </el-col>
        </el-row>
        <el-skeleton-item variant="rect" style="height:72px;margin-top:16px;border-radius:10px" />
        <el-skeleton-item variant="rect" style="height:240px;margin-top:16px;border-radius:10px" />
      </template>
    </el-skeleton>
    <template v-else>
      <el-row :gutter="16" class="stat-row">
        <el-col v-for="item in statCards" :key="item.key" :xs="12" :sm="8" :md="item.span">
          <DashboardStatCard
            :label="item.label"
            :value="stats?.[item.key] ?? 0"
            :icon="item.icon"
            :tone="item.tone"
          />
        </el-col>
      </el-row>

      <el-card class="section-card fk-card" shadow="never">
        <template #header><span class="section-title">快速入口</span></template>
        <div class="quick-row">
          <QuickEntryButton
            v-for="link in quickLinks"
            :key="link.path"
            :path="link.path"
            :label="link.label"
            :icon="link.icon"
            :primary="link.primary"
          />
        </div>
      </el-card>

      <el-card class="section-card" shadow="never">
        <template #header><span class="section-title">最近操作</span></template>
        <el-table v-if="audits.length" :data="pagedAudits" size="default" stripe>
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <span class="audit-action">
                <el-icon :class="auditActionMeta(row.action).tone">
                  <component :is="auditIcon(row.action)" />
                </el-icon>
                {{ row.action }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="targetType" label="对象" width="100" />
          <el-table-column prop="detail" label="详情" show-overflow-tooltip min-width="200" />
          <el-table-column label="时间" width="180">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
        <EmptyState v-else variant="default" description="暂无操作记录" />
        <div v-if="audits.length" class="table-footer">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :total="audits.length"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            background
            small
          />
        </div>
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  Clock,
  Collection,
  Document,
  EditPen,
  QuestionFilled,
  Search,
  ChatDotRound,
  CircleCheck,
  CircleClose,
  Upload,
  Refresh,
  User
} from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import DashboardStatCard from '@/components/design/DashboardStatCard.vue'
import QuickEntryButton from '@/components/design/QuickEntryButton.vue'
import { auditActionMeta, formatDateTime } from '@/utils/format'
import { useAuditsQuery, useDashboardStatsQuery } from '@/composables/queries/useDashboard'

const AUDIT_ICONS = {
  Collection,
  Upload,
  Refresh,
  CircleCheck,
  User,
  Document
} as const

function auditIcon(action: string) {
  return AUDIT_ICONS[auditActionMeta(action).icon]
}

const { data: stats, isLoading: statsLoading } = useDashboardStatsQuery()
const { data: auditsData, isLoading: auditsLoading } = useAuditsQuery(50)

const page = ref(1)
const pageSize = ref(10)

const loading = computed(() => statsLoading.value || auditsLoading.value)
const audits = computed(() => auditsData.value || [])
const pagedAudits = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return audits.value.slice(start, start + pageSize.value)
})

const statCards = [
  { key: 'kbCount', label: '知识库', icon: Collection, tone: 'primary' as const, span: 4 },
  { key: 'documentCount', label: '文档总数', icon: Document, tone: 'primary' as const, span: 5 },
  { key: 'indexedCount', label: '已索引', icon: CircleCheck, tone: 'success' as const, span: 5 },
  { key: 'failedCount', label: '索引失败', icon: CircleClose, tone: 'danger' as const, span: 5 },
  { key: 'pendingTasks', label: '待处理任务', icon: Clock, tone: 'warning' as const, span: 5 }
]

const quickLinks = [
  { path: '/kbs', label: '管理知识库', icon: Collection, primary: true },
  { path: '/search', label: '智能检索', icon: Search, primary: false },
  { path: '/qa', label: '智能问答', icon: QuestionFilled, primary: false },
  { path: '/chat', label: '智能对话', icon: ChatDotRound, primary: false },
  { path: '/writer', label: '智能写文档', icon: EditPen, primary: false }
]
</script>

<style scoped lang="scss">
.stat-row {
  margin-bottom: 16px;
}

.section-card {
  margin-top: 16px;
  border-radius: 10px;
  border: 1px solid $fk-border;
  background: $fk-card-bg;
}

.section-title {
  font-weight: 600;
  color: $fk-text-primary;
}

.quick-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.audit-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.audit-action .el-icon {
  font-size: 15px;
}

.audit-action .el-icon.is-primary {
  color: $fk-primary;
}

.audit-action .el-icon.is-success {
  color: $fk-success;
}

.audit-action .el-icon.is-warning {
  color: $fk-warning;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
