<template>
  <div class="page-container chat-page">
    <PageHeader title="智能对话" subtitle="多轮 RAG 对话，支持历史会话与引用来源">
      <template #actions>
        <span class="kb-label">知识库：</span>
        <KbSelect
          v-model="kbId"
          width="200px"
          :auto-default="false"
          clearable
          placeholder="产品知识库"
        />
      </template>
    </PageHeader>

    <div class="chat-layout">
      <aside v-loading="sessionsLoading" class="session-panel">
        <div class="panel-header">
          <span>历史会话</span>
          <el-button link type="primary" @click="newSession">+ 新对话</el-button>
        </div>
        <EmptyState v-if="!sessions.length && !sessionsLoading" variant="chat-sessions" />
        <div
          v-for="s in sessions"
          :key="s.id"
          :class="['session-item', { active: sessionId === s.id }]"
          @click="selectSession(s)"
        >
          <el-icon class="session-icon"><ChatDotRound /></el-icon>
          <div class="session-info">
            <div class="session-title">{{ s.title || '新对话' }}</div>
            <div class="session-time">{{ formatDateTime(s.updatedAt) }}</div>
          </div>
        </div>
      </aside>

      <main class="chat-main">
        <div ref="messagesRef" v-loading="messagesLoading" class="messages">
          <EmptyState
            v-if="!messages.length && !streaming && !messagesLoading"
            :variant="sessionId ? 'chat-empty' : 'chat'"
          />
          <div v-if="messages.length || streaming" class="date-sep">{{ dateSepLabel }}</div>
          <div v-for="(m, i) in messages" :key="i" :class="['msg-row', m.role]">
            <div v-if="m.role === 'assistant'" class="msg-avatar msg-avatar--bot">
              <el-icon><Cpu /></el-icon>
            </div>
            <div :class="['msg-bubble', m.role]">
              <MarkdownBody v-if="m.role === 'assistant'" :content="m.content" />
              <div v-else class="msg-text">{{ m.content }}</div>
              <SourceList
                :sources="m.sources"
                :kb-id="kbId"
                @open="openSourcePreview"
              />
              <div v-if="m.role === 'user'" class="msg-time">{{ msgTime }}</div>
            </div>
            <div v-if="m.role === 'user'" class="msg-avatar msg-avatar--user">
              <el-icon><User /></el-icon>
            </div>
          </div>
          <div v-if="streaming" class="msg-row assistant">
            <div class="msg-avatar msg-avatar--bot">
              <el-icon><Cpu /></el-icon>
            </div>
            <div class="msg-bubble assistant is-streaming">
              <MarkdownBody v-if="streamText" :content="streamText" />
              <div v-else class="streaming-hint">
                <span class="streaming-dots"><i /><i /><i /></span>
                思考中...
              </div>
            </div>
          </div>
        </div>

        <div class="chat-input">
          <el-input
            v-model="input"
            type="textarea"
            :rows="2"
            placeholder="输入消息，Enter 发送"
            :disabled="streaming"
            @keydown.enter.exact.prevent="send"
          />
          <el-button type="primary" :loading="streaming" :disabled="!input.trim()" @click="send">
            <el-icon class="btn-icon"><Promotion /></el-icon>
            发送
          </el-button>
        </div>
      </main>
    </div>

    <DocumentPreviewDrawer
      v-model:visible="previewVisible"
      :kb-id="kbId!"
      :doc-id="previewDocId"
      :highlight-chunk-id="previewChunkId"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { streamChat, type StreamDoneMeta } from '@/api'
import PageHeader from '@/components/PageHeader.vue'
import KbSelect from '@/components/KbSelect.vue'
import { MarkdownBody, DocumentPreviewDrawer } from '@/components/async'
import SourceList from '@/components/SourceList.vue'
import EmptyState from '@/components/EmptyState.vue'
import { formatDateTime } from '@/utils/format'
import {
  type ChatMessage,
  type ChatSession,
  useChatMessagesQuery,
  useChatSessionsQuery
} from '@/composables/queries/useChat'
import { queryKeys } from '@/lib/query-keys'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Cpu, Promotion, User } from '@element-plus/icons-vue'

const queryClient = useQueryClient()
const { data: sessionsData, isLoading: sessionsLoading } = useChatSessionsQuery()

const sessions = computed(() => sessionsData.value || [])
const kbId = ref<number>()
const sessionId = ref<number>()
const previewVisible = ref(false)
const previewDocId = ref<number>()
const previewChunkId = ref<number>()
const messages = ref<ChatMessage[]>([])
const input = ref('')
const streaming = ref(false)
const streamText = ref('')
const pendingSources = ref<ChatMessage['sources']>([])
const messagesRef = ref<HTMLElement>()
const skipMessagesSync = ref(false)

const { data: sessionMessages, isLoading: messagesLoading } = useChatMessagesQuery(sessionId)

const dateSepLabel = computed(() => {
  const now = new Date()
  return `今天 ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
})
const msgTime = computed(() => dateSepLabel.value.replace('今天 ', ''))

watch(sessionMessages, data => {
  if (skipMessagesSync.value || streaming.value) return
  if (data) messages.value = [...data]
})

function scrollToBottom() {
  nextTick(() => {
    const el = messagesRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function newSession() {
  skipMessagesSync.value = true
  sessionId.value = undefined
  messages.value = []
  nextTick(() => {
    skipMessagesSync.value = false
  })
}

function selectSession(session: ChatSession) {
  skipMessagesSync.value = false
  sessionId.value = session.id
  if (session.kbId) kbId.value = session.kbId
}

function openSourcePreview(payload: { kbId: number; documentId: number; chunkId?: number }) {
  if (!kbId.value) kbId.value = payload.kbId
  previewDocId.value = payload.documentId
  previewChunkId.value = payload.chunkId
  previewVisible.value = true
}

async function send() {
  if (!input.value.trim() || streaming.value) return

  const text = input.value
  skipMessagesSync.value = true
  messages.value.push({ role: 'user', content: text })
  input.value = ''
  streaming.value = true
  streamText.value = ''
  pendingSources.value = []
  scrollToBottom()

  try {
    await streamChat(
      { sessionId: sessionId.value, kbId: kbId.value, message: text },
      chunk => {
        streamText.value += chunk
        scrollToBottom()
      },
      (meta?: StreamDoneMeta) => {
        if (meta?.sessionId) sessionId.value = meta.sessionId
        pendingSources.value = meta?.sources
      }
    )
    messages.value.push({
      role: 'assistant',
      content: streamText.value,
      sources: pendingSources.value
    })
    await queryClient.invalidateQueries({ queryKey: queryKeys.chat.sessions })
  } catch (e: unknown) {
    const message = e instanceof Error ? e.message : '对话失败'
    ElMessage.error(message)
    messages.value.pop()
  } finally {
    streaming.value = false
    streamText.value = ''
    skipMessagesSync.value = false
    scrollToBottom()
  }
}
</script>

<style scoped lang="scss">
.chat-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 56px);
}

.kb-label {
  font-size: 13px;
  color: $fk-text-regular;
}

.chat-layout {
  display: flex;
  gap: 0;
  flex: 1;
  min-height: 0;
  border: 1px solid $fk-border;
  border-radius: 10px;
  overflow: hidden;
  background: $fk-card-bg;
}

.session-panel {
  width: 260px;
  border-right: 1px solid $fk-border;
  padding: 12px;
  overflow-y: auto;
  flex-shrink: 0;
  background: $fk-card-bg;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-weight: 600;
  color: $fk-text-primary;
}

.session-item {
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 6px;
  display: flex;
  gap: 10px;
  align-items: flex-start;
  border: 1px solid transparent;
}

.session-item:hover,
.session-item.active {
  background: $fk-primary-light;
  border-color: var(--fk-aside-note-border);
}

.session-icon {
  color: $fk-primary;
  margin-top: 2px;
  flex-shrink: 0;
}

.session-title {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: $fk-text-primary;
}

.session-time {
  font-size: 11px;
  color: $fk-text-secondary;
  margin-top: 4px;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: $fk-surface-muted;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
}

.date-sep {
  text-align: center;
  font-size: 12px;
  color: $fk-text-secondary;
  margin-bottom: 16px;
}

.msg-row {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  align-items: flex-start;
}

.msg-row.user {
  justify-content: flex-end;
}

.msg-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #fff;
}

.msg-avatar--user,
.msg-avatar--bot {
  background: $fk-primary;
}

.msg-bubble {
  max-width: 72%;
  padding: 12px 14px;
  border-radius: 10px;
}

.msg-bubble.user {
  background: var(--fk-chat-user-bg);
  color: var(--fk-chat-user-text);
  border-bottom-right-radius: 4px;
}

.msg-bubble.user .msg-text {
  color: var(--fk-chat-user-text);
}

.msg-bubble.assistant {
  background: $fk-card-bg;
  border: 1px solid $fk-border;
  border-bottom-left-radius: 4px;
}

.msg-bubble.assistant.is-streaming {
  border-style: dashed;
  border-color: var(--fk-aside-note-border);
}

.msg-text {
  white-space: pre-wrap;
  line-height: 1.7;
}

.msg-time {
  margin-top: 6px;
  font-size: 11px;
  opacity: 0.85;
  text-align: right;
}

.streaming-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  color: $fk-text-secondary;
  font-size: 14px;
}

.streaming-dots {
  display: inline-flex;
  gap: 4px;
}

.streaming-dots i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: $fk-primary;
  animation: dot-bounce 1.2s infinite ease-in-out;
}

.streaming-dots i:nth-child(2) {
  animation-delay: 0.15s;
}

.streaming-dots i:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes dot-bounce {
  0%,
  80%,
  100% {
    opacity: 0.3;
    transform: scale(0.8);
  }
  40% {
    opacity: 1;
    transform: scale(1);
  }
}

.chat-input {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  padding: 12px 16px;
  background: $fk-card-bg;
  border-top: 1px solid $fk-border;
}

.chat-input .el-textarea {
  flex: 1;
}

.btn-icon {
  margin-right: 4px;
}
</style>
