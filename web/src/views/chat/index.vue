<template>
  <div class="page-container chat-page">
    <PageHeader title="智能对话" subtitle="多轮 RAG 对话，支持历史会话与引用来源" />

    <div class="chat-layout">
      <aside v-loading="sessionsLoading" class="session-panel">
        <div class="panel-header">
          <span>历史会话</span>
          <el-button link type="primary" @click="newSession">
            <el-icon><Plus /></el-icon>
            新对话
          </el-button>
        </div>
        <EmptyState v-if="!sessions.length && !sessionsLoading" variant="chat-sessions" />
        <div
          v-for="s in sessions"
          :key="s.id"
          :class="['session-item', { active: sessionId === s.id }]"
          @click="selectSession(s)"
        >
          <div class="session-info">
            <div class="session-title">{{ s.title || '新对话' }}</div>
            <div class="session-time">{{ formatDateTime(s.updatedAt) }}</div>
          </div>
          <el-button link type="danger" size="small" @click.stop="removeSession(s.id)">删</el-button>
        </div>
      </aside>

      <main class="chat-main">
        <div class="chat-toolbar">
          <span class="toolbar-label">知识库</span>
          <KbSelect v-model="kbId" width="220px" :auto-default="false" clearable placeholder="可选" />
        </div>

        <div ref="messagesRef" v-loading="messagesLoading" class="messages">
          <EmptyState
            v-if="!messages.length && !streaming && !messagesLoading"
            :variant="sessionId ? 'chat-empty' : 'chat'"
          />
          <div v-for="(m, i) in messages" :key="i" :class="['msg-bubble', m.role]">
            <div class="msg-bubble__avatar">{{ m.role === 'user' ? '我' : 'AI' }}</div>
            <div class="msg-bubble__body">
              <MarkdownBody v-if="m.role === 'assistant'" :content="m.content" />
              <div v-else class="msg-text">{{ m.content }}</div>
              <SourceList :sources="m.sources" />
            </div>
          </div>
          <div v-if="streaming" class="msg-bubble assistant">
            <div class="msg-bubble__avatar">AI</div>
            <div class="msg-bubble__body is-streaming">
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
            :rows="3"
            placeholder="输入消息，Ctrl+Enter 发送"
            :disabled="streaming"
            @keydown.ctrl.enter="send"
          />
          <el-button type="primary" :loading="streaming" :disabled="!input.trim()" @click="send">
            发送
          </el-button>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { streamChat, type StreamDoneMeta } from '@/api'
import PageHeader from '@/components/PageHeader.vue'
import KbSelect from '@/components/KbSelect.vue'
import { MarkdownBody } from '@/components/async'
import SourceList from '@/components/SourceList.vue'
import EmptyState from '@/components/EmptyState.vue'
import { formatDateTime } from '@/utils/format'
import {
  type ChatMessage,
  type ChatSession,
  useChatMessagesQuery,
  useChatSessionsQuery,
  useDeleteChatSessionMutation
} from '@/composables/queries/useChat'
import { queryKeys } from '@/lib/query-keys'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

const queryClient = useQueryClient()
const { data: sessionsData, isLoading: sessionsLoading } = useChatSessionsQuery()
const deleteSessionMutation = useDeleteChatSessionMutation()

const sessions = computed(() => sessionsData.value || [])
const kbId = ref<number>()
const sessionId = ref<number>()
const messages = ref<ChatMessage[]>([])
const input = ref('')
const streaming = ref(false)
const streamText = ref('')
const pendingSources = ref<ChatMessage['sources']>([])
const messagesRef = ref<HTMLElement>()
const skipMessagesSync = ref(false)

const { data: sessionMessages, isLoading: messagesLoading } = useChatMessagesQuery(sessionId)

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

async function removeSession(id: number) {
  await ElMessageBox.confirm('确认删除该会话？', '提示', { type: 'warning' })
  try {
    await deleteSessionMutation.mutateAsync(id)
    if (sessionId.value === id) newSession()
    ElMessage.success('已删除')
  } catch {
    /* 错误已由 axios 拦截器提示 */
  }
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
  height: calc(100vh - 88px);
}

.chat-layout {
  display: flex;
  gap: 16px;
  flex: 1;
  min-height: 0;
}

.session-panel {
  width: 260px;
  background: #fff;
  border: 1px solid $fk-border;
  border-radius: 10px;
  padding: 12px;
  overflow-y: auto;
  flex-shrink: 0;
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
  justify-content: space-between;
  align-items: center;
  gap: 4px;
  border: 1px solid transparent;
}

.session-item:hover,
.session-item.active {
  background: $fk-primary-light;
  border-color: #b3d8ff;
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
}

.chat-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding: 10px 14px;
  background: #fff;
  border: 1px solid $fk-border;
  border-radius: 10px;
}

.toolbar-label {
  font-size: 13px;
  color: $fk-text-regular;
}

.messages {
  flex: 1;
  overflow-y: auto;
  background: #fff;
  padding: 16px;
  border: 1px solid $fk-border;
  border-radius: 10px;
  margin-bottom: 12px;
}

.msg-bubble {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.msg-bubble.user {
  flex-direction: row-reverse;
}

.msg-bubble__avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: $fk-primary;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  flex-shrink: 0;
}

.msg-bubble__body {
  max-width: 75%;
  background: #f6f7f8;
  padding: 12px 14px;
  border-radius: 10px;
}

.msg-bubble.user .msg-bubble__body {
  background: $fk-primary-light;
}

.msg-bubble__body.is-streaming {
  border: 1px dashed #b3d8ff;
}

.msg-text {
  white-space: pre-wrap;
  line-height: 1.7;
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
  padding: 12px;
  background: #fff;
  border: 1px solid $fk-border;
  border-radius: 10px;
}

.chat-input .el-textarea {
  flex: 1;
}
</style>
