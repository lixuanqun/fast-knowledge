import request from '@/utils/request'

export interface EvalDataset {
  id: number
  name: string
  kbId: number
  topK: number
  description: string | null
  createdAt: string
  updatedAt: string
}

export interface EvalCase {
  id: number
  datasetId: number
  question: string
  /** JSON 数组字符串，如 "[101,102]" */
  expectedChunkIds: string | null
  /** JSON 数组字符串，如 ["冬季巡检"] */
  expectedKeywords: string | null
  enabled: number
  createdAt: string
}

export interface EvalRun {
  id: number
  datasetId: number
  kbId: number
  topK: number
  status: 'RUNNING' | 'DONE' | 'FAILED'
  totalCases: number
  metricsJson: string | null
  error: string | null
  startedAt: string
  finishedAt: string | null
}

export interface EvalRunItem {
  id: number
  runId: number
  caseId: number
  question: string
  firstHitRank: number | null
  recall: number | null
  keywordHit: number | null
  latencyMs: number
  hitChunkIds: string | null
  createdAt: string
}

/** 聚合指标（来自 metricsJson 解析） */
export interface EvalMetrics {
  total_cases: number
  recall_at_k: number | null
  mrr: number | null
  hit_rate: number | null
  keyword_hit_rate: number | null
  avg_latency_ms: number
}

export interface EvalRunDetail {
  run: EvalRun
  items: EvalRunItem[]
}

export function listEvalDatasets(kbId?: number) {
  return request.get<any, { data: EvalDataset[] }>('/evals/datasets', { params: { kbId } })
}

export function createEvalDataset(data: { name: string; kbId: number; topK?: number; description?: string }) {
  return request.post<any, { data: EvalDataset }>('/evals/datasets', data)
}

export function updateEvalDataset(id: number, patch: Partial<EvalDataset>) {
  return request.put<any, { data: EvalDataset }>(`/evals/datasets/${id}`, patch)
}

export function deleteEvalDataset(id: number) {
  return request.delete(`/evals/datasets/${id}`)
}

export function listEvalCases(datasetId: number) {
  return request.get<any, { data: EvalCase[] }>(`/evals/datasets/${datasetId}/cases`)
}

export function addEvalCase(
  datasetId: number,
  data: { question: string; expectedChunkIds?: number[]; expectedKeywords?: string[]; enabled?: number }
) {
  return request.post<any, { data: EvalCase }>(`/evals/datasets/${datasetId}/cases`, data)
}

export function updateEvalCase(id: number, patch: Partial<EvalCase>) {
  return request.put(`/evals/cases/${id}`, patch)
}

export function deleteEvalCase(id: number) {
  return request.delete(`/evals/cases/${id}`)
}

export function startEvalRun(datasetId: number) {
  return request.post<any, { data: EvalRun }>(`/evals/datasets/${datasetId}/runs`)
}

export function listEvalRuns(datasetId: number) {
  return request.get<any, { data: EvalRun[] }>(`/evals/datasets/${datasetId}/runs`)
}

export function getEvalRun(id: number) {
  return request.get<any, { data: EvalRunDetail }>(`/evals/runs/${id}`)
}
