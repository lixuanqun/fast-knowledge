import request from '@/utils/request'

export function ask(kbId: number, question: string) {
  return request.post('/qa', { kbId, question })
}

export interface QaHistoryItem {
  id: number
  userId?: number
  kbId: number
  question: string
  answer: string
  sources?: string
  sourceCount?: number
  createdAt?: string
}

export function listQaHistory(params?: {
  page?: number
  size?: number
  kbId?: number
  userId?: number
}) {
  return request.get<any, { data: { records: QaHistoryItem[]; total: number; current: number; size: number } }>(
    '/qa/history',
    { params }
  )
}

export async function exportQaHistory(params?: { kbId?: number; userId?: number; limit?: number }) {
  const res = await request.get('/qa/history/export', {
    params,
    responseType: 'blob'
  })
  return res as unknown as Blob
}
