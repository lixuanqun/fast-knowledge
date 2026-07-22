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
        <template #header>
          <div class="section-header">
            <span class="section-title">RAG 运营</span>
            <el-button size="small" :loading="exporting" @click="handleExportQa">导出问答抽检 CSV</el-button>
          </div>
        </template>
        <el-row :gutter="12" class="rag-stat-row">
          <el-col :xs="12" :sm="8" :md="4">
            <div class="rag-metric">
              <div class="rag-metric-label">检索次数</div>
              <div class="rag-metric-value">{{ ragOps?.searchCount ?? 0 }}</div>
            </div>
          </el-col>
          <el-col :xs="12" :sm="8" :md="4">
            <div class="rag-metric">
              <div class="rag-metric-label">问答次数</div>
              <div class="rag-metric-value">{{ ragOps?.ragCount ?? 0 }}</div>
            </div>
          </el-col>
          <el-col :xs="12" :sm="8" :md="4">
            <div class="rag-metric">
              <div class="rag-metric-label">零结果率</div>
              <div class="rag-metric-value">{{ formatRate(ragOps?.zeroHitRate) }}</div>
            </div>
          </el-col>
          <el-col :xs="12" :sm="8" :md="4">
            <div class="rag-metric">
              <div class="rag-metric-label">缓存命中率</div>
              <div class="rag-metric-value">{{ formatRate(ragOps?.cacheHitRate) }}</div>
            </div>
          </el-col>
          <el-col :xs="12" :sm="8" :md="4">
            <div class="rag-metric">
              <div class="rag-metric-label">检索均值 / P95</div>
              <div class="rag-metric-value">{{ formatLatency(ragOps?.searchLatencyMeanMs) }} / {{ formatLatency(ragOps?.searchLatencyP95Ms) }}</div>
            </div>
          </el-col>
          <el-col :xs="12" :sm="8" :md="4">
            <div class="rag-metric">
              <div class="rag-metric-label">问答历史</div>
              <div class="rag-metric-value">{{ ragOps?.qaHistoryCount ?? 0 }}</div>
            </div>
          </el-col>
          <el-col :xs="12" :sm="8" :md="4">
            <div class="rag-metric">
              <div class="rag-metric-label">Agentic 多跳</div>
              <div class="rag-metric-value">{{ ragOps?.agenticCount ?? 0 }}</div>
            </div>
          </el-col>
        </el-row>

        <el-row :gutter="16" class="rag-tables">
          <el-col :xs="24" :md="12">
            <div class="sub-title">热门查询（近 500 次检索）</div>
            <el-table v-if="ragOps?.hotQueries?.length" :data="ragOps.hotQueries" size="small" stripe>
              <el-table-column prop="query" label="查询" show-overflow-tooltip />
              <el-table-column prop="count" label="次数" width="80" align="right" />
            </el-table>
            <EmptyState v-else variant="default" description="暂无检索记录" />
          </el-col>
          <el-col :xs="24" :md="12">
            <div class="sub-title">近期零结果</div>
            <el-table v-if="ragOps?.recentZeroQueries?.length" :data="ragOps.recentZeroQueries" size="small" stripe>
              <el-table-column prop="query" label="查询" show-overflow-tooltip />
              <el-table-column prop="kbId" label="KB" width="72" />
              <el-table-column label="时间" width="160">
                <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
              </el-table-column>
            </el-table>
            <EmptyState v-else variant="default" description="暂无零结果查询" />
          </el-col>
        </el-row>

        <div class="sub-title qa-history-title">问答抽检（最近）</div>
        <el-table v-if="qaHistory.length" :data="qaHistory" size="small" stripe>
          <el-table-column prop="kbId" label="KB" width="72" />
          <el-table-column prop="question" label="问题" min-width="160" show-overflow-tooltip />
          <el-table-column prop="answer" label="答案" min-width="200" show-overflow-tooltip />
          <el-table-column prop="sourceCount" label="引用" width="64" align="center" />
          <el-table-column label="时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
        <EmptyState v-else variant="default" description="暂无问答历史，完成一次智能问答后会出现在此" />
      </el-card>

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
import {
  useAuditsQuery,
  useDashboardStatsQuery,
  useQaHistoryQuery,
  useRagOpsQuery
} from '@/composables/queries/useDashboard'
import { exportQaHistory } from '@/api/qa'
import { ElMessage } from 'element-plus'

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
const { data: ragOps, isLoading: ragOpsLoading } = useRagOpsQuery(true)
const { data: qaHistoryPage, isLoading: qaHistoryLoading } = useQaHistoryQuery(1, 8)

const page = ref(1)
const pageSize = ref(10)
const exporting = ref(false)

const loading = computed(
  () => statsLoading.value || auditsLoading.value || ragOpsLoading.value || qaHistoryLoading.value
)
const audits = computed(() => auditsData.value || [])
const qaHistory = computed(() => qaHistoryPage.value?.records || [])
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

function formatRate(v?: number) {
  if (v == null || Number.isNaN(v)) return '—'
  return `${(v * 100).toFixed(1)}%`
}

function formatLatency(v?: number) {
  if (v == null || Number.isNaN(v)) return '—'
  return `${Math.round(v)}ms`
}

async function handleExportQa() {
  exporting.value = true
  try {
    const blob = await exportQaHistory({ limit: 5000 })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'qa-history.csv'
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('已导出问答抽检')
  } catch {
    /* axios 已提示 */
  } finally {
    exporting.value = false
  }
}
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

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-title {
  font-weight: 600;
  color: $fk-text-primary;
}

.sub-title {
  margin: 12px 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: $fk-text-secondary;
}

.qa-history-title {
  margin-top: 20px;
}

.rag-stat-row {
  margin-bottom: 8px;
}

.rag-metric {
  padding: 10px 12px;
  border: 1px solid $fk-border;
  border-radius: 8px;
  background: $fk-surface-muted;
  min-height: 64px;
}

.rag-metric-label {
  font-size: 12px;
  color: $fk-text-secondary;
  margin-bottom: 6px;
}

.rag-metric-value {
  font-size: 16px;
  font-weight: 600;
  color: $fk-text-primary;
  word-break: break-all;
}

.rag-tables {
  margin-top: 8px;
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
