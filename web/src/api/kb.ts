import request from '@/utils/request'

export interface KnowledgeBase {
  id: number
  name: string
  description: string
  workspaceId?: number
  ownerId: number
  visibility: string
  searchAlpha: number
  searchTopK: number
}

export interface KbDocument {
  id: number
  kbId: number
  title: string
  fileName: string
  fileType: string
  fileSize: number
  indexStatus: string
  chunkCount: number
}

export function listKbs() {
  return request.get<any, { data: KnowledgeBase[] }>('/kbs')
}

export function createKb(data: Partial<KnowledgeBase>) {
  return request.post('/kbs', data)
}

export function updateKb(id: number, data: Partial<KnowledgeBase>) {
  return request.put(`/kbs/${id}`, data)
}

export function deleteKb(id: number) {
  return request.delete(`/kbs/${id}`)
}

export function getKb(id: number) {
  return request.get<any, { data: KnowledgeBase }>(`/kbs/${id}`)
}

export function listDocuments(kbId: number) {
  return request.get<any, { data: KbDocument[] }>(`/kbs/${kbId}/documents`)
}

export function uploadDocument(kbId: number, file: File) {
  const form = new FormData()
  form.append('file', file)
  return request.post(`/kbs/${kbId}/documents/upload`, form)
}

export function deleteDocument(kbId: number, docId: number) {
  return request.delete(`/kbs/${kbId}/documents/${docId}`)
}

export function reindexDocument(kbId: number, docId: number) {
  return request.post(`/kbs/${kbId}/documents/${docId}/reindex`)
}

export function getDocument(kbId: number, docId: number) {
  return request.get<any, { data: KbDocument }>(`/kbs/${kbId}/documents/${docId}`)
}

export function rebuildKbIndex(kbId: number) {
  return request.post(`/index-tasks/rebuild/${kbId}`)
}

export function listKbMembers(kbId: number) {
  return request.get(`/kbs/${kbId}/members`)
}

export function addKbMember(kbId: number, username: string, permission: string) {
  return request.post(`/kbs/${kbId}/members`, { username, permission })
}

export function removeKbMember(kbId: number, memberId: number) {
  return request.delete(`/kbs/${kbId}/members/${memberId}`)
}
