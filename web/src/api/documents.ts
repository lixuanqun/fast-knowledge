import request from '@/utils/request'

export interface DocumentPreview {
  documentId: number
  title: string
  fileType: string
  previewMode: string
  content: string
  truncated: boolean
  contentLength: number
}

export interface DocumentChunk {
  id: number
  chunkIndex: number
  content: string
  tokenCount: number
}

export function getDocumentPreview(kbId: number, docId: number) {
  return request.get<any, { data: DocumentPreview }>(`/kbs/${kbId}/documents/${docId}/preview`)
}

export function getDocumentChunks(kbId: number, docId: number) {
  return request.get<any, { data: DocumentChunk[] }>(`/kbs/${kbId}/documents/${docId}/chunks`)
}
