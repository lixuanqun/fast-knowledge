import { useQuery } from '@tanstack/vue-query'
import { getSystemConfig } from '@/api/config'
import { queryKeys } from '@/lib/query-keys'

export function useSystemConfigQuery() {
  return useQuery({
    queryKey: queryKeys.system.config,
    queryFn: async () => {
      const res = await getSystemConfig()
      return res.data
    }
  })
}
