const prefetched = new Set<string>()

/** 与 router 懒加载路径一致，用于侧栏悬停预取 */
export const viewImports: Record<string, () => Promise<unknown>> = {
  '/dashboard': () => import('@/views/dashboard/index.vue'),
  '/kbs': () => import('@/views/knowledge-base/list.vue'),
  '/search': () => import('@/views/search/index.vue'),
  '/qa': () => import('@/views/qa/index.vue'),
  '/chat': () => import('@/views/chat/index.vue'),
  '/writer': () => import('@/views/writer/index.vue'),
  '/users': () => import('@/views/users/index.vue')
}

export function prefetchView(path: string) {
  if (prefetched.has(path)) return
  const loader = viewImports[path]
  if (!loader) return
  prefetched.add(path)
  void loader()
}

export function prefetchMainLayout() {
  const key = '__main_layout__'
  if (prefetched.has(key)) return
  prefetched.add(key)
  void import('@/layouts/MainLayout.vue')
}

/** 登录成功后预取主框架与目标页，缩短首屏等待 */
export function prefetchAfterLogin(target: '/setup' | '/dashboard') {
  prefetchMainLayout()
  if (target === '/setup') {
    void import('@/views/setup/index.vue')
    return
  }
  prefetchView('/dashboard')
  prefetchAllViewsOnIdle(['/dashboard', '/kbs'])
}

/** 浏览器空闲时预取所有主菜单页面 */
export function prefetchAllViewsOnIdle(paths?: string[]) {
  const run = () => {
    const targets = paths ?? Object.keys(viewImports)
    for (const path of targets) {
      prefetchView(path)
    }
  }

  if (typeof requestIdleCallback !== 'undefined') {
    requestIdleCallback(run, { timeout: 3000 })
  } else {
    setTimeout(run, 1500)
  }
}
