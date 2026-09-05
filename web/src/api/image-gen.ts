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

/** 以 blob 拉取生成图片（后端已持久化到存储资产，无需前端拼接临时链接） */
export function fetchImageGenImage(taskId: string) {
  return request.get<any, Blob>(`/image-gen/tasks/${taskId}/image`, {
    responseType: 'blob',
    timeout: 60000
  })
}
