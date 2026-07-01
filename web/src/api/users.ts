import request from '@/utils/request'

export interface KbUser {
  id: number
  username: string
  displayName: string
  role: string
  status: number
  createdAt?: string
  updatedAt?: string
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
