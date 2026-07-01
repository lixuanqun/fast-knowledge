import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { saveWriterDocument } from '@/api'
import { queryKeys } from '@/lib/query-keys'

export function useSaveWriterDocumentMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      kbId,
      title,
      content
    }: {
      kbId: number
      title: string
      content: string
    }) => saveWriterDocument(kbId, title, content),
    onSuccess: (_data, { kbId }) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.kbs.documents(kbId) })
      queryClient.invalidateQueries({ queryKey: queryKeys.dashboard.stats })
    }
  })
}
