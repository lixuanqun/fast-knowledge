<template>
  <div class="page-container">
    <PageHeader title="质量评测" subtitle="检索质量闭环 — 用例回归 · recall@k / MRR / 关键词命中率" />

    <el-row :gutter="16">
      <!-- 数据集 -->
      <el-col :span="8">
        <el-card class="fk-card" shadow="never" header="评测数据集">
          <template #header>
            <div class="card-header">
              <span>评测数据集</span>
              <el-button size="small" type="primary" @click="datasetDialog.open()">新建</el-button>
            </div>
          </template>

          <div v-if="!datasets.length" class="dataset-empty">
            <p class="dataset-empty__hint">还没有评测数据集。选择一个知识库，添加"问题 + 期望命中"的用例，作为检索质量的回归基线。</p>
          </div>

          <div
            v-for="d in datasets"
            :key="d.id"
            class="dataset-item"
            :class="{ 'dataset-item--active': d.id === selectedDatasetId }"
            @click="selectedDatasetId = d.id"
          >
            <div class="dataset-item__main">
              <strong class="dataset-item__name">{{ d.name }}</strong>
              <span class="dataset-item__meta">topK {{ d.topK }} · {{ d.createdAt?.slice(0, 10) }}</span>
            </div>
            <el-button link type="danger" size="small" @click.stop="handleDeleteDataset(d)">删除</el-button>
          </div>
        </el-card>
      </el-col>

      <!-- 用例 + 运行 -->
      <el-col :span="16">
        <el-card class="fk-card cases-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>评测用例{{ selectedDataset ? ` · ${selectedDataset.name}` : '' }}</span>
              <el-button size="small" type="primary" :disabled="!selectedDatasetId" @click="caseDialog.open()">
                添加用例
              </el-button>
            </div>
          </template>

          <el-table v-if="cases.length" :data="cases" size="small">
            <el-table-column prop="question" label="测试问题" min-width="220" show-overflow-tooltip />
            <el-table-column label="期望 chunk" width="140">
              <template #default="{ row }">{{ parseIds(row.expectedChunkIds) || '—' }}</template>
            </el-table-column>
            <el-table-column label="期望关键词" min-width="140">
              <template #default="{ row }">{{ parseKeywords(row.expectedKeywords) || '—' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="right">
              <template #default="{ row }">
                <el-button link type="danger" size="small" @click="handleDeleteCase(caseRow(row))">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <p v-else class="cases-empty">暂无用例。建议从真实检索日志中挑高频问题，标注期望命中的 chunk 或关键词。</p>
        </el-card>

        <el-card class="fk-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>评测运行</span>
              <el-button
                size="small"
                type="primary"
                :disabled="!selectedDatasetId || !cases.length || startRunMutation.isPending.value"
                :loading="startRunMutation.isPending.value"
                @click="handleStartRun"
              >
                运行评测
              </el-button>
            </div>
          </template>

          <el-table v-if="runs.length" :data="runs" size="small">
            <el-table-column prop="id" label="#" width="60" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)" size="small" effect="light" round>
                  {{ statusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="recall@k" width="100">
              <template #default="{ row }">{{ fmtMetrics(row).recall }}</template>
            </el-table-column>
            <el-table-column label="MRR" width="80">
              <template #default="{ row }">{{ fmtMetrics(row).mrr }}</template>
            </el-table-column>
            <el-table-column label="关键词命中" width="110">
              <template #default="{ row }">{{ fmtMetrics(row).kw }}</template>
            </el-table-column>
            <el-table-column label="平均延迟" width="100">
              <template #default="{ row }">{{ fmtMetrics(row).latency }}</template>
            </el-table-column>
            <el-table-column prop="startedAt" label="开始时间" min-width="150">
              <template #default="{ row }">{{ (row.startedAt || '').replace('T', ' ').slice(0, 19) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openRunDetail(runRow(row))">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          <p v-else class="cases-empty">还没有运行记录。用例就绪后点击「运行评测」生成基线。</p>
        </el-card>
      </el-col>
    </el-row>

    <!-- 新建数据集 -->
    <el-dialog v-model="datasetDialog.visible.value" title="新建评测数据集" width="480px">
      <el-form ref="datasetFormRef" :model="datasetDialog.form.value" label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="datasetDialog.form.value.name" placeholder="如：制度检索回归基线" />
        </el-form-item>
        <el-form-item label="知识库" required>
          <KbSelect v-model="datasetDialog.form.value.kbId" width="100%" :auto-default="false" />
        </el-form-item>
        <el-form-item label="topK">
          <el-input-number v-model="datasetDialog.form.value.topK" :min="1" :max="50" style="width: 100%" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="datasetDialog.form.value.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="datasetDialog.close()">取消</el-button>
        <el-button type="primary" :loading="createDatasetMutation.isPending.value" @click="handleCreateDataset">
          创建
        </el-button>
      </template>
    </el-dialog>

    <!-- 添加用例 -->
    <el-dialog v-model="caseDialog.visible.value" title="添加评测用例" width="560px">
      <el-form ref="caseFormRef" :model="caseDialog.form.value" label-width="110px">
        <el-form-item label="测试问题" required>
          <el-input v-model="caseDialog.form.value.question" type="textarea" :rows="2" placeholder="用户会怎么问，如：冬季设备巡检要注意什么" />
        </el-form-item>
        <el-form-item label="期望 chunk ID">
          <el-input v-model="caseDialog.form.value.expectedChunkIds" placeholder="逗号分隔，如 101,102（用于 recall/MRR）" />
        </el-form-item>
        <el-form-item label="期望关键词">
          <el-input v-model="caseDialog.form.value.expectedKeywords" placeholder="逗号分隔，如 冬季巡检,供暖" />
        </el-form-item>
      </el-form>
      <p class="case-hint">期望 chunk ID 可在知识库文档「分块列表」中查看；两者至少填一项。</p>
      <template #footer>
        <el-button @click="caseDialog.close()">取消</el-button>
        <el-button type="primary" :loading="addCaseMutation.isPending.value" @click="handleAddCase">添加</el-button>
      </template>
    </el-dialog>

    <!-- 运行详情 -->
    <el-drawer v-model="runDetailVisible" :title="`运行 #${selectedRunId} 详情`" size="60%">
      <template v-if="runDetail">
        <el-descriptions :column="3" size="small" border class="run-metrics">
          <el-descriptions-item label="recall@k">{{ fmtPct(runMetrics?.recall_at_k) }}</el-descriptions-item>
          <el-descriptions-item label="MRR">{{ runMetrics?.mrr?.toFixed(3) ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="命中率">{{ fmtPct(runMetrics?.hit_rate) }}</el-descriptions-item>
          <el-descriptions-item label="关键词命中率">{{ fmtPct(runMetrics?.keyword_hit_rate) }}</el-descriptions-item>
          <el-descriptions-item label="平均延迟">{{ runMetrics?.avg_latency_ms ?? '—' }} ms</el-descriptions-item>
          <el-descriptions-item label="用例数">{{ runMetrics?.total_cases ?? '—' }}</el-descriptions-item>
        </el-descriptions>

        <el-table :data="runDetail.items" size="small" class="run-items">
          <el-table-column prop="question" label="问题" min-width="200" show-overflow-tooltip />
          <el-table-column label="首命中名次" width="100" align="center">
            <template #default="{ row }">{{ row.firstHitRank ?? '未命中' }}</template>
          </el-table-column>
          <el-table-column label="recall" width="80" align="center">
            <template #default="{ row }">{{ row.recall != null ? fmtPct(row.recall) : '—' }}</template>
          </el-table-column>
          <el-table-column label="关键词" width="80" align="center">
            <template #default="{ row }">{{ row.keywordHit == null ? '—' : row.keywordHit ? '✓' : '✗' }}</template>
          </el-table-column>
          <el-table-column label="延迟" width="90" align="center">
            <template #default="{ row }">{{ row.latencyMs }} ms</template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import KbSelect from '@/components/KbSelect.vue'
import { useFormDialog } from '@/composables/useFormDialog'
import {
  useAddEvalCaseMutation,
  useCreateEvalDatasetMutation,
  useDeleteEvalCaseMutation,
  useDeleteEvalDatasetMutation,
  useEvalCasesQuery,
  useEvalDatasetsQuery,
  useEvalRunQuery,
  useEvalRunsQuery,
  useStartEvalRunMutation
} from '@/composables/queries/useEvals'
import type { EvalCase, EvalDataset, EvalRun } from '@/api/evals'

const selectedDatasetId = ref<number>()
const runDetailVisible = ref(false)
const selectedRunId = ref<number>()

const datasetsQuery = useEvalDatasetsQuery()
const datasets = computed(() => datasetsQuery.data.value || [])
const selectedDataset = computed(() => datasets.value.find(d => d.id === selectedDatasetId.value))

const casesQuery = useEvalCasesQuery(() => selectedDatasetId.value)
const cases = computed(() => casesQuery.data.value || [])

let runsQuery: ReturnType<typeof useEvalRunsQuery> | undefined
const hasRunning = () => runsQuery?.data.value?.some(r => r.status === 'RUNNING') ?? false
runsQuery = useEvalRunsQuery(() => selectedDatasetId.value, hasRunning)
const runs = computed(() => runsQuery?.data.value || [])

const createDatasetMutation = useCreateEvalDatasetMutation()
const deleteDatasetMutation = useDeleteEvalDatasetMutation()
const addCaseMutation = useAddEvalCaseMutation()
const deleteCaseMutation = useDeleteEvalCaseMutation()
const startRunMutation = useStartEvalRunMutation()

const runDetailQuery = useEvalRunQuery(() => (runDetailVisible.value ? selectedRunId.value : undefined))
const runDetail = computed(() => runDetailQuery.data.value)
const runMetrics = computed(() => {
  const json = runDetail.value?.run.metricsJson
  if (!json) return null
  try {
    return JSON.parse(json)
  } catch {
    return null
  }
})

const datasetDialog = useFormDialog({ name: '', kbId: undefined as number | undefined, topK: 8, description: '' })
const caseDialog = useFormDialog({ question: '', expectedChunkIds: '', expectedKeywords: '' })

function handleCreateDataset() {
  const form = datasetDialog.form.value
  if (!form.name.trim()) {
    ElMessage.warning('请填写数据集名称')
    return
  }
  if (form.kbId == null) {
    ElMessage.warning('请选择知识库')
    return
  }
  createDatasetMutation.mutate(
    { name: form.name.trim(), kbId: form.kbId, topK: form.topK, description: form.description },
    {
      onSuccess: res => {
        datasetDialog.close()
        ElMessage.success('数据集已创建')
        selectedDatasetId.value = res.data?.id
      }
    }
  )
}

async function handleDeleteDataset(d: EvalDataset) {
  await ElMessageBox.confirm(`删除数据集「${d.name}」将同时删除其用例与运行记录，确定？`, '删除确认', { type: 'warning' })
  deleteDatasetMutation.mutate(d.id, {
    onSuccess: () => {
      if (selectedDatasetId.value === d.id) selectedDatasetId.value = undefined
      ElMessage.success('已删除')
    }
  })
}

function handleAddCase() {
  const form = caseDialog.form.value
  if (!selectedDatasetId.value) return
  if (!form.question.trim()) {
    ElMessage.warning('请填写测试问题')
    return
  }
  const chunkIds = parseIdList(form.expectedChunkIds)
  const keywords = parseStringList(form.expectedKeywords)
  if (!chunkIds.length && !keywords.length) {
    ElMessage.warning('期望 chunk 与期望关键词至少填一项，否则无法评估')
    return
  }
  addCaseMutation.mutate(
    {
      datasetId: selectedDatasetId.value,
      data: {
        question: form.question.trim(),
        expectedChunkIds: chunkIds.length ? chunkIds : undefined,
        expectedKeywords: keywords.length ? keywords : undefined,
        enabled: 1
      }
    },
    {
      onSuccess: () => {
        caseDialog.close()
        ElMessage.success('用例已添加')
      }
    }
  )
}

function caseRow(row: unknown) {
  return row as EvalCase
}

function runRow(row: unknown) {
  return row as EvalRun
}

function handleDeleteCase(row: EvalCase) {
  deleteCaseMutation.mutate(row.id)
}

function handleStartRun() {
  if (!selectedDatasetId.value) return
  startRunMutation.mutate(selectedDatasetId.value, {
    onSuccess: () => ElMessage.success('评测已开始，运行中每 2 秒自动刷新')
  })
}

function openRunDetail(row: EvalRun) {
  selectedRunId.value = row.id
  runDetailVisible.value = true
}

function parseIds(json: string | null) {
  try {
    const list = json ? (JSON.parse(json) as number[]) : []
    return list.join(', ')
  } catch {
    return ''
  }
}

function parseKeywords(json: string | null) {
  try {
    const list = json ? (JSON.parse(json) as string[]) : []
    return list.join('、')
  } catch {
    return ''
  }
}

function parseIdList(input: string) {
  return input
    .split(/[,，\s]+/)
    .map(s => Number(s.trim()))
    .filter(n => Number.isFinite(n) && n > 0)
}

function parseStringList(input: string) {
  return input
    .split(/[,，]+/)
    .map(s => s.trim())
    .filter(Boolean)
}

function fmtPct(v: number | null | undefined) {
  return v == null ? '—' : `${Math.round(v * 100)}%`
}

function fmtMetrics(row: unknown) {
  const r = row as EvalRun
  try {
    const m = r.metricsJson ? JSON.parse(r.metricsJson) : null
    return {
      recall: m?.recall_at_k != null ? fmtPct(m.recall_at_k) : '—',
      mrr: m?.mrr != null ? m.mrr.toFixed(3) : '—',
      kw: m?.keyword_hit_rate != null ? fmtPct(m.keyword_hit_rate) : '—',
      latency: m?.avg_latency_ms != null ? `${m.avg_latency_ms} ms` : '—'
    }
  } catch {
    return { recall: '—', mrr: '—', kw: '—', latency: '—' }
  }
}

function statusType(status: string) {
  return status === 'DONE' ? 'success' : status === 'FAILED' ? 'danger' : 'primary'
}

function statusLabel(status: string) {
  return status === 'DONE' ? '已完成' : status === 'FAILED' ? '失败' : '运行中'
}
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.dataset-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  margin-bottom: 8px;
  border: 1px solid $fk-border;
  border-radius: 10px;
  cursor: pointer;
  transition: border-color 0.2s ease, background-color 0.2s ease;

  &:hover {
    border-color: $fk-primary;
  }

  &--active {
    border-color: $fk-primary;
    background: var(--fk-primary-light);
  }

  &__main {
    min-width: 0;
  }

  &__name {
    display: block;
    font-size: 14px;
    color: $fk-text-primary;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__meta {
    font-size: 12px;
    color: $fk-text-secondary;
  }
}

.dataset-empty__hint,
.cases-empty {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: $fk-text-secondary;
}

.cases-card {
  margin-bottom: 16px;
}

.case-hint {
  margin: 0 0 4px 110px;
  font-size: 12px;
  color: $fk-text-secondary;
}

.run-metrics {
  margin-bottom: 16px;
}
</style>
