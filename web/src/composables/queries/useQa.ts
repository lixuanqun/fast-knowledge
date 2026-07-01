import { useMutation } from '@tanstack/vue-query'
import { ask } from '@/api'

export interface QaResult {
  answer: string
  sources?: Array<{ documentTitle: string; content: string; score?: number }>
}

export function useAskMutation() {
  return useMutation({
    mutationFn: async ({ kbId, question }: { kbId: number; question: string }) => {
      const res = await ask(kbId, question)
      return res.data as QaResult
    }
  })
}
