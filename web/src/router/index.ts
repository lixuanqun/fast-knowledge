import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useConfigStore } from '@/stores/config'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('@/views/login/index.vue'), meta: { public: true } },
    { path: '/setup', component: () => import('@/views/setup/index.vue') },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          component: () => import('@/views/dashboard/index.vue'),
          meta: { keepAlive: true, title: '概览' }
        },
        {
          path: 'kbs',
          component: () => import('@/views/knowledge-base/list.vue'),
          meta: { keepAlive: true, title: '知识库' }
        },
        {
          path: 'kbs/:id',
          component: () => import('@/views/knowledge-base/detail.vue'),
          meta: { title: '知识库详情' }
        },
        {
          path: 'search',
          component: () => import('@/views/search/index.vue'),
          meta: { keepAlive: true, title: '智能检索' }
        },
        {
          path: 'qa',
          component: () => import('@/views/qa/index.vue'),
          meta: { keepAlive: true, title: '智能问答' }
        },
        {
          path: 'chat',
          component: () => import('@/views/chat/index.vue'),
          meta: { keepAlive: true, title: '智能对话' }
        },
        {
          path: 'writer',
          component: () => import('@/views/writer/index.vue'),
          meta: { keepAlive: true, title: '智能写文档' }
        },
        {
          path: 'settings',
          component: () => import('@/views/settings/index.vue'),
          meta: { keepAlive: true, title: '设置与隐私' }
        },
        {
          path: 'users',
          component: () => import('@/views/users/index.vue'),
          meta: { keepAlive: true, adminOnly: true, title: '用户管理' }
        }
      ]
    }
  ]
})

router.beforeEach(async (to, _from, next) => {
  const authStore = useAuthStore()

  if (to.meta.public) {
    next()
    return
  }
  if (!authStore.isLoggedIn) {
    next('/login')
    return
  }
  if (authStore.mustChangePassword && to.path !== '/setup') {
    next('/setup')
    return
  }

  const configStore = useConfigStore()
  try {
    await configStore.ensureLoaded()
  } catch {
    /* 配置拉取失败时不阻断导航 */
  }

  if (configStore.needsSetup && to.path !== '/setup') {
    next('/setup')
    return
  }
  if (configStore.setupComplete && to.path === '/setup' && !authStore.mustChangePassword) {
    next('/dashboard')
    return
  }
  if (to.meta.adminOnly && !authStore.isAdmin) {
    next('/dashboard')
    return
  }
  next()
})

export default router
