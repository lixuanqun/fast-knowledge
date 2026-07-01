import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { createKb, deleteKb, listKbs, type KnowledgeBase } from '@/api/kb'
import { queryKeys } from '@/lib/query-keys'

async function fetchKbs(): Promise<KnowledgeBase[]> {
  const res = await listKbs()
  return res.data || []
}

export function useKbsQuery() {
  return useQuery({
    queryKey: queryKeys.kbs.all,
    queryFn: fetchKbs
  })
}

export function useCreateKbMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: Partial<KnowledgeBase>) => createKb(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.kbs.all })
    }
  })
}

export function useDeleteKbMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => deleteKb(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.kbs.all })
    }
  })
}
