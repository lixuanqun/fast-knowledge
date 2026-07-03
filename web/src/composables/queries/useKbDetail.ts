import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import type { MaybeRefOrGetter } from 'vue'
import { computed, toValue } from 'vue'
import {
  addKbMember,
  deleteDocument,
  getDocument,
  getDocumentChunks,
  getDocumentPreview,
  getKb,
  listDocuments,
  listFailedTasks,
  listKbMembers,
  rebuildKbIndex,
  reindexDocument,
  removeKbMember,
  retryIndexTask,
  updateKb,
  uploadDocument,
  updateDocumentMetadata,
  type DocumentMetadata,
  type KbDocument,
  type KnowledgeBase
} from '@/api'
import { queryKeys } from '@/lib/query-keys'

export interface KbMember {
  id: number
  username: string
  displayName: string
  permission: string
}

export interface FailedIndexTask {
  documentId: number
  status: string
  retryCount: number
  errorMsg: string
}

function kbDetailKeys(kbId: number) {
  return {
    detail: queryKeys.kbs.detail(kbId),
    documents: queryKeys.kbs.documents(kbId),
    members: queryKeys.kbs.members(kbId),
    failedTasks: queryKeys.kbs.failedTasks(kbId)
  }
}

function invalidateKbDetail(queryClient: ReturnType<typeof useQueryClient>, kbId: number) {
  const keys = kbDetailKeys(kbId)
  queryClient.invalidateQueries({ queryKey: keys.detail })
  queryClient.invalidateQueries({ queryKey: keys.documents })
  queryClient.invalidateQueries({ queryKey: keys.members })
  queryClient.invalidateQueries({ queryKey: keys.failedTasks })
  queryClient.invalidateQueries({ queryKey: queryKeys.kbs.all })
  queryClient.invalidateQueries({ queryKey: queryKeys.dashboard.stats })
}

export function useKbQuery(kbId: MaybeRefOrGetter<number>) {
  return useQuery({
    queryKey: computed(() => queryKeys.kbs.detail(toValue(kbId))),
    queryFn: async () => {
      const res = await getKb(toValue(kbId))
      return res.data as KnowledgeBase
    },
    enabled: computed(() => Number.isFinite(toValue(kbId)) && toValue(kbId) > 0)
  })
}

export function useKbDocumentsQuery(kbId: MaybeRefOrGetter<number>) {
  return useQuery({
    queryKey: computed(() => queryKeys.kbs.documents(toValue(kbId))),
    queryFn: async () => {
      const res = await listDocuments(toValue(kbId))
      return (res.data || []) as KbDocument[]
    },
    enabled: computed(() => Number.isFinite(toValue(kbId)) && toValue(kbId) > 0)
  })
}

export function useKbMembersQuery(kbId: MaybeRefOrGetter<number>) {
  return useQuery({
    queryKey: computed(() => queryKeys.kbs.members(toValue(kbId))),
    queryFn: async () => {
      const res = await listKbMembers(toValue(kbId))
      return (res.data || []) as KbMember[]
    },
    enabled: computed(() => Number.isFinite(toValue(kbId)) && toValue(kbId) > 0)
  })
}

export function useKbFailedTasksQuery(kbId: MaybeRefOrGetter<number>) {
  return useQuery({
    queryKey: computed(() => queryKeys.kbs.failedTasks(toValue(kbId))),
    queryFn: async () => {
      const res = await listFailedTasks(toValue(kbId))
      return (res.data || []) as FailedIndexTask[]
    },
    enabled: computed(() => Number.isFinite(toValue(kbId)) && toValue(kbId) > 0)
  })
}

export function useUpdateKbMutation(kbId: MaybeRefOrGetter<number>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: Partial<KnowledgeBase>) => updateKb(toValue(kbId), data),
    onSuccess: () => invalidateKbDetail(queryClient, toValue(kbId))
  })
}

export function useUploadDocumentMutation(kbId: MaybeRefOrGetter<number>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ file, metadata }: { file: File; metadata?: DocumentMetadata }) =>
      uploadDocument(toValue(kbId), file, metadata),
    onSuccess: () => {
      const id = toValue(kbId)
      queryClient.invalidateQueries({ queryKey: queryKeys.kbs.documents(id) })
      queryClient.invalidateQueries({ queryKey: queryKeys.kbs.failedTasks(id) })
      queryClient.invalidateQueries({ queryKey: queryKeys.dashboard.stats })
    }
  })
}

export function useUpdateDocumentMetadataMutation(kbId: MaybeRefOrGetter<number>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ docId, metadata }: { docId: number; metadata: DocumentMetadata }) =>
      updateDocumentMetadata(toValue(kbId), docId, metadata),
    onSuccess: (_data, vars) => {
      const id = toValue(kbId)
      queryClient.invalidateQueries({ queryKey: queryKeys.kbs.documents(id) })
      queryClient.invalidateQueries({ queryKey: queryKeys.kbs.document(id, vars.docId) })
    }
  })
}

export function useDeleteDocumentMutation(kbId: MaybeRefOrGetter<number>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (docId: number) => deleteDocument(toValue(kbId), docId),
    onSuccess: () => invalidateKbDetail(queryClient, toValue(kbId))
  })
}

export function useReindexDocumentMutation(kbId: MaybeRefOrGetter<number>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (docId: number) => reindexDocument(toValue(kbId), docId),
    onSuccess: () => {
      const id = toValue(kbId)
      queryClient.invalidateQueries({ queryKey: queryKeys.kbs.documents(id) })
      queryClient.invalidateQueries({ queryKey: queryKeys.kbs.failedTasks(id) })
    }
  })
}

export function useAddKbMemberMutation(kbId: MaybeRefOrGetter<number>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ username, permission }: { username: string; permission: string }) =>
      addKbMember(toValue(kbId), username, permission),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.kbs.members(toValue(kbId)) })
    }
  })
}

export function useRemoveKbMemberMutation(kbId: MaybeRefOrGetter<number>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (memberId: number) => removeKbMember(toValue(kbId), memberId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.kbs.members(toValue(kbId)) })
    }
  })
}

export function useRetryIndexTaskMutation(kbId: MaybeRefOrGetter<number>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (documentId: number) => retryIndexTask(documentId),
    onSuccess: () => {
      const id = toValue(kbId)
      queryClient.invalidateQueries({ queryKey: queryKeys.kbs.documents(id) })
      queryClient.invalidateQueries({ queryKey: queryKeys.kbs.failedTasks(id) })
    }
  })
}

export function useRebuildKbIndexMutation(kbId: MaybeRefOrGetter<number>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => rebuildKbIndex(toValue(kbId)),
    onSuccess: () => invalidateKbDetail(queryClient, toValue(kbId))
  })
}

export function useDocumentQuery(
  kbId: MaybeRefOrGetter<number>,
  docId: MaybeRefOrGetter<number | undefined>
) {
  return useQuery({
    queryKey: computed(() => queryKeys.kbs.document(toValue(kbId), toValue(docId) ?? 0)),
    queryFn: async () => {
      const res = await getDocument(toValue(kbId), toValue(docId)!)
      return res.data as KbDocument
    },
    enabled: computed(() => {
      const id = toValue(docId)
      return Number.isFinite(toValue(kbId)) && id != null && id > 0
    })
  })
}

export function useDocumentPreviewQuery(
  kbId: MaybeRefOrGetter<number>,
  docId: MaybeRefOrGetter<number | undefined>,
  highlightChunkId?: MaybeRefOrGetter<number | undefined>
) {
  return useQuery({
    queryKey: computed(() =>
      queryKeys.kbs.documentPreview(
        toValue(kbId),
        toValue(docId) ?? 0,
        toValue(highlightChunkId) ?? undefined
      )
    ),
    queryFn: async () => {
      const chunkId = toValue(highlightChunkId)
      const res = await getDocumentPreview(toValue(kbId), toValue(docId)!, chunkId ?? undefined)
      return res.data
    },
    enabled: computed(() => {
      const id = toValue(docId)
      return Number.isFinite(toValue(kbId)) && id != null && id > 0
    })
  })
}

export function useDocumentChunksQuery(
  kbId: MaybeRefOrGetter<number>,
  docId: MaybeRefOrGetter<number | undefined>
) {
  return useQuery({
    queryKey: computed(() => queryKeys.kbs.documentChunks(toValue(kbId), toValue(docId) ?? 0)),
    queryFn: async () => {
      const res = await getDocumentChunks(toValue(kbId), toValue(docId)!)
      return res.data || []
    },
    enabled: computed(() => {
      const id = toValue(docId)
      return Number.isFinite(toValue(kbId)) && id != null && id > 0
    })
  })
}
