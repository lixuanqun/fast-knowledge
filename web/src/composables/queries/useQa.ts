import { useMutation } from '@tanstack/vue-query'
import { ask } from '@/api'
import type { SearchHit } from '@/api/search'

export interface QaResult {
  answer: string
  sources?: SearchHit[]
}

export function useAskMutation() {
  return useMutation({
    mutationFn: async ({ kbId, question }: { kbId: number; question: string }) => {
      const res = await ask(kbId, question)
      return res.data as QaResult
    }
  })
}
