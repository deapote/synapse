// ========== 知识库 ==========

export interface CreateKnowledgeBaseRequest {
  name: string
  description?: string
}

export interface KnowledgeBase {
  id: string
  name: string
  description: string
  ownerUserId: string
  createdAt: string
}

// ========== 认证 / 权限 ==========

export type RoleName = 'ADMIN' | 'USER'
export type AuthPermission = 'KB_READ' | 'KB_WRITE' | 'KB_DELETE' | 'AUTH_ADMIN'

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  id: string
  username: string
  displayName: string
  roles: RoleName[]
  permissions: AuthPermission[]
  tokenName: string
  tokenValue: string
}

export interface CurrentUser {
  id: string
  username: string
  displayName: string
  roles: RoleName[]
  permissions: AuthPermission[]
  enabled: boolean
  createdAt: string | null
}

export interface CreateUserRequest {
  username: string
  displayName?: string
  password: string
  roles: RoleName[]
}

export interface UserAdminView {
  id: string
  username: string
  displayName: string
  roles: RoleName[]
  enabled: boolean
  createdAt: string
}

export interface RoleView {
  name: RoleName
  permissions: AuthPermission[]
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
  sessionId?: string
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

export interface ChatSession {
  id: string
  knowledgeBaseId: string
  title: string
  summary: string
  messageCount: number
  createdAt: string
  updatedAt: string
}

export interface ChatMessage {
  id: string
  sessionId?: string
  role: MessageRole
  content: string
  references?: ChunkReference[]
  createdAt: number
}

export interface ChatMessageResponse {
  id: string
  sessionId: string
  role: MessageRole
  content: string
  references: ChunkReference[]
  sequence: number
  createdAt: string
}
