import { useQuery } from '@tanstack/vue-query'
import { getDashboardStats, getRagOps, listAudits } from '@/api'
import { listQaHistory } from '@/api/qa'
import { queryKeys } from '@/lib/query-keys'

export function useDashboardStatsQuery() {
  return useQuery({
    queryKey: queryKeys.dashboard.stats,
    queryFn: async () => {
      const res = await getDashboardStats()
      return res.data as Record<string, number>
    }
  })
}

export function useAuditsQuery(limit = 10) {
  return useQuery({
    queryKey: queryKeys.dashboard.audits(limit),
    queryFn: async () => {
      const res = await listAudits(limit)
      return res.data || []
    }
  })
}

export function useRagOpsQuery(enabled = true) {
  return useQuery({
    queryKey: queryKeys.dashboard.ragOps,
    enabled,
    queryFn: async () => {
      const res = await getRagOps()
      return res.data
    }
  })
}

export function useQaHistoryQuery(page: number, size = 10) {
  return useQuery({
    queryKey: queryKeys.qa.history(page, size),
    queryFn: async () => {
      const res = await listQaHistory({ page, size })
      return res.data
    }
  })
}
