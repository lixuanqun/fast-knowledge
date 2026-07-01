import request from '@/utils/request'

export function ask(kbId: number, question: string) {
  return request.post('/qa', { kbId, question })
}
