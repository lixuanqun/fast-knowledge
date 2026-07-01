<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">
        <span class="logo-icon">FK</span>
        <span class="logo-text">{{ instanceName }}</span>
      </div>
      <el-menu
        :default-active="route.path"
        router
        class="side-menu"
        background-color="#ffffff"
        text-color="#606266"
        active-text-color="#409eff"
      >
        <el-menu-item
          v-for="item in visibleMenus"
          :key="item.path"
          :index="item.path"
          @mouseenter="prefetchView(item.path)"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="header-title">{{ pageTitle }}</span>
        <div class="header-right">
          <el-tag size="small" type="info">{{ user?.displayName || user?.username }}</el-tag>
          <el-tag v-if="user?.role" size="small">{{ roleLabel }}</el-tag>
          <el-button type="primary" link @click="pwdDialog?.open()">改密</el-button>
          <el-button type="primary" link @click="handleLogout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main-content">
        <router-view v-slot="{ Component, route: childRoute }">
          <transition v-if="Component" name="page-fade" mode="out-in">
            <keep-alive v-if="childRoute.meta.keepAlive" :max="8">
              <component :is="Component" :key="childRoute.fullPath" />
            </keep-alive>
            <component :is="Component" v-else :key="childRoute.fullPath" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
    <ChangePasswordDialog ref="pwdDialog" />
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useConfigStore } from '@/stores/config'
import { prefetchAllViewsOnIdle, prefetchView } from '@/router/prefetch'
import { ChangePasswordDialog } from '@/components/async'
import {
  Odometer, Collection, Search, QuestionFilled, ChatDotRound, EditPen, User, Setting
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const configStore = useConfigStore()
const { user, isAdmin } = storeToRefs(authStore)
const { instanceName } = storeToRefs(configStore)
const pwdDialog = ref<{ open: () => void }>()

const menuItems = [
  { path: '/dashboard', label: '概览', icon: Odometer },
  { path: '/kbs', label: '知识库', icon: Collection },
  { path: '/search', label: '智能检索', icon: Search },
  { path: '/qa', label: '智能问答', icon: QuestionFilled },
  { path: '/chat', label: '智能对话', icon: ChatDotRound },
  { path: '/writer', label: '智能写文档', icon: EditPen },
  { path: '/settings', label: '设置与隐私', icon: Setting },
  { path: '/users', label: '用户管理', icon: User, adminOnly: true }
]

const visibleMenus = computed(() =>
  menuItems.filter(m => !m.adminOnly || isAdmin.value)
)

const roleLabel = computed(() => {
  const map: Record<string, string> = { ADMIN: '管理员', USER: '普通用户' }
  return map[user.value?.role || ''] || user.value?.role
})

const pageTitle = computed(() => {
  if (route.path.startsWith('/kbs/') && route.params.id) return '知识库详情'
  const matched = [...visibleMenus.value].reverse().find(m => route.path.startsWith(m.path))
  return (route.meta.title as string) || matched?.label || 'Fast Knowledge 快速知识库'
})

onMounted(() => {
  configStore.ensureLoaded().catch(() => {})
  const paths = visibleMenus.value.map(m => m.path)
  prefetchAllViewsOnIdle(paths)
})

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout { height: 100vh; }

.aside {
  background: #fff;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 16px;
  border-bottom: 1px solid #e4e7ed;
}

.logo-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: #409eff;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.logo-text {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  line-height: 1.3;
}

.side-menu {
  border-right: none;
  flex: 1;
}

.side-menu :deep(.el-menu-item.is-active) {
  background-color: #ecf5ff;
  border-right: 3px solid #409eff;
}

.side-menu :deep(.el-menu-item:hover) {
  background-color: #f5f7fa;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  height: 56px;
}

.header-title { font-size: 16px; font-weight: 600; color: #303133; }
.header-right { display: flex; align-items: center; gap: 10px; }
.main-content { background: #f5f7fa; padding: 0; overflow: auto; }

.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity 0.15s ease;
}
.page-fade-enter-from,
.page-fade-leave-to {
  opacity: 0;
}
</style>
