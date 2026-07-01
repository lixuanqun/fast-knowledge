export * from './auth'
export * from './kb'
export * from './search'
export * from './config'

import request from '@/utils/request'
import { getToken } from '@/utils/auth'
import { API_BASE } from '@/utils/api-base'

export interface KbUser {
  id: number
  username: string
  displayName: string
  role: string
  status: number
  createdAt?: string
  updatedAt?: string
}

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

export function listUsers() {
  return request.get<any, { data: KbUser[] }>('/users')
}

export function createUser(data: {
  username: string
  password: string
  displayName?: string
  role: string
}) {
  return request.post('/users', data)
}

export function updateUser(id: number, data: Partial<KbUser>) {
  return request.put(`/users/${id}`, data)
}

export function deleteUser(id: number) {
  return request.delete(`/users/${id}`)
}

export function resetUserPassword(id: number, newPassword: string) {
  return request.post(`/users/${id}/reset-password`, { newPassword })
}

export function getDashboardStats() {
  return request.get('/dashboard/stats')
}

export function listAudits(limit = 20) {
  return request.get(`/dashboard/audits?limit=${limit}`)
}

export function addKbMember(kbId: number, username: string, permission: string) {
  return request.post(`/kbs/${kbId}/members`, { username, permission })
}

export function removeKbMember(kbId: number, memberId: number) {
  return request.delete(`/kbs/${kbId}/members/${memberId}`)
}

export function listFailedTasks(kbId?: number, limit = 20) {
  const kbParam = kbId != null ? `&kbId=${kbId}` : ''
  return request.get(`/index-tasks/failed?limit=${limit}${kbParam}`)
}

export function retryIndexTask(documentId: number) {
  return request.post(`/index-tasks/${documentId}/retry`)
}

export function saveWriterDocument(kbId: number, title: string, content: string) {
  return request.post('/writer/save', { kbId, title, content })
}

export function ask(kbId: number, question: string) {
  return request.post('/qa', { kbId, question })
}

export function listChatSessions() {
  return request.get('/chat/sessions')
}

export function createChatSession(kbId?: number, title?: string) {
  return request.post('/chat/sessions', { kbId, title })
}

export function getChatMessages(sessionId: number) {
  return request.get(`/chat/sessions/${sessionId}/messages`)
}

export function deleteChatSession(sessionId: number) {
  return request.delete(`/chat/sessions/${sessionId}`)
}

export interface StreamDoneMeta {
  sessionId?: number
  sources?: Array<{ documentTitle: string; content: string; score?: number }>
}

async function consumeSse(
  url: string,
  body: object,
  onChunk: (text: string) => void,
  onDone?: (meta?: StreamDoneMeta) => void
): Promise<void> {
  const token = getToken()
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify(body)
  })
  if (!res.ok) {
    const errText = await res.text()
    throw new Error(errText || `请求失败 (${res.status})`)
  }
  const reader = res.body?.getReader()
  if (!reader) return

  const decoder = new TextDecoder()
  let buffer = ''
  let eventName = 'message'

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const parts = buffer.split('\n\n')
    buffer = parts.pop() || ''

    for (const part of parts) {
      const lines = part.split('\n')
      let data = ''
      eventName = 'message'
      for (const line of lines) {
        if (line.startsWith('event:')) {
          eventName = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          data += line.slice(5).trim()
        }
      }
      if (!data) continue
      if (eventName === 'error') {
        throw new Error(data)
      }
      if (eventName === 'done') {
        if (data !== '[DONE]') {
          try {
            onDone?.(JSON.parse(data))
          } catch {
            onDone?.()
          }
        } else {
          onDone?.()
        }
        continue
      }
      if (data !== '[DONE]') onChunk(data)
    }
  }
}

export async function streamChat(
  body: object,
  onChunk: (text: string) => void,
  onDone?: (meta?: StreamDoneMeta) => void
): Promise<void> {
  return consumeSse(`${API_BASE}/chat/messages/stream`, body, onChunk, onDone)
}

export async function streamWriter(body: object, onChunk: (text: string) => void): Promise<void> {
  return consumeSse(`${API_BASE}/writer/generate`, body, onChunk)
}

// backward-compatible aliases
export { login, logout, changePassword, getCurrentUser } from './auth'
export { listKbs, createKb, updateKb, deleteKb, getKb, listDocuments, uploadDocument, deleteDocument, reindexDocument, getDocument, rebuildKbIndex, listKbMembers } from './kb'
export { search } from './search'
