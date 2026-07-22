import request from '@/utils/request'

export interface ScenarioTemplate {
  id: string
  name: string
  category: string
  description: string
  suggestedKbName?: string
  recommendedChunk?: { size?: number; overlap?: number }
  sampleDocTypes?: string[]
  docTypeLabels?: Record<string, string>
  checklist?: string[]
}

export function listScenarios() {
  return request.get<any, { data: ScenarioTemplate[] }>('/scenarios')
}

export function getScenario(id: string) {
  return request.get<any, { data: ScenarioTemplate }>(`/scenarios/${id}`)
}
