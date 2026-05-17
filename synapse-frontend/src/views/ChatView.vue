<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'
import { useChatStore } from '@/stores/chat'
import type { ChatMessage, ChunkReference } from '@/types'
import {
  Send,
  Square,
  Bot,
  User,
  BookOpen,
  ChevronDown,
  Plus,
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const kbStore = useKnowledgeBaseStore()
const chatStore = useChatStore()

const kbId = computed(() => (route.params.id as string) || kbStore.currentId)
const kb = computed(() => kbStore.list.find((k) => k.id === kbId.value))

const inputText = ref('')
const messagesContainer = ref<HTMLDivElement | null>(null)
const showKbSelector = ref(false)

onMounted(() => {
  if (kbStore.list.length === 0) {
    kbStore.fetchList().then(() => {
      initChat()
    }).catch(() => {})
  } else {
    initChat()
  }
})

function initChat() {
  if (kb.value) {
    chatStore.loadCurrentSession(kb.value.id, kb.value.name).then(scrollToBottom)
  } else {
    chatStore.clearHistory()
    chatStore.initWelcome()
  }
}

watch(() => kbId.value, () => {
  initChat()
})

watch(() => chatStore.messages.length, () => {
  scrollToBottom()
})

function scrollToBottom() {
  nextTick(() => {
    messagesContainer.value?.scrollTo({
      top: messagesContainer.value.scrollHeight,
      behavior: 'smooth'
    })
  })
}

function handleSend() {
  const text = inputText.value.trim()
  if (!text || chatStore.loading) return

  if (!kbId.value) {
    chatStore.error = '请先选择一个知识库'
    return
  }

  inputText.value = ''
  chatStore.sendQuestionStream(kbId.value, text)
  scrollToBottom()
}

function handleStop() {
  chatStore.stopGeneration()
}

function handleNewChat() {
  if (!kb.value || chatStore.loading) return
  chatStore.startNewSession(kb.value.id, kb.value.name).then(scrollToBottom)
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    handleSend()
  }
}

function selectKb(kb: typeof kbStore.list[0]) {
  kbStore.setCurrent(kb.id)
  showKbSelector.value = false
  router.push(`/knowledge-bases/${kb.id}/chat`)
}

function referenceNumber(ref: ChunkReference, index: number): string {
  return String(ref.sourceId || index + 1)
}

function visibleReferences(msg: ChatMessage): ChunkReference[] {
  const references = msg.references || []
  const usedReferences = references.filter((ref) => ref.used)
  return usedReferences.length > 0 ? usedReferences : references
}

function hiddenCandidateCount(msg: ChatMessage): number {
  const references = msg.references || []
  const visibleSourceIds = new Set(visibleReferences(msg).map((ref) => ref.sourceId))
  return references.filter((ref) => !visibleSourceIds.has(ref.sourceId)).length
}

function validationWarning(msg: ChatMessage): string {
  if (!msg.validation || msg.validation.trusted) {
    return ''
  }
  return msg.validation.warnings.length > 0
    ? msg.validation.warnings.join('；')
    : '引用校验未通过，答案可能包含未由知识库支持的内容'
}
</script>

<template>
  <div class="chat-page">
    <!-- Header -->
    <div class="chat-header">
      <div class="kb-selector" @click="showKbSelector = !showKbSelector">
        <BookOpen :size="16" />
        <span class="kb-name">{{ kb?.name || '选择知识库' }}</span>
        <ChevronDown :size="14" class="chevron" :class="{ open: showKbSelector }" />

        <!-- Dropdown -->
        <Transition name="dropdown">
          <div v-if="showKbSelector" class="kb-dropdown">
            <div
              v-for="item in kbStore.list"
              :key="item.id"
              class="kb-dropdown-item"
              :class="{ active: item.id === kbId }"
              @click.stop="selectKb(item)"
            >
              <span class="kb-dropdown-name">{{ item.name }}</span>
              <span v-if="item.id === kbId" class="kb-dropdown-check">✓</span>
            </div>
            <div v-if="kbStore.list.length === 0" class="kb-dropdown-empty">
              暂无知识库
            </div>
          </div>
        </Transition>
      </div>
      <div v-if="kb" class="header-actions">
        <button
          class="new-chat-btn"
          :disabled="chatStore.loading"
          title="新对话"
          @click="handleNewChat"
        >
          <Plus :size="14" />
          <span>新对话</span>
        </button>
        <div class="model-info">
          qwen2.5:7b
        </div>
      </div>
    </div>

    <!-- Messages -->
    <div ref="messagesContainer" class="messages">
      <div
        v-for="msg in chatStore.messages"
        :key="msg.id"
        class="message"
        :class="msg.role"
      >
        <div class="message-inner">
          <div class="message-avatar">
            <Bot v-if="msg.role === 'assistant'" :size="16" />
            <User v-else :size="16" />
          </div>
          <div class="message-content">
            <div class="message-text">{{ msg.content }}</div>

            <div v-if="validationWarning(msg)" class="validation-warning">
              {{ validationWarning(msg) }}
            </div>

            <!-- Thinking indicator: shown when assistant message is empty (before first token arrives) -->
            <div v-if="msg.role === 'assistant' && !msg.content" class="thinking-hint">
              <span class="thinking-dot" />
              <span class="thinking-dot" />
              <span class="thinking-dot" />
              <span class="thinking-label">正在思考</span>
            </div>

            <!-- References -->
            <div v-if="msg.references && msg.references.length > 0" class="references">
              <div v-if="hiddenCandidateCount(msg) > 0" class="reference-note">
                已隐藏 {{ hiddenCandidateCount(msg) }} 个未被答案引用的检索候选
              </div>
              <div
                v-for="(ref, idx) in visibleReferences(msg)"
                :key="`${ref.sourceId}-${ref.documentId}-${ref.startPosition}`"
                class="reference-card"
                :class="{ used: ref.used, candidate: ref.used === false }"
              >
                <div class="reference-header">
                  <span class="reference-num">{{ referenceNumber(ref, idx) }}</span>
                  <span class="reference-doc">{{ ref.documentName }}</span>
                  <span v-if="ref.used" class="reference-used">已引用</span>
                  <span class="reference-score">相关度 {{ ref.score.toFixed(2) }}</span>
                </div>
                <div class="reference-text">{{ ref.chunkText }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Loading indicator -->
      <div v-if="chatStore.loading && !chatStore.streaming" class="message assistant">
        <div class="message-inner">
          <div class="message-avatar">
            <Bot :size="16" />
          </div>
          <div class="message-content">
            <div class="typing-indicator">
              <span />
              <span />
              <span />
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Input -->
    <div class="input-area">
      <div v-if="chatStore.error" class="input-error">
        {{ chatStore.error }}
      </div>
      <div class="input-wrap">
        <textarea
          v-model="inputText"
          rows="1"
          placeholder="询问关于知识库的内容..."
          @keydown="handleKeydown"
          @input="(e) => {
            const target = e.target as HTMLTextAreaElement
            target.style.height = 'auto'
            target.style.height = target.scrollHeight + 'px'
          }"
        />
        <button
          v-if="chatStore.streaming"
          class="send-btn stop-btn"
          @click="handleStop"
        >
          <Square :size="16" />
        </button>
        <button
          v-else
          class="send-btn"
          :disabled="!inputText.trim() || chatStore.loading || !kbId"
          @click="handleSend"
        >
          <Send :size="16" />
        </button>
      </div>
      <div class="input-hint">
        Synapse 生成的内容可能不准确，请验证重要信息。
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  max-width: 900px;
  margin: 0 auto;
  padding: 0 20px;
}

/* Header */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 0;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.kb-selector {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  background: var(--bg-base);
  user-select: none;
  transition: border-color 0.15s;
}

.kb-selector:hover {
  border-color: var(--border-strong);
}

.kb-name {
  font-weight: 500;
  color: var(--text-primary);
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chevron {
  transition: transform 0.2s;
  color: var(--text-muted);
}

.chevron.open {
  transform: rotate(180deg);
}

.model-info {
  font-size: 12px;
  color: var(--text-muted);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.new-chat-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 30px;
  padding: 0 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-base);
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s;
}

.new-chat-btn:hover:not(:disabled) {
  border-color: var(--border-strong);
  color: var(--text-primary);
}

.new-chat-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Dropdown */
.kb-dropdown {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  min-width: 240px;
  background: var(--bg-base);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
  z-index: 200;
  padding: 4px;
  max-height: 300px;
  overflow-y: auto;
}

.kb-dropdown-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 13px;
  transition: background 0.15s;
}

.kb-dropdown-item:hover,
.kb-dropdown-item.active {
  background: var(--bg-hover);
}

.kb-dropdown-name {
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-dropdown-check {
  color: var(--accent);
  font-weight: 600;
  font-size: 12px;
}

.kb-dropdown-empty {
  padding: 12px;
  text-align: center;
  font-size: 13px;
  color: var(--text-muted);
}

.dropdown-enter-active,
.dropdown-leave-active {
  transition: opacity 0.15s, transform 0.15s;
}

.dropdown-enter-from,
.dropdown-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* Messages */
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px 0;
  display: flex;
  flex-direction: column;
  gap: 0;
}

.message {
  padding: 16px 0;
}

.message-inner {
  display: flex;
  gap: 12px;
  max-width: 100%;
}

.message-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.message.assistant .message-avatar {
  background: var(--text-primary);
  color: #fff;
}

.message.user .message-avatar {
  background: var(--accent-subtle);
  color: var(--accent);
}

.message-content {
  flex: 1;
  padding-top: 2px;
}

.message-text {
  font-size: 14.5px;
  line-height: 1.7;
  color: var(--text-primary);
  white-space: pre-wrap;
}

.message.user .message-text {
  background: var(--bg-subtle);
  padding: 10px 14px;
  border-radius: var(--radius-md);
  display: inline-block;
}

.validation-warning {
  margin-top: 10px;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  background: #fff7ed;
  color: #9a3412;
  font-size: 12px;
  line-height: 1.5;
  border: 1px solid #fed7aa;
}

/* References */
.references {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.reference-note {
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.5;
}

.reference-card {
  padding: 12px 16px;
  background: var(--bg-subtle);
  border-radius: var(--radius-sm);
  border-left: 3px solid var(--accent);
}

.reference-card.candidate {
  border-left-color: var(--border-color);
  opacity: 0.74;
}

.reference-card.used {
  background: #f8fafc;
}

.reference-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.reference-num {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--accent-subtle);
  color: var(--accent);
  font-size: 10px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.reference-doc {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-primary);
}

.reference-used {
  font-size: 11px;
  color: var(--accent);
  background: var(--accent-subtle);
  border-radius: 999px;
  padding: 2px 6px;
  flex-shrink: 0;
}

.reference-score {
  font-size: 11px;
  color: var(--text-muted);
  margin-left: auto;
}

.reference-text {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
}

/* Typing indicator */
.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 8px 0;
}

.typing-indicator span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-muted);
  animation: typing 1.4s infinite ease-in-out both;
}

.typing-indicator span:nth-child(1) {
  animation-delay: 0s;
}

.typing-indicator span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-indicator span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 80%, 100% {
    transform: scale(0.6);
    opacity: 0.4;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

/* Input area */
.input-area {
  flex-shrink: 0;
  padding: 16px 0 24px;
  border-top: 1px solid var(--border);
}

.input-error {
  padding: 8px 12px;
  margin-bottom: 8px;
  background: var(--danger-subtle);
  color: var(--danger);
  border-radius: var(--radius-sm);
  font-size: 13px;
}

.input-wrap {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-lg);
  padding: 10px 14px;
  background: var(--bg-base);
  transition: border-color 0.15s, box-shadow 0.15s;
}

.input-wrap:focus-within {
  border-color: var(--text-primary);
  box-shadow: 0 0 0 3px rgba(0, 0, 0, 0.04);
}

.input-wrap textarea {
  flex: 1;
  border: none;
  outline: none;
  resize: none;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.5;
  min-height: 24px;
  max-height: 200px;
  background: transparent;
  color: var(--text-primary);
}

.input-wrap textarea::placeholder {
  color: var(--text-muted);
}

.send-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--text-primary);
  color: #fff;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: opacity 0.15s;
}

.send-btn:hover:not(:disabled) {
  opacity: 0.8;
}

.send-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

/* Thinking hint: shown inside assistant message before first token arrives */
.thinking-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
}

.thinking-label {
  font-size: 13px;
  color: var(--text-muted);
}

.thinking-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--text-muted);
  animation: thinking-pulse 1.4s infinite ease-in-out both;
}

.thinking-dot:nth-child(1) {
  animation-delay: 0s;
}

.thinking-dot:nth-child(2) {
  animation-delay: 0.2s;
}

.thinking-dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes thinking-pulse {
  0%, 80%, 100% {
    transform: scale(0.6);
    opacity: 0.4;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

.input-hint {
  text-align: center;
  margin-top: 8px;
  font-size: 11px;
  color: var(--text-muted);
}

/* Scrollbar for messages */
.messages::-webkit-scrollbar {
  width: 4px;
}

.messages::-webkit-scrollbar-track {
  background: transparent;
}

.messages::-webkit-scrollbar-thumb {
  background: var(--border);
  border-radius: 2px;
}
</style>
