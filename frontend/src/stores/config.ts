import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getSystemConfig, listLlmProviders, type LlmProviderPreset, type SystemConfig } from '@/api/config'

export const useConfigStore = defineStore('config', () => {
  const config = ref<SystemConfig | null>(null)
  const llmProviders = ref<LlmProviderPreset[]>([])
  const loaded = ref(false)
  const loading = ref(false)

  const instanceName = computed(() => config.value?.instanceName || 'Fast Knowledge')
  const setupComplete = computed(() => !!config.value?.setupComplete)
  const needsSetup = computed(() => loaded.value && !setupComplete.value)

  async function fetchConfig() {
    loading.value = true
    try {
      const res = await getSystemConfig()
      config.value = res.data
      loaded.value = true
      return res.data
    } finally {
      loading.value = false
    }
  }

  async function fetchLlmProviders() {
    const res = await listLlmProviders()
    llmProviders.value = res.data || []
    return llmProviders.value
  }

  async function ensureLoaded() {
    if (!loaded.value) {
      await fetchConfig()
    }
    return config.value
  }

  function invalidate() {
    loaded.value = false
    config.value = null
  }

  return {
    config,
    llmProviders,
    loaded,
    loading,
    instanceName,
    setupComplete,
    needsSetup,
    fetchConfig,
    fetchLlmProviders,
    ensureLoaded,
    invalidate
  }
})
