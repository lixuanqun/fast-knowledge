import { describe, expect, it } from 'vitest'
import { mapChatMessages } from '@/composables/queries/useChat'

describe('mapChatMessages', () => {
  it('parses sources json string', () => {
    const result = mapChatMessages([
      {
        role: 'assistant',
        content: 'hello',
        sources: JSON.stringify([{ documentTitle: 'doc', content: 'chunk' }])
      }
    ])
    expect(result[0].sources).toEqual([{ documentTitle: 'doc', content: 'chunk' }])
  })

  it('leaves sources undefined when absent', () => {
    const result = mapChatMessages([{ role: 'user', content: 'hi' }])
    expect(result[0].sources).toBeUndefined()
  })
})
