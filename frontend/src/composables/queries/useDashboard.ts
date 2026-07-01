import { useQuery } from '@tanstack/vue-query'
import { getDashboardStats, listAudits } from '@/api'
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
