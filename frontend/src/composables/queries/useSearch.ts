import { useMutation } from '@tanstack/vue-query'
import { search, type SearchHit } from '@/api/search'

export function useSearchMutation() {
  return useMutation({
    mutationFn: async ({
      kbId,
      query,
      topK
    }: {
      kbId: number
      query: string
      topK?: number
    }) => {
      const res = await search(kbId, query, topK)
      return (res.data || []) as SearchHit[]
    }
  })
}
