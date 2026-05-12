import client from './client'
import type { KnowledgeBase, CreateKnowledgeBaseRequest } from '@/types'

export async function listKnowledgeBases(): Promise<KnowledgeBase[]> {
  const res = await client.get('/knowledge-bases')
  return res.data
}

export async function createKnowledgeBase(
  data: CreateKnowledgeBaseRequest
): Promise<KnowledgeBase> {
  const res = await client.post('/knowledge-bases', data)
  return res.data
}

export async function deleteKnowledgeBase(id: string): Promise<void> {
  await client.delete(`/knowledge-bases/${id}`)
}
