import request from '@/utils/request'

export function getDashboardStats() {
  return request.get('/dashboard/stats')
}

export function listAudits(limit = 20) {
  return request.get(`/dashboard/audits?limit=${limit}`)
}
