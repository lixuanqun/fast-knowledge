import request from '@/utils/request'

export interface SystemConfig {
  instanceName: string
  setupComplete: boolean
  vectorProvider: string
  embeddingProvider: string
  llmProvider?: string
  llmProviderName?: string
  llmModel: string
  llmAllowExternal: boolean
}

export interface LlmProviderPreset {
  id: string
  name: string
  defaultBaseUrl: string
  defaultModel: string
  local: boolean
  docsHint?: string
}

export function getSystemConfig() {
  return request.get<any, { data: SystemConfig }>('/system/config')
}

export function listLlmProviders() {
  return request.get<any, { data: LlmProviderPreset[] }>('/system/llm-providers')
}
