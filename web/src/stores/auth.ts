import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getToken, setToken, removeToken, getUser, setUser, removeUser } from '@/utils/auth'
import type { LoginResult } from '@/api/auth'
import { login as apiLogin, logout as apiLogout } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(getToken())
  const user = ref<LoginResult | null>(getUser<LoginResult>())

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const mustChangePassword = computed(() => !!user.value?.mustChangePassword)

  function clearSession() {
    token.value = null
    user.value = null
    removeToken()
    removeUser()
  }

  async function login(username: string, password: string) {
    const res = await apiLogin(username, password)
    token.value = res.data.token
    user.value = res.data
    setToken(res.data.token)
    setUser(res.data)
    return res.data
  }

  async function logout() {
    try {
      await apiLogout()
    } finally {
      clearSession()
    }
  }

  function clearMustChangePassword() {
    if (user.value) {
      user.value = { ...user.value, mustChangePassword: false }
      setUser(user.value)
    }
  }

  function applySession(data: LoginResult) {
    token.value = data.token
    user.value = data
    setToken(data.token)
    setUser(data)
  }

  return {
    token,
    user,
    isLoggedIn,
    isAdmin,
    mustChangePassword,
    login,
    logout,
    clearSession,
    clearMustChangePassword,
    applySession
  }
})
