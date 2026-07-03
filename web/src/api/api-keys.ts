import request from '@/utils/request'

export interface ApiKey {
  id: number
  name: string
  keyPrefix: string
  kbId: number | null
  userId: number
  status: number
  createdAt: string
  lastUsedAt: string | null
}

export interface ApiKeyCreated {
  id: number
  name: string
  apiKey: string
}

export function listApiKeys() {
  return request.get<any, { data: ApiKey[] }>('/api-keys')
}

export function createApiKey(data: {
  name: string
  userId: number
  kbId?: number
}) {
  return request.post<any, { data: ApiKeyCreated }>('/api-keys', data)
}

export function revokeApiKey(id: number) {
  return request.delete(`/api-keys/${id}`)
}
