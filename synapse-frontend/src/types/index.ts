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

export type DocumentSourceType = 'GENERAL' | 'LEGAL' | 'POLICY'

export type DocumentLifecycleStatus = 'ACTIVE' | 'SUPERSEDED' | 'RETIRED'

export type DocumentIndexStatus = 'SYNCED' | 'STALE' | 'REFRESHING' | 'FAILED'

export interface Document {
  id: string
  knowledgeBaseId: string
  fileName: string
  fileType: string
  fileSize: number
  status: DocumentStatus
  chunkCount: number
  uploadedAt: string | null
  // 时效元数据
  sourceType?: DocumentSourceType | null
  canonicalKey?: string | null
  versionLabel?: string | null
  effectiveFrom?: string | null
  effectiveTo?: string | null
  lifecycleStatus?: DocumentLifecycleStatus | null
  supersedesDocumentId?: string | null
  authorityLevel?: number | null
  jurisdiction?: string | null
  // v2 索引状态
  metadataVersion?: number | null
  indexedMetadataVersion?: number | null
  indexStatus?: DocumentIndexStatus | null
  lastIndexRefreshAt?: string | null
  lastIndexFailureReason?: string | null
}

export interface DocumentAuditEvent {
  id: string
  documentId: string
  knowledgeBaseId: string
  actorUserId: string
  action: string
  beforeSnapshot: string | null
  afterSnapshot: string | null
  reason: string | null
  createdAt: string
}

export interface PatchDocumentMetadata {
  sourceType?: DocumentSourceType | null
  canonicalKey?: string | null
  versionLabel?: string | null
  effectiveFrom?: string | null
  effectiveTo?: string | null
  authorityLevel?: number | null
  jurisdiction?: string | null
}

export interface DocumentUploadMetadata {
  sourceType?: DocumentSourceType
  canonicalKey?: string
  versionLabel?: string
  effectiveFrom?: string
  effectiveTo?: string
  authorityLevel?: number
  jurisdiction?: string
  supersedesDocumentId?: string
}

// ========== 问答 ==========

export interface QueryRequest {
  query: string
  sessionId?: string
  asOfDate?: string
}

export interface ChunkReference {
  sourceId: number
  documentId: string
  documentName: string
  chunkText: string
  score: number
  startPosition: number
  endPosition: number
  used?: boolean | null
  // 时效元数据
  canonicalKey?: string | null
  versionLabel?: string | null
  effectiveFrom?: string | null
  effectiveTo?: string | null
  lifecycleStatus?: DocumentLifecycleStatus | null
  authorityLevel?: number | null
  jurisdiction?: string | null
}

export interface CitationValidation {
  trusted: boolean
  usedSourceIds: number[]
  warnings: string[]
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
  validation?: CitationValidation
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
