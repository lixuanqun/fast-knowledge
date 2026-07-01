import request from '@/utils/request'

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
