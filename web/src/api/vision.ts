import request from '@/utils/request'

export function askAboutImage(image: File, question: string) {
  const form = new FormData()
  form.append('image', image)
  form.append('question', question)
  return request.post<any, { data: string }>('/vision/ask', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}
