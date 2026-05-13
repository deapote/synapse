import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as api from '@/api/query'
import type { ChatMessage, ChunkReference } from '@/types'

function generateId(): string {
  return Math.random().toString(36).substring(2, 10)
}

export const useChatStore = defineStore('chat', () => {
  // State
  const messages = ref<ChatMessage[]>([])
  const loading = ref(false)
  const streaming = ref(false)
  const error = ref<string | null>(null)

  // 当前流式请求的 AbortController
  let currentAbortCtrl: AbortController | null = null

  // 当前 generation，用于丢弃过期回调（竞态防护）
  let currentGeneration = 0

  // 流式 token 缓冲与定时刷新（解决 Vue 批量更新导致第一次无打字机效果的问题）
  let tokenBuffer = ''
  let flushTimer: ReturnType<typeof setInterval> | null = null

  /**
   * 将缓冲区中的 token 批量刷新到最后一条 assistant 消息。
   */
  function flushTokens() {
    if (!tokenBuffer) return
    const lastMsg = messages.value[messages.value.length - 1]
    if (lastMsg?.role === 'assistant') {
      lastMsg.content += tokenBuffer
    }
    tokenBuffer = ''
  }

  /**
   * 启动 token 定时刷新器（每 40ms 刷新一次，确保打字机效果）。
   */
  function startFlushTimer() {
    if (flushTimer) return
    flushTimer = setInterval(() => {
      flushTokens()
    }, 40)
  }

  /**
   * 停止 token 定时刷新器并清空缓冲。
   */
  function stopFlushTimer() {
    if (flushTimer) {
      clearInterval(flushTimer)
      flushTimer = null
    }
    tokenBuffer = ''
  }

  // Actions

  /**
   * 发送问题（流式模式）。
   */
  function sendQuestionStream(knowledgeBaseId: string, query: string) {
    const generation = ++currentGeneration

    // 添加用户消息
    const userMessage: ChatMessage = {
      id: generateId(),
      role: 'user',
      content: query,
      createdAt: Date.now()
    }
    messages.value.push(userMessage)

    // 添加空的助手消息（待流式填充）
    const assistantMessage: ChatMessage = {
      id: generateId(),
      role: 'assistant',
      content: '',
      references: [],
      createdAt: Date.now()
    }
    messages.value.push(assistantMessage)

    streaming.value = true
    loading.value = true
    error.value = null
    stopFlushTimer()

    currentAbortCtrl = api.streamQueryKnowledgeBase(knowledgeBaseId, query, {
      onToken: (token: string) => {
        if (generation !== currentGeneration) return
        tokenBuffer += token
        startFlushTimer()
      },

      onReferences: (references: ChunkReference[]) => {
        if (generation !== currentGeneration) return
        flushTokens()
        const lastMsg = messages.value[messages.value.length - 1]
        if (lastMsg?.role === 'assistant') {
          lastMsg.references = references
        }
      },

      onComplete: () => {
        if (generation !== currentGeneration) return
        flushTokens()
        stopFlushTimer()
        streaming.value = false
        loading.value = false
        currentAbortCtrl = null
      },

      onError: (errMsg: string) => {
        if (generation !== currentGeneration) return
        flushTokens()
        stopFlushTimer()
        streaming.value = false
        loading.value = false
        error.value = errMsg

        const lastMsg = messages.value[messages.value.length - 1]
        if (lastMsg?.role === 'assistant' && !lastMsg.content) {
          lastMsg.content = `生成失败：${errMsg}`
        }
        currentAbortCtrl = null
      }
    })
  }

  /**
   * 停止生成。
   */
  function stopGeneration() {
    currentGeneration++
    if (currentAbortCtrl) {
      currentAbortCtrl.abort()
      currentAbortCtrl = null
    }
    stopFlushTimer()
    streaming.value = false
    loading.value = false

    // 如果最后一条 assistant 消息内容为空，标记为已停止
    const lastMsg = messages.value[messages.value.length - 1]
    if (lastMsg?.role === 'assistant' && !lastMsg.content) {
      lastMsg.content = '已停止生成'
    }
  }

  function clearHistory() {
    currentGeneration++
    stopFlushTimer()
    messages.value = []
    error.value = null
    streaming.value = false
    loading.value = false
    if (currentAbortCtrl) {
      currentAbortCtrl.abort()
      currentAbortCtrl = null
    }
  }

  function initWelcome(knowledgeBaseName?: string) {
    messages.value = [
      {
        id: generateId(),
        role: 'assistant',
        content: knowledgeBaseName
          ? `你好！我是 Synapse 知识库助手。我已经加载了「${knowledgeBaseName}」的内容，你可以问我任何关于该知识库的问题。`
          : '你好！我是 Synapse 知识库助手。请选择一个知识库后开始提问。',
        createdAt: Date.now()
      }
    ]
  }

  return {
    messages,
    loading,
    streaming,
    error,
    sendQuestionStream,
    stopGeneration,
    clearHistory,
    initWelcome
  }
})
