import { describe, expect, it } from 'vitest'
import { indexStatusMeta, permissionLabel, visibilityLabel } from '@/utils/format'

describe('format utils', () => {
  it('maps visibility codes to labels', () => {
    expect(visibilityLabel('PUBLIC')).toBe('公开')
    expect(visibilityLabel('PRIVATE')).toBe('私有')
    expect(visibilityLabel('UNKNOWN')).toBe('私有')
  })

  it('maps index status codes to labels', () => {
    expect(indexStatusMeta('PENDING').label).toBe('待处理')
    expect(indexStatusMeta('INDEXING').label).toBe('索引中')
    expect(indexStatusMeta('INDEXED').label).toBe('已索引')
    expect(indexStatusMeta('FAILED').label).toBe('失败')
  })

  it('maps permission codes to labels', () => {
    expect(permissionLabel('READ')).toBe('只读')
    expect(permissionLabel('WRITE')).toBe('编辑')
    expect(permissionLabel('ADMIN')).toBe('管理')
  })
})
