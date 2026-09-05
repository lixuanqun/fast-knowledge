import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { ask } from '@/api'
import type { SearchHit } from '@/api/search'
import { queryKeys } from '@/lib/query-keys'

export interface QaResult {
  answer: string
  sources?: SearchHit[]
}

export function useAskMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ kbId, question }: { kbId: number; question: string }) => {
      const res = await ask(kbId, question)
      return res.data as QaResult
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.dashboard.stats })
    }
  })
}