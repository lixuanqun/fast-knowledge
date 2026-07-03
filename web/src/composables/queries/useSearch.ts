import { useMutation } from '@tanstack/vue-query'
import { search, type SearchHit } from '@/api/search'

export function useSearchMutation() {
  return useMutation({
    mutationFn: async ({
      kbId,
      query,
      topK,
      docType
    }: {
      kbId: number
      query: string
      topK?: number
      docType?: string
    }) => {
      const res = await search(kbId, query, topK, docType)
      return (res.data || []) as SearchHit[]
    }
  })
}
