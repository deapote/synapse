import client from './client'
import type { AnswerResponse } from '@/types'

export async function queryKnowledgeBase(
  knowledgeBaseId: string,
  query: string
): Promise<AnswerResponse> {
  const res = await client.post(`/knowledge-bases/${knowledgeBaseId}/query`, { query })
  return res.data
}
