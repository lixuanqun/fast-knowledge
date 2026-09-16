import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import {
  addEvalCase,
  createEvalDataset,
  deleteEvalCase,
  deleteEvalDataset,
  getEvalRun,
  listEvalCases,
  listEvalDatasets,
  listEvalRuns,
  startEvalRun,
  updateEvalCase,
  updateEvalDataset,
  type EvalCase,
  type EvalDataset
} from '@/api/evals'
import { queryKeys } from '@/lib/query-keys'

export function useEvalDatasetsQuery(kbId?: () => number | undefined) {
  return useQuery({
    queryKey: [...queryKeys.evals.datasets()],
    queryFn: async () => (await listEvalDatasets(kbId?.())).data || []
  })
}

export function useEvalCasesQuery(datasetId: () => number | undefined) {
  return useQuery({
    queryKey: ['evals', 'datasets', datasetId() ?? 0, 'cases'],
    queryFn: async () => (await listEvalCases(datasetId()!)).data || [],
    enabled: () => datasetId() != null
  })
}

export function useEvalRunsQuery(datasetId: () => number | undefined, hasRunning: () => boolean) {
  return useQuery({
    queryKey: ['evals', 'datasets', datasetId() ?? 0, 'runs'],
    queryFn: async () => (await listEvalRuns(datasetId()!)).data || [],
    enabled: () => datasetId() != null,
    refetchInterval: () => (hasRunning() ? 2000 : false)
  })
}

export function useEvalRunQuery(runId: () => number | undefined) {
  return useQuery({
    queryKey: ['evals', 'runs', runId() ?? 0],
    queryFn: async () => (await getEvalRun(runId()!)).data,
    enabled: () => runId() != null
  })
}

function useInvalidateEvals() {
  const queryClient = useQueryClient()
  return () => {
    queryClient.invalidateQueries({ queryKey: ['evals'] })
  }
}

export function useCreateEvalDatasetMutation() {
  const invalidate = useInvalidateEvals()
  return useMutation({
    mutationFn: (data: { name: string; kbId: number; topK?: number; description?: string }) =>
      createEvalDataset(data),
    onSuccess: invalidate
  })
}

export function useUpdateEvalDatasetMutation() {
  const invalidate = useInvalidateEvals()
  return useMutation({
    mutationFn: ({ id, patch }: { id: number; patch: Partial<EvalDataset> }) => updateEvalDataset(id, patch),
    onSuccess: invalidate
  })
}

export function useDeleteEvalDatasetMutation() {
  const invalidate = useInvalidateEvals()
  return useMutation({
    mutationFn: (id: number) => deleteEvalDataset(id),
    onSuccess: invalidate
  })
}

export function useAddEvalCaseMutation() {
  const invalidate = useInvalidateEvals()
  return useMutation({
    mutationFn: ({
      datasetId,
      data
    }: {
      datasetId: number
      data: { question: string; expectedChunkIds?: number[]; expectedKeywords?: string[]; enabled?: number }
    }) => addEvalCase(datasetId, data),
    onSuccess: invalidate
  })
}

export function useUpdateEvalCaseMutation() {
  const invalidate = useInvalidateEvals()
  return useMutation({
    mutationFn: ({ id, patch }: { id: number; patch: Partial<EvalCase> }) => updateEvalCase(id, patch),
    onSuccess: invalidate
  })
}

export function useDeleteEvalCaseMutation() {
  const invalidate = useInvalidateEvals()
  return useMutation({
    mutationFn: (id: number) => deleteEvalCase(id),
    onSuccess: invalidate
  })
}

export function useStartEvalRunMutation() {
  const invalidate = useInvalidateEvals()
  return useMutation({
    mutationFn: (datasetId: number) => startEvalRun(datasetId),
    onSuccess: invalidate
  })
}
