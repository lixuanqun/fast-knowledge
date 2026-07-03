import request from '@/utils/request'

export interface DocumentMetadata {
  docType?: string
  docNo?: string
  effectiveDate?: string
  expireDate?: string
  department?: string
  tags?: string
}

export interface DocumentPreview {
  documentId: number
  title: string
  fileType: string
  previewMode: string
  content: string
  truncated: boolean
  contentLength: number
  highlightSnippet?: string
  docType?: string
  docNo?: string
}

export interface DocumentChunk {
  id: number
  chunkIndex: number
  content: string
  tokenCount: number
  sectionTitle?: string
}

export function getDocumentPreview(kbId: number, docId: number, chunkId?: number) {
  return request.get<any, { data: DocumentPreview }>(`/kbs/${kbId}/documents/${docId}/preview`, {
    params: chunkId ? { chunkId } : undefined
  })
}

export function getDocumentChunks(kbId: number, docId: number) {
  return request.get<any, { data: DocumentChunk[] }>(`/kbs/${kbId}/documents/${docId}/chunks`)
}

export function updateDocumentMetadata(
  kbId: number,
  docId: number,
  metadata: DocumentMetadata
) {
  return request.put(`/kbs/${kbId}/documents/${docId}/metadata`, metadata)
}
