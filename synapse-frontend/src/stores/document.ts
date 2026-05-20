import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as api from '@/api/document'
import type { Document, DocumentUploadMetadata, PatchDocumentMetadata } from '@/types'

export const useDocumentStore = defineStore('document', () => {
  // State
  const list = ref<Document[]>([])
  const loading = ref(false)
  const uploading = ref(false)
  const error = ref<string | null>(null)
  const filters = ref<api.DocumentListFilters>({})

  // Actions
  async function fetchList(knowledgeBaseId: string, queryFilters?: api.DocumentListFilters) {
    loading.value = true
    error.value = null
    try {
      const f = queryFilters || filters.value
      list.value = await api.listDocuments(knowledgeBaseId, f)
    } catch (err) {
      error.value = err instanceof Error ? err.message : '获取文档列表失败'
      throw err
    } finally {
      loading.value = false
    }
  }

  async function upload(knowledgeBaseId: string, file: File, metadata?: DocumentUploadMetadata) {
    uploading.value = true
    error.value = null
    try {
      const doc = await api.uploadDocument(knowledgeBaseId, file, metadata)
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

  async function patchDocMetadata(id: string, patch: PatchDocumentMetadata) {
    error.value = null
    try {
      const doc = await api.patchMetadata(id, patch)
      const idx = list.value.findIndex((d) => d.id === id)
      if (idx >= 0) {
        list.value[idx] = doc
      }
      return doc
    } catch (err) {
      error.value = err instanceof Error ? err.message : '修改元数据失败'
      throw err
    }
  }

  async function supersedeDocument(id: string, newDocumentId: string, effectiveTo: string) {
    error.value = null
    try {
      await api.supersedeDocument(id, newDocumentId, effectiveTo)
    } catch (err) {
      error.value = err instanceof Error ? err.message : '替代文档失败'
      throw err
    }
  }

  async function retireDoc(id: string, effectiveTo?: string) {
    error.value = null
    try {
      const doc = await api.retireDocument(id, effectiveTo)
      const idx = list.value.findIndex((d) => d.id === id)
      if (idx >= 0) {
        list.value[idx] = doc
      }
      return doc
    } catch (err) {
      error.value = err instanceof Error ? err.message : '废止文档失败'
      throw err
    }
  }

  async function reactivateDoc(id: string) {
    error.value = null
    try {
      const doc = await api.reactivateDocument(id)
      const idx = list.value.findIndex((d) => d.id === id)
      if (idx >= 0) {
        list.value[idx] = doc
      }
      return doc
    } catch (err) {
      error.value = err instanceof Error ? err.message : '重新启用文档失败'
      throw err
    }
  }

  async function reindexDoc(id: string) {
    error.value = null
    try {
      const doc = await api.reindexDocument(id)
      const idx = list.value.findIndex((d) => d.id === id)
      if (idx >= 0) {
        list.value[idx] = doc
      }
      return doc
    } catch (err) {
      error.value = err instanceof Error ? err.message : '刷新索引失败'
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

  function updateDocumentInList(updated: Document) {
    const idx = list.value.findIndex((d) => d.id === updated.id)
    if (idx >= 0) {
      list.value[idx] = updated
    }
  }

  return {
    list,
    loading,
    uploading,
    error,
    filters,
    fetchList,
    upload,
    remove,
    patchDocMetadata,
    supersedeDocument,
    retireDoc,
    reactivateDoc,
    reindexDoc,
    updateDocumentStatus,
    updateDocumentInList
  }
})
