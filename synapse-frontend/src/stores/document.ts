import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as api from '@/api/document'
import type { Document } from '@/types'

export const useDocumentStore = defineStore('document', () => {
  // State
  const list = ref<Document[]>([])
  const loading = ref(false)
  const uploading = ref(false)
  const error = ref<string | null>(null)

  // Actions
  async function fetchList(knowledgeBaseId: string) {
    loading.value = true
    error.value = null
    try {
      list.value = await api.listDocuments(knowledgeBaseId)
    } catch (err) {
      error.value = err instanceof Error ? err.message : '获取文档列表失败'
      throw err
    } finally {
      loading.value = false
    }
  }

  async function upload(knowledgeBaseId: string, file: File) {
    uploading.value = true
    error.value = null
    try {
      const doc = await api.uploadDocument(knowledgeBaseId, file)
      list.value.unshift(doc)
      return doc
    } catch (err) {
      error.value = err instanceof Error ? err.message : '上传文档失败'
      throw err
    } finally {
      uploading.value = false
    }
  }

  async function remove(id: string) {
    error.value = null
    try {
      await api.deleteDocument(id)
      list.value = list.value.filter((doc) => doc.id !== id)
    } catch (err) {
      error.value = err instanceof Error ? err.message : '删除文档失败'
      throw err
    }
  }

  function updateDocumentStatus(id: string, status: Document['status'], chunkCount?: number) {
    const doc = list.value.find((d) => d.id === id)
    if (doc) {
      doc.status = status
      if (chunkCount !== undefined) {
        doc.chunkCount = chunkCount
      }
    }
  }

  return {
    list,
    loading,
    uploading,
    error,
    fetchList,
    upload,
    remove,
    updateDocumentStatus
  }
})
