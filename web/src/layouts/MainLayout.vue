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
        :background-color="menuBg"
        :text-color="menuText"
        :active-text-color="menuActive"
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
      <div v-if="!isAdmin" class="aside-note">
        <el-icon><Lock /></el-icon>
        <div>
          <p>注：管理员专属菜单已隐藏</p>
          <p class="aside-note__sub">当前为普通用户视图</p>
        </div>
      </div>
    </el-aside>
    <el-container class="main-shell">
      <el-header class="header">
        <span class="header-title">{{ pageTitle }}</span>
        <div class="header-right">
          <el-tooltip :content="themeTooltip" placement="bottom">
            <el-button circle class="theme-btn" @click="themeStore.toggle()">
              <el-icon><component :is="themeIcon" /></el-icon>
            </el-button>
          </el-tooltip>
          <span v-if="user" class="header-user">{{ user.displayName || user.username }}</span>
          <el-tag v-if="user?.role" size="small" effect="plain">{{ roleLabel }}</el-tag>
          <el-button type="primary" link @click="pwdDialog?.open()">改密</el-button>
          <span v-if="!isAdmin" class="header-sep">|</span>
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
import { useThemeStore } from '@/stores/theme'
import { prefetchAllViewsOnIdle, prefetchView } from '@/router/prefetch'
import { ChangePasswordDialog } from '@/components/async'
import {
  Odometer,
  Collection,
  Search,
  QuestionFilled,
  ChatDotRound,
  EditPen,
  User,
  Lock,
  Moon,
  Sunny,
  Cpu,
  Document,
  Key
} from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const configStore = useConfigStore()
const themeStore = useThemeStore()
const { user, isAdmin } = storeToRefs(authStore)
const { instanceName } = storeToRefs(configStore)
const { resolvedTheme } = storeToRefs(themeStore)
const pwdDialog = ref<{ open: () => void }>()

const menuBg = 'var(--fk-sidebar-bg)'
const menuText = 'var(--fk-text-regular)'
const menuActive = 'var(--fk-primary)'

const menuItems = [
  { path: '/dashboard', label: '概览', icon: Odometer },
  { path: '/kbs', label: '知识库', icon: Collection },
  { path: '/search', label: '智能检索', icon: Search },
  { path: '/qa', label: '智能问答', icon: QuestionFilled },
  { path: '/chat', label: '智能对话', icon: ChatDotRound },
  { path: '/writer', label: '智能写文档', icon: EditPen },
  { path: '/settings/llm', label: '大模型配置', icon: Cpu, adminOnly: true },
  { path: '/users', label: '用户管理', icon: User, adminOnly: true },
  { path: '/api-keys', label: 'API Key', icon: Key, adminOnly: true },
  { path: '/audits', label: '审计日志', icon: Document, adminOnly: true }
]

const visibleMenus = computed(() => menuItems.filter(m => !m.adminOnly || isAdmin.value))

const roleLabel = computed(() => {
  const map: Record<string, string> = { ADMIN: '管理员', USER: '普通用户' }
  return map[user.value?.role || ''] || user.value?.role
})

const themeIcon = computed(() => (resolvedTheme.value === 'dark' ? Sunny : Moon))
const themeTooltip = computed(() =>
  resolvedTheme.value === 'dark' ? '切换为浅色主题' : '切换为暗色主题'
)

const pageTitle = computed(() => {
  if (route.path.startsWith('/kbs/') && route.params.id) return '知识库详情'
  const matched = [...visibleMenus.value].reverse().find(m => route.path.startsWith(m.path))
  return (route.meta.title as string) || matched?.label || 'Fast Knowledge 快速知识库'
})

onMounted(() => {
  configStore.ensureLoaded().catch(() => {})
  prefetchAllViewsOnIdle(visibleMenus.value.map(m => m.path))
})

async function handleLogout() {
  await ElMessageBox.confirm('确认退出当前账号？', '退出登录', {
    confirmButtonText: '退出',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await authStore.logout()
  router.push('/login')
}
</script>

<style scoped lang="scss">
.layout {
  height: 100vh;
  background: $fk-page-bg;
}

.aside {
  background: $fk-sidebar-bg;
  border-right: 1px solid $fk-border;
  display: flex;
  flex-direction: column;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 16px;
  border-bottom: 1px solid $fk-border;
}

.logo-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: $fk-primary;
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
  color: $fk-text-primary;
  line-height: 1.3;
}

.side-menu {
  border-right: none;
  flex: 1;
}

.side-menu :deep(.el-menu-item.is-active) {
  background-color: var(--fk-menu-active-bg);
  border-right: 3px solid $fk-primary;
}

.side-menu :deep(.el-menu-item:hover) {
  background-color: var(--fk-menu-hover-bg);
}

.main-shell {
  min-width: 0;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: $fk-header-bg;
  border-bottom: 1px solid $fk-border;
  height: 56px;
  padding: 0 20px;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
  color: $fk-text-primary;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-user {
  font-size: 14px;
  color: $fk-text-primary;
}

.header-sep {
  color: $fk-border;
  font-size: 12px;
}

.theme-btn {
  border-color: $fk-border;
  background: $fk-surface-muted;
  color: $fk-text-regular;
}

.main-content {
  background: $fk-page-bg;
  padding: 0;
  overflow: auto;
}

.aside-note {
  margin: 12px;
  padding: 12px;
  border: 1px dashed var(--fk-aside-note-border);
  border-radius: 8px;
  background: var(--fk-aside-note-bg);
  display: flex;
  gap: 8px;
  align-items: flex-start;
  font-size: 12px;
  color: $fk-text-regular;
  line-height: 1.5;
}

.aside-note p {
  margin: 0;
}

.aside-note__sub {
  color: $fk-text-secondary;
  margin-top: 4px !important;
}

.aside-note .el-icon {
  color: $fk-primary;
  margin-top: 2px;
}

.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity 0.15s ease;
}

.page-fade-enter-from,
.page-fade-leave-to {
  opacity: 0;
}
</style>
