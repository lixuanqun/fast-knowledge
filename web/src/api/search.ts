import request from '@/utils/request'

export interface SearchHit {
  chunkId: number
  documentId: number
  documentTitle: string
  content: string
  score: number
}

export function search(kbId: number, query: string, topK?: number, alpha?: number) {
  return request.post<any, { data: SearchHit[] }>('/search', { kbId, query, topK, alpha })
}

export function qa(kbId: number, question: string) {
  return request.post('/qa', { kbId, question })
}
