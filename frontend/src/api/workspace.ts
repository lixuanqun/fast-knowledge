import request from '@/utils/request'

export interface Workspace {
  id: number
  name: string
  ownerId: number
  createdAt?: string
  updatedAt?: string
}

export function listWorkspaces() {
  return request.get<any, { data: Workspace[] }>('/workspaces')
}

export function getWorkspace(id: number) {
  return request.get<any, { data: Workspace }>(`/workspaces/${id}`)
}
