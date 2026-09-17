import request from '@/utils/request'

export interface SearchHit {
  chunkId: number
  documentId: number
  documentTitle: string
  content: string
  score: number
  section?: string
  docType?: string
  docNo?: string
  /** WP5 溯源：所在页码（OCR/分页文档） */
  pageNo?: number
  /** WP5 溯源：锚点类型 text / table */
  anchorType?: string
}

export function search(kbId: number, query: string, topK?: number, docType?: string) {
  return request.post<any, { data: SearchHit[] }>('/search', { kbId, query, topK, docType })
}

export function qa(kbId: number, question: string) {
  return request.post('/qa', { kbId, question })
}
