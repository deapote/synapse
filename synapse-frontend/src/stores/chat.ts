import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as api from '@/api/query'
import type { ChatMessage } from '@/types'

function generateId(): string {
  return Math.random().toString(36).substring(2, 10)
}

export const useChatStore = defineStore('chat', () => {
  // State
  const messages = ref<ChatMessage[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  // Actions
  async function sendQuestion(knowledgeBaseId: string, query: string) {
    // Add user message
    const userMessage: ChatMessage = {
      id: generateId(),
      role: 'user',
      content: query,
      createdAt: Date.now()
    }
    messages.value.push(userMessage)

    loading.value = true
    error.value = null

    try {
      const response = await api.queryKnowledgeBase(knowledgeBaseId, query)

      const assistantMessage: ChatMessage = {
        id: generateId(),
        role: 'assistant',
        content: response.answer,
        references: response.references,
        createdAt: Date.now()
      }
      messages.value.push(assistantMessage)
      return response
    } catch (err) {
      error.value = err instanceof Error ? err.message : '问答请求失败'
      // Add error message as assistant
      const errorMessage: ChatMessage = {
        id: generateId(),
        role: 'assistant',
        content: `请求失败：${error.value}`,
        createdAt: Date.now()
      }
      messages.value.push(errorMessage)
      throw err
    } finally {
      loading.value = false
    }
  }

  function clearHistory() {
    messages.value = []
    error.value = null
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
    error,
    sendQuestion,
    clearHistory,
    initWelcome
  }
})
