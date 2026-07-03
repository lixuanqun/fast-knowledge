import request from '@/utils/request'

export interface AuditLog {
  id: number
  userId: number | null
  action: string
  targetType: string | null
  targetId: number | null
  detail: string | null
  createdAt: string
}

export interface AuditPage {
  records: AuditLog[]
  total: number
  size: number
  current: number
}

export function listAudits(params: {
  page?: number
  size?: number
  userId?: number
  action?: string
}) {
  return request.get<any, { data: AuditPage }>('/audits', { params })
}

export function exportAudits(params?: { userId?: number; action?: string; limit?: number }) {
  return request.get('/audits/export', {
    params,
    responseType: 'blob'
  })
}
