import { useQuery } from '@tanstack/vue-query'
import { listWorkspaces } from '@/api/workspace'
import { queryKeys } from '@/lib/query-keys'

export function useWorkspacesQuery() {
  return useQuery({
    queryKey: queryKeys.workspaces.all,
    queryFn: async () => {
      const res = await listWorkspaces()
      return res.data || []
    }
  })
}
