import client from '@/api/client'
import type { ChatMessageResponse, ChatSession, ChunkReference } from '@/types'
import { clearToken, getStoredToken } from '@/api/token'

export interface StreamCallbacks {
  onSession?: (sessionId: string) => void
  onToken: (token: string) => void
  onReferences: (references: ChunkReference[]) => void
  onComplete: () => void
  onError: (message: string) => void
}

export async function getCurrentChatSession(knowledgeBaseId: string): Promise<ChatSession> {
  const { data } = await client.get<ChatSession>(`/knowledge-bases/${knowledgeBaseId}/chat/sessions/current`)
  return data
}

export async function createChatSession(knowledgeBaseId: string): Promise<ChatSession> {
  const { data } = await client.post<ChatSession>(`/knowledge-bases/${knowledgeBaseId}/chat/sessions`)
  return data
}

export async function listChatMessages(sessionId: string, page = 0, size = 50): Promise<ChatMessageResponse[]> {
  const { data } = await client.get<ChatMessageResponse[]>(`/chat/sessions/${sessionId}/messages`, {
    params: { page, size }
  })
  return data
}

/**
 * 流式问答 SSE 接口。
 *
 * 使用原生 fetch + ReadableStream 手动解析 SSE，
 * 避免引入额外依赖（axios 的 stream 支持在浏览器端不可靠）。
 *
 * @returns AbortController，用于取消流
 */
export function streamQueryKnowledgeBase(
  knowledgeBaseId: string,
  query: string,
  sessionId: string | null,
  callbacks: StreamCallbacks
): AbortController {
  const ctrl = new AbortController()
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
  const token = getStoredToken()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json'
  }
  if (token) {
    headers[token.tokenName] = token.tokenValue
  }

  fetch(`${baseUrl}/knowledge-bases/${knowledgeBaseId}/query/stream`, {
    method: 'POST',
    headers,
    body: JSON.stringify(sessionId ? { query, sessionId } : { query }),
    signal: ctrl.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        const message = await errorMessage(response)
        if (response.status === 401) {
          clearToken()
          if (window.location.pathname !== '/login') {
            window.location.href = `/login?redirect=${encodeURIComponent(window.location.pathname + window.location.search)}`
          }
        }
        throw new Error(message)
      }
      if (!response.body) {
        throw new Error('响应体为空')
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let terminalEventReceived = false

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        // SSE 按 \n\n 分割事件块
        const chunks = buffer.split('\n\n')
        buffer = chunks.pop() || ''

        for (const chunk of chunks) {
          const lines = chunk.split('\n')
          let event = 'message'
          let data: string | null = null

          for (const line of lines) {
            if (line.startsWith('event:')) {
              event = line.slice(6).trim()
            } else if (line.startsWith('data:')) {
              data = line.slice(5).trim()
            }
          }

          if (!data) continue

          let payload: any
          try {
            payload = JSON.parse(data)
          } catch (e) {
            console.error('SSE JSON parse error:', data, e)
            continue
          }

          if (event === 'session') {
            callbacks.onSession?.(payload.sessionId)
          } else if (event === 'token') {
            callbacks.onToken(payload.token)
          } else if (event === 'references') {
            callbacks.onReferences(payload.references)
          } else if (event === 'complete') {
            terminalEventReceived = true
            callbacks.onComplete()
          } else if (event === 'error') {
            terminalEventReceived = true
            callbacks.onError(payload.message)
          }
        }
      }

      if (!terminalEventReceived) {
        callbacks.onComplete()
      }
    })
    .catch((err) => {
      if (err.name === 'AbortError') {
        callbacks.onComplete()
      } else {
        callbacks.onError(err instanceof Error ? err.message : 'SSE 连接错误')
      }
    })

  return ctrl
}

async function errorMessage(response: Response): Promise<string> {
  const text = await response.text()
  if (!text) {
    return `HTTP ${response.status}`
  }
  try {
    const payload = JSON.parse(text)
    if (payload?.message) {
      return payload.message
    }
  } catch {
    // Fall through to raw text.
  }
  return `HTTP ${response.status}: ${text}`
}
