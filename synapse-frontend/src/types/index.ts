// ========== 知识库 ==========

export interface CreateKnowledgeBaseRequest {
  name: string
  description?: string
}

export interface KnowledgeBase {
  id: string
  name: string
  description: string
  createdAt: string
}

// ========== 文档 ==========

export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface Document {
  id: string
  knowledgeBaseId: string
  fileName: string
  fileType: string
  fileSize: number
  status: DocumentStatus
  chunkCount: number
  uploadedAt: string | null
}

// ========== 问答 ==========

export interface QueryRequest {
  query: string
}

export interface ChunkReference {
  documentId: string
  documentName: string
  chunkText: string
  score: number
  startPosition: number
  endPosition: number
}

// ========== API 错误 ==========

export interface ApiError {
  error: string
  message: string
  timestamp: string
}

// ========== 聊天 ==========

export type MessageRole = 'user' | 'assistant'

export interface ChatMessage {
  id: string
  role: MessageRole
  content: string
  references?: ChunkReference[]
  createdAt: number
}
