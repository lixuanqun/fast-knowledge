import request from '@/utils/request'

export interface ImageGenTask {
  taskId: string
  status: string
  imageUrl?: string
}

export function submitImageGen(prompt: string) {
  return request.post<any, { data: { taskId: string } }>('/image-gen/tasks', { prompt }, { timeout: 30000 })
}

export function getImageGenTask(taskId: string) {
  return request.get<any, { data: ImageGenTask }>(`/image-gen/tasks/${taskId}`)
}

export function imageGenDownloadUrl(taskId: string) {
  return `${import.meta.env.VITE_API_BASE || '/api/v1'}/image-gen/tasks/${taskId}/download`
}
