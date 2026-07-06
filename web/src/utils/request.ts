import axios from 'axios'
import { getToken } from './auth'
import { API_BASE } from './api-base'
import router from '@/router'
import { ElMessage } from 'element-plus'
import { getActivePinia } from 'pinia'

const request = axios.create({
  baseURL: API_BASE,
  timeout: 60000
})

request.interceptors.request.use(config => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  res => {
    // Blob responses (e.g., CSV export) don't have the standard ApiResponse wrapper
    if (res.config.responseType === 'blob') {
      return res.data
    }
    const data = res.data
    if (data.code !== 0) {
      ElMessage.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message))
    }
    return data
  },
  err => {
    if (err.response?.status === 401) {
      const pinia = getActivePinia()
      if (pinia) {
        import('@/stores/auth').then(({ useAuthStore }) => {
          useAuthStore(pinia).clearSession()
        })
      } else {
        import('./auth').then(({ clearAuth }) => clearAuth())
      }
      router.push('/login')
    }
    ElMessage.error(err.message || '网络错误')
    return Promise.reject(err)
  }
)

export default request
