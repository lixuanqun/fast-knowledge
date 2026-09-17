import { getToken } from '@/utils/auth'
import { API_BASE } from '@/utils/api-base'

import type { SearchHit } from './search'

export interface StreamDoneMeta {
  sessionId?: number
  sources?: SearchHit[]
}

/** 写文档多步编排的阶段事件载荷（event: step） */
export interface WriterStep {
  stage: string
  sectionIndex?: number
  sectionTotal?: number
  title?: string
}

/** WP6 Agentic 检索步骤事件载荷（event: retrieval-step） */
export interface RetrievalStep {
  round: number
  mode: 'single' | 'agentic' | 'refine'
  queries: string[]
  totalHits: number
}

export async function consumeSse(
  url: string,
  body: object,
  onChunk: (text: string) => void,
  onDone?: (meta?: StreamDoneMeta) => void,
  onStep?: (step: WriterStep) => void,
  signal?: AbortSignal,
  onRetrievalStep?: (step: RetrievalStep) => void
): Promise<void> {
  const token = getToken()
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify(body),
    signal
  })
  if (!res.ok) {
    const errText = await res.text()
    let msg = errText || `请求失败 (${res.status})`
    try {
      const j = JSON.parse(errText)
      if (j?.message) msg = j.message
    } catch {
      /* 非 JSON 错误体原样保留 */
    }
    throw new Error(msg)
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
      if (eventName === 'step') {
        try {
          onStep?.(JSON.parse(data))
        } catch {
          /* 非法 step 载荷忽略 */
        }
        continue
      }
      if (eventName === 'retrieval-step') {
        try {
          onRetrievalStep?.(JSON.parse(data))
        } catch {
          /* 非法载荷忽略 */
        }
        continue
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
  onDone?: (meta?: StreamDoneMeta) => void,
  signal?: AbortSignal,
  onRetrievalStep?: (step: RetrievalStep) => void
): Promise<void> {
  return consumeSse(`${API_BASE}/chat/messages/stream`, body, onChunk, onDone, undefined, signal, onRetrievalStep)
}

export async function streamWriter(
  body: object,
  onChunk: (text: string) => void,
  onStep?: (step: WriterStep) => void,
  signal?: AbortSignal
): Promise<void> {
  return consumeSse(`${API_BASE}/writer/generate`, body, onChunk, undefined, onStep, signal)
}
