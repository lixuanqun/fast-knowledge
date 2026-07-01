import request from '@/utils/request'

export function listFailedTasks(kbId?: number, limit = 20) {
  const kbParam = kbId != null ? `&kbId=${kbId}` : ''
  return request.get(`/index-tasks/failed?limit=${limit}${kbParam}`)
}

export function retryIndexTask(documentId: number) {
  return request.post(`/index-tasks/${documentId}/retry`)
}
