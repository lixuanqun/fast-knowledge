import { describe, expect, it, vi } from 'vitest'
import { prefetchAllViewsOnIdle, prefetchView, viewImports } from '@/router/prefetch'

describe('prefetch', () => {
  it('loads a view chunk once', async () => {
    const loader = vi.fn().mockResolvedValue({})
    const path = '/__test__'
    viewImports[path] = loader

    prefetchView(path)
    prefetchView(path)

    expect(loader).toHaveBeenCalledTimes(1)
    delete viewImports[path]
  })

  it('schedules idle prefetch for all menu paths', () => {
    const idle = vi.fn((cb: IdleRequestCallback) => cb({ didTimeout: false, timeRemaining: () => 50 }))
    vi.stubGlobal('requestIdleCallback', idle)

    prefetchAllViewsOnIdle(['/dashboard', '/kbs'])

    expect(idle).toHaveBeenCalled()
    vi.unstubAllGlobals()
  })
})
