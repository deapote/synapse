import client from './client'
import type { Document, DocumentUploadMetadata, PatchDocumentMetadata, DocumentAuditEvent } from '@/types'

export interface DocumentListFilters {
  sourceType?: string
  lifecycleStatus?: string
  indexStatus?: string
  canonicalKey?: string
}

export async function listDocuments(knowledgeBaseId: string, filters?: DocumentListFilters): Promise<Document[]> {
  const params = new URLSearchParams()
  if (filters?.sourceType) params.append('sourceType', filters.sourceType)
  if (filters?.lifecycleStatus) params.append('lifecycleStatus', filters.lifecycleStatus)
  if (filters?.indexStatus) params.append('indexStatus', filters.indexStatus)
  if (filters?.canonicalKey) params.append('canonicalKey', filters.canonicalKey)
  const query = params.toString()
  const res = await client.get(`/knowledge-bases/${knowledgeBaseId}/documents${query ? '?' + query : ''}`)
  return res.data
}

export async function uploadDocument(
  knowledgeBaseId: string,
  file: File,
  metadata?: DocumentUploadMetadata
): Promise<Document> {
  const formData = new FormData()
  formData.append('file', file)

  if (metadata) {
    if (metadata.sourceType) formData.append('sourceType', metadata.sourceType)
    if (metadata.canonicalKey) formData.append('canonicalKey', metadata.canonicalKey)
    if (metadata.versionLabel) formData.append('versionLabel', metadata.versionLabel)
    if (metadata.effectiveFrom) formData.append('effectiveFrom', metadata.effectiveFrom)
    if (metadata.effectiveTo) formData.append('effectiveTo', metadata.effectiveTo)
    if (metadata.authorityLevel !== undefined) formData.append('authorityLevel', String(metadata.authorityLevel))
    if (metadata.jurisdiction) formData.append('jurisdiction', metadata.jurisdiction)
    if (metadata.supersedesDocumentId) formData.append('supersedesDocumentId', metadata.supersedesDocumentId)
  }

  const res = await client.post(`/knowledge-bases/${knowledgeBaseId}/documents`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
  return res.data
}

export async function deleteDocument(id: string): Promise<void> {
  await client.delete(`/documents/${id}`)
}

export async function patchMetadata(id: string, patch: PatchDocumentMetadata): Promise<Document> {
  const res = await client.put(`/documents/${id}/metadata`, patch)
  return res.data
}

export async function supersedeDocument(id: string, newDocumentId: string, effectiveTo: string): Promise<void> {
  await client.post(`/documents/${id}/supersede`, { newDocumentId, effectiveTo })
}

export async function retireDocument(id: string, effectiveTo?: string): Promise<Document> {
  const res = await client.post(`/documents/${id}/retire`, { effectiveTo })
  return res.data
}

export async function reactivateDocument(id: string): Promise<Document> {
  const res = await client.post(`/documents/${id}/reactivate`)
  return res.data
}

export async function reindexDocument(id: string): Promise<Document> {
  const res = await client.post(`/documents/${id}/reindex`)
  return res.data
}

export async function getVersionChain(id: string): Promise<Document[]> {
  const res = await client.get(`/documents/${id}/version-chain`)
  return res.data
}

export async function getAuditEvents(id: string): Promise<DocumentAuditEvent[]> {
  const res = await client.get(`/documents/${id}/audit-events`)
  return res.data
}
