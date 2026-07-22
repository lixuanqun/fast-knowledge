import request from '@/utils/request'

export function getDashboardStats() {
  return request.get('/dashboard/stats')
}

export function listAudits(limit = 20) {
  return request.get(`/dashboard/audits?limit=${limit}`)
}

export interface RagOpsSnapshot {
  searchCount: number
  ragCount: number
  zeroHitCount: number
  zeroHitRate: number
  cacheHitRate: number
  searchLatencyMeanMs?: number
  searchLatencyP95Ms?: number
  ragLatencyMeanMs?: number
  ragLatencyP95Ms?: number
  hotQueries: { query: string; count: number }[]
  recentZeroQueries: { kbId?: number; query: string; createdAt?: string }[]
  qaHistoryCount: number
  agenticCount?: number
}

export function getRagOps() {
  return request.get<any, { data: RagOpsSnapshot }>('/dashboard/rag-ops')
}
