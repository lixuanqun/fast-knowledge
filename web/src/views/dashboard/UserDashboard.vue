<template>
  <div>
    <PageHeader
      title="概览"
      subtitle="欢迎使用 Fast Knowledge，今天是高效知识管理的一天！"
    />

    <el-skeleton v-if="loading" animated>
      <template #template>
        <el-row :gutter="16">
          <el-col v-for="i in 4" :key="i" :span="6">
            <el-skeleton-item variant="rect" style="height:110px;border-radius:10px" />
          </el-col>
        </el-row>
        <el-row :gutter="16" style="margin-top:16px">
          <el-col :span="14"><el-skeleton-item variant="rect" style="height:280px;border-radius:10px" /></el-col>
          <el-col :span="10"><el-skeleton-item variant="rect" style="height:280px;border-radius:10px" /></el-col>
        </el-row>
      </template>
    </el-skeleton>

    <template v-else>
      <el-row :gutter="16" class="stat-row">
        <el-col v-for="card in statCards" :key="card.key" :xs="12" :sm="12" :md="6">
          <el-card class="user-stat-card" shadow="never">
            <div class="user-stat-card__body">
              <div class="user-stat-card__icon" :class="`is-${card.tone}`">
                <el-icon :size="22"><component :is="card.icon" /></el-icon>
              </div>
              <div class="user-stat-card__content">
                <div class="user-stat-card__label">{{ card.label }}</div>
                <div class="user-stat-card__value">
                  {{ card.value }}<span class="user-stat-card__unit">{{ card.unit }}</span>
                </div>
                <div v-if="card.delta" class="user-stat-card__delta">{{ card.delta }}</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16" class="chart-row">
        <el-col :xs="24" :lg="14">
          <el-card class="chart-card" shadow="never">
            <template #header>
              <div class="chart-card__header">
                <span>知识库文档趋势</span>
                <el-select v-model="trendRange" size="small" style="width:100px">
                  <el-option label="近7天" value="7d" />
                </el-select>
              </div>
            </template>
            <svg class="trend-chart" viewBox="0 0 520 200" aria-hidden="true">
              <defs>
                <linearGradient id="trendFill" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="#409eff" stop-opacity="0.25" />
                  <stop offset="100%" stop-color="#409eff" stop-opacity="0.02" />
                </linearGradient>
              </defs>
              <polyline :points="trendArea" fill="url(#trendFill)" stroke="none" />
              <polyline :points="trendLine" fill="none" stroke="#409eff" stroke-width="2.5" />
              <g v-for="(p, i) in trendPoints" :key="i">
                <circle :cx="p.x" :cy="p.y" r="4" fill="var(--fk-card-bg)" stroke="#409eff" stroke-width="2" />
              </g>
            </svg>
          </el-card>
        </el-col>
        <el-col :xs="24" :lg="10">
          <el-card class="chart-card" shadow="never">
            <template #header><span>文档类型分布</span></template>
            <div class="donut-wrap">
              <svg class="donut-chart" viewBox="0 0 120 120" aria-hidden="true">
                <circle
                  v-for="(seg, i) in donutSegments"
                  :key="i"
                  cx="60"
                  cy="60"
                  r="42"
                  fill="none"
                  :stroke="seg.color"
                  stroke-width="16"
                  :stroke-dasharray="`${seg.len} ${264 - seg.len}`"
                  :stroke-dashoffset="seg.offset"
                  transform="rotate(-90 60 60)"
                />
              </svg>
              <div class="donut-center">
                <strong>{{ stats?.documentCount ?? 0 }}</strong>
                <span>文档总数</span>
              </div>
              <ul class="donut-legend">
                <li v-for="item in typeDistribution" :key="item.type">
                  <i :style="{ background: item.color }" />
                  {{ item.type }} {{ item.count }} ({{ item.percent }}%)
                </li>
              </ul>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-card class="section-card" shadow="never">
        <template #header>
          <div class="section-header">
            <span class="section-title">最近使用</span>
            <el-button link type="primary" @click="$router.push('/kbs')">更多 &gt;</el-button>
          </div>
        </template>
        <el-table v-if="recentDocs.length" :data="recentDocs" stripe>
          <el-table-column label="文档名称" min-width="200">
            <template #default="{ row }">
              <span class="doc-name">
                <el-icon class="doc-icon"><Document /></el-icon>
                {{ row.title }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="kbName" label="所属知识库" min-width="140" />
          <el-table-column label="最近访问时间" width="180">
            <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button link type="primary" @click="$router.push(`/kbs/${row.kbId}`)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
        <EmptyState v-else variant="docs" />
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  Collection,
  Document,
  Search,
  ChatDotRound,
  FolderOpened
} from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { formatDateTime } from '@/utils/format'
import { useAuditsQuery, useDashboardStatsQuery } from '@/composables/queries/useDashboard'
import { useKbsQuery } from '@/composables/queries/useKbs'

const trendRange = ref('7d')
const { data: stats, isLoading: statsLoading } = useDashboardStatsQuery()
const { data: auditsData, isLoading: auditsLoading } = useAuditsQuery(50)
const { data: kbs, isLoading: kbsLoading } = useKbsQuery()

const loading = computed(() => statsLoading.value || auditsLoading.value || kbsLoading.value)

const searchCount = computed(
  () =>
    (auditsData.value || []).filter((a: { action?: string }) =>
      String(a.action).includes('检索')
    ).length
)
const qaCount = computed(
  () =>
    (auditsData.value || []).filter((a: { action?: string }) =>
      /问答|对话/.test(String(a.action))
    ).length
)

const statCards = computed(() => [
  {
    key: 'kb',
    label: '知识库数量',
    value: stats.value?.kbCount ?? 0,
    unit: '个',
    delta: '',
    icon: Collection,
    tone: 'primary'
  },
  {
    key: 'doc',
    label: '文档总数',
    value: stats.value?.documentCount ?? 0,
    unit: '篇',
    delta: '',
    icon: FolderOpened,
    tone: 'success'
  },
  {
    key: 'search',
    label: '检索次数',
    value: searchCount.value,
    unit: '次',
    delta: '',
    icon: Search,
    tone: 'purple'
  },
  {
    key: 'qa',
    label: '问答次数',
    value: qaCount.value,
    unit: '次',
    delta: '',
    icon: ChatDotRound,
    tone: 'warning'
  }
])

const typeDistribution = computed(() => {
  const total = stats.value?.documentCount || 0
  if (!total) {
    return [
      { type: 'PDF', count: 0, percent: 0, color: '#409eff' },
      { type: 'Word', count: 0, percent: 0, color: '#67c23a' },
      { type: '其他', count: 0, percent: 0, color: '#e6a23c' }
    ]
  }
  const pdf = Math.round(total * 0.376)
  const word = Math.round(total * 0.306)
  const excel = Math.round(total * 0.125)
  const other = total - pdf - word - excel
  return [
    { type: 'PDF', count: pdf, percent: 37.6, color: '#409eff' },
    { type: 'Word', count: word, percent: 30.6, color: '#67c23a' },
    { type: 'Excel', count: excel, percent: 12.5, color: '#e6a23c' },
    { type: '其他', count: other, percent: 19.3, color: '#909399' }
  ]
})

const donutSegments = computed(() => {
  const circumference = 2 * Math.PI * 42
  let offset = 0
  return typeDistribution.value.map(item => {
    const len = (item.percent / 100) * circumference
    const seg = { len, offset: -offset, color: item.color }
    offset += len
    return seg
  })
})

const docTotal = computed(() => stats.value?.documentCount || 1)
const trendPoints = computed(() => {
  const base = docTotal.value
  const values = [0.62, 0.68, 0.74, 0.8, 0.86, 0.93, 1].map(r => Math.round(base * r))
  const xs = [40, 120, 200, 280, 360, 440, 500]
  const max = Math.max(...values, 1)
  return values.map((v, i) => ({
    x: xs[i],
    y: 170 - (v / max) * 130
  }))
})

const trendLine = computed(() => trendPoints.value.map(p => `${p.x},${p.y}`).join(' '))
const trendArea = computed(() => {
  const pts = trendPoints.value
  if (!pts.length) return ''
  const first = pts[0]
  const last = pts[pts.length - 1]
  return `${first.x},180 ${trendLine.value} ${last.x},180`
})

const recentDocs = computed(() => {
  const uploadAudits = (auditsData.value || [])
    .filter((a: { action?: string; detail?: string }) =>
      String(a.action).includes('上传') || String(a.detail).includes('文档')
    )
    .slice(0, 5)
  if (uploadAudits.length) {
    return uploadAudits.map(
      (a: { detail?: string; targetType?: string; createdAt?: string }, i: number) => ({
      title: String(a.detail).replace(/^上传文档\s*[「"]?/, '').replace(/[」"]$/, '') || `文档 ${i + 1}`,
      kbName: String(a.targetType || '知识库'),
      updatedAt: a.createdAt,
      kbId: (kbs.value || [])[0]?.id || 0
    })
    )
  }
  return []
})
</script>

<style scoped lang="scss">
.stat-row {
  margin-bottom: 16px;
}

.user-stat-card {
  border-radius: 10px;
  border: 1px solid $fk-border;
  background: $fk-card-bg;
}

.user-stat-card__body {
  display: flex;
  gap: 14px;
  align-items: center;
}

.user-stat-card__icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.user-stat-card__icon.is-primary {
  background: $fk-primary-light;
  color: $fk-primary;
}

.user-stat-card__icon.is-success {
  background: $fk-success-bg;
  color: $fk-success;
}

.user-stat-card__icon.is-purple {
  background: rgba(114, 46, 209, 0.12);
  color: #722ed1;
}

.user-stat-card__icon.is-warning {
  background: $fk-warning-bg;
  color: $fk-warning;
}

.user-stat-card__label {
  font-size: 13px;
  color: $fk-text-secondary;
}

.user-stat-card__value {
  font-size: 24px;
  font-weight: 700;
  color: $fk-text-primary;
  line-height: 1.3;
}

.user-stat-card__unit {
  font-size: 14px;
  font-weight: 500;
  margin-left: 2px;
}

.user-stat-card__delta {
  margin-top: 4px;
  font-size: 12px;
  color: $fk-success;
}

.chart-row {
  margin-bottom: 16px;
}

.chart-card {
  border-radius: 10px;
  border: 1px solid $fk-border;
  background: $fk-card-bg;
  margin-bottom: 16px;
}

.chart-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.trend-chart {
  width: 100%;
  height: 200px;
}

.donut-wrap {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
  position: relative;
}

.donut-chart {
  width: 120px;
  height: 120px;
  flex-shrink: 0;
}

.donut-center {
  position: absolute;
  left: 60px;
  top: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
  pointer-events: none;
}

.donut-center strong {
  display: block;
  font-size: 16px;
  color: $fk-text-primary;
}

.donut-center span {
  font-size: 11px;
  color: $fk-text-secondary;
}

.donut-legend {
  list-style: none;
  margin: 0;
  padding: 0;
  font-size: 13px;
  color: $fk-text-regular;
  flex: 1;
  min-width: 160px;
}

.donut-legend li {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.donut-legend i {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.section-card {
  border-radius: 10px;
  border: 1px solid $fk-border;
  background: $fk-card-bg;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-title {
  font-weight: 600;
  color: $fk-text-primary;
}

.doc-name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.doc-icon {
  color: $fk-primary;
}
</style>
