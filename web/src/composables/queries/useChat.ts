import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import type { MaybeRefOrGetter } from 'vue'
import { computed, toValue } from 'vue'
import { deleteChatSession, getChatMessages, listChatSessions } from '@/api'
import { queryKeys } from '@/lib/query-keys'

export interface ChatSession {
  id: number
  title?: string
  kbId?: number
  updatedAt?: string
}

export interface ChatMessageDto {
  role: string
  content: string
  sources?: string
}

import type { SearchHit } from '@/api/search'

export interface ChatMessage {
  role: string
  content: string
  sources?: SearchHit[]
}

export function mapChatMessages(data: ChatMessageDto[]): ChatMessage[] {
  return data.map(m => ({
    role: m.role,
    content: m.content,
    sources: m.sources ? JSON.parse(m.sources) : undefined
  }))
}

export function useChatSessionsQuery() {
  return useQuery({
    queryKey: queryKeys.chat.sessions,
    queryFn: async () => {
      const res = await listChatSessions()
      return (res.data || []) as ChatSession[]
    }
  })
}

export function useChatMessagesQuery(sessionId: MaybeRefOrGetter<number | undefined>) {
  return useQuery({
    queryKey: computed(() => queryKeys.chat.messages(toValue(sessionId) ?? 0)),
    queryFn: async () => {
      const res = await getChatMessages(toValue(sessionId)!)
      return mapChatMessages((res.data || []) as ChatMessageDto[])
    },
    enabled: computed(() => {
      const id = toValue(sessionId)
      return id != null && id > 0
    })
  })
}

export function useDeleteChatSessionMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (sessionId: number) => deleteChatSession(sessionId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.chat.sessions })
    }
  })
}
