import { describe, expect, it } from 'vitest'
import { visibilityLabel } from '@/utils/format'

describe('format utils', () => {
  it('maps visibility codes to labels', () => {
    expect(visibilityLabel('PUBLIC')).toBe('公开')
    expect(visibilityLabel('PRIVATE')).toBe('私有')
    expect(visibilityLabel('UNKNOWN')).toBe('私有')
  })
})
