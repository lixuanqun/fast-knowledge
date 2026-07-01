import request from '@/utils/request'

export function saveWriterDocument(kbId: number, title: string, content: string) {
  return request.post('/writer/save', { kbId, title, content })
}
