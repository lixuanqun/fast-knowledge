import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

export type ThemeMode = 'light' | 'dark' | 'system'

const STORAGE_KEY = 'fk-theme'

function getSystemTheme(): 'light' | 'dark' {
  if (typeof window === 'undefined') return 'light'
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

function readStoredMode(): ThemeMode {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored === 'light' || stored === 'dark' || stored === 'system') return stored
  return 'system'
}

export function applyResolvedTheme(theme: 'light' | 'dark') {
  const root = document.documentElement
  root.setAttribute('data-theme', theme)
  root.classList.toggle('dark', theme === 'dark')
}

export function initTheme() {
  const mode = readStoredMode()
  const resolved = mode === 'system' ? getSystemTheme() : mode
  applyResolvedTheme(resolved)
  return mode
}

export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>(readStoredMode())

  const resolvedTheme = computed<'light' | 'dark'>(() =>
    mode.value === 'system' ? getSystemTheme() : mode.value
  )

  function setMode(next: ThemeMode) {
    mode.value = next
    localStorage.setItem(STORAGE_KEY, next)
    applyResolvedTheme(resolvedTheme.value)
  }

  function toggle() {
    setMode(resolvedTheme.value === 'dark' ? 'light' : 'dark')
  }

  function syncFromSystem() {
    if (mode.value === 'system') {
      applyResolvedTheme(getSystemTheme())
    }
  }

  if (typeof window !== 'undefined') {
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', syncFromSystem)
  }

  return { mode, resolvedTheme, setMode, toggle, syncFromSystem }
})
