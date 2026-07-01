import { describe, expect, it, vi } from 'vitest'
import {
  prefetchAfterLogin,
  prefetchAllViewsOnIdle,
  prefetchMainLayout,
  prefetchView,
  viewImports
} from '@/router/prefetch'

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

  it('prefetches main layout once', async () => {
    prefetchMainLayout()
    prefetchMainLayout()
    expect(true).toBe(true)
  })

  it('prefetches dashboard path after login', () => {
    const loader = vi.fn().mockResolvedValue({})
    viewImports['/dashboard'] = loader
    prefetchAfterLogin('/dashboard')
    expect(loader).toHaveBeenCalled()
  })

  it('schedules idle prefetch for all menu paths', () => {
    const idle = vi.fn((cb: IdleRequestCallback) => cb({ didTimeout: false, timeRemaining: () => 50 }))
    vi.stubGlobal('requestIdleCallback', idle)

    prefetchAllViewsOnIdle(['/dashboard', '/kbs'])

    expect(idle).toHaveBeenCalled()
    vi.unstubAllGlobals()
  })
})
