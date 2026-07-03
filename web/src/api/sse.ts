import { getToken } from '@/utils/auth'
import { API_BASE } from '@/utils/api-base'

import type { SearchHit } from './search'

export interface StreamDoneMeta {
  sessionId?: number
  sources?: SearchHit[]
}

export async function consumeSse(
  url: string,
  body: object,
  onChunk: (text: string) => void,
  onDone?: (meta?: StreamDoneMeta) => void
): Promise<void> {
  const token = getToken()
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify(body)
  })
  if (!res.ok) {
    const errText = await res.text()
    throw new Error(errText || `请求失败 (${res.status})`)
  }
  const reader = res.body?.getReader()
  if (!reader) return

  const decoder = new TextDecoder()
  let buffer = ''
  let eventName = 'message'

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const parts = buffer.split('\n\n')
    buffer = parts.pop() || ''

    for (const part of parts) {
      const lines = part.split('\n')
      let data = ''
      eventName = 'message'
      for (const line of lines) {
        if (line.startsWith('event:')) {
          eventName = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          data += line.slice(5).trim()
        }
      }
      if (!data) continue
      if (eventName === 'error') {
        throw new Error(data)
      }
      if (eventName === 'done') {
        if (data !== '[DONE]') {
          try {
            onDone?.(JSON.parse(data))
          } catch {
            onDone?.()
          }
        } else {
          onDone?.()
        }
        continue
      }
      if (data !== '[DONE]') onChunk(data)
    }
  }
}

export async function streamChat(
  body: object,
  onChunk: (text: string) => void,
  onDone?: (meta?: StreamDoneMeta) => void
): Promise<void> {
  return consumeSse(`${API_BASE}/chat/messages/stream`, body, onChunk, onDone)
}

export async function streamWriter(body: object, onChunk: (text: string) => void): Promise<void> {
  return consumeSse(`${API_BASE}/writer/generate`, body, onChunk)
}
