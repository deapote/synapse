import client from './client'
import type { Document } from '@/types'

export async function listDocuments(knowledgeBaseId: string): Promise<Document[]> {
  const res = await client.get(`/knowledge-bases/${knowledgeBaseId}/documents`)
  return res.data
}

export async function uploadDocument(
  knowledgeBaseId: string,
  file: File
): Promise<Document> {
  const formData = new FormData()
  formData.append('file', file)

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
