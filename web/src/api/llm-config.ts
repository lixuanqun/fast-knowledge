import request from '@/utils/request'

export interface LlmConfig {
  provider: string
  providerName: string
  baseUrl: string
  model: string
  allowExternal: boolean
  configuredInDb: boolean
  apiKeyConfigured: boolean
  apiKeyMask: string
}

export interface LlmConfigUpdate {
  provider: string
  baseUrl: string
  apiKey?: string
  model: string
  allowExternal: boolean
}

export function getLlmConfig() {
  return request.get<any, { data: LlmConfig }>('/system/llm-config')
}

export function updateLlmConfig(payload: LlmConfigUpdate) {
  return request.put<any, { data: LlmConfig }>('/system/llm-config', payload)
}

export function testLlmConfig(payload: LlmConfigUpdate) {
  return request.post<any, { data: { message: string } }>('/system/llm-config/test', payload)
}
