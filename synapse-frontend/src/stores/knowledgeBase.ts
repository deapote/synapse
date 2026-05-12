import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as api from '@/api/knowledgeBase'
import type { KnowledgeBase, CreateKnowledgeBaseRequest } from '@/types'

const STORAGE_KEY = 'synapse:currentKbId'

export const useKnowledgeBaseStore = defineStore('knowledgeBase', () => {
  // State
  const list = ref<KnowledgeBase[]>([])
  const currentId = ref<string | null>(localStorage.getItem(STORAGE_KEY))
  const loading = ref(false)
  const error = ref<string | null>(null)

  // Getters
  const current = computed(() =>
    list.value.find((kb) => kb.id === currentId.value) ?? null
  )

  // Actions
  async function fetchList() {
    loading.value = true
    error.value = null
    try {
      list.value = await api.listKnowledgeBases()
    } catch (err) {
      error.value = err instanceof Error ? err.message : '获取知识库列表失败'
      throw err
    } finally {
      loading.value = false
    }
  }

  async function create(data: CreateKnowledgeBaseRequest) {
    error.value = null
    try {
      const kb = await api.createKnowledgeBase(data)
      list.value.unshift(kb)
      return kb
    } catch (err) {
      error.value = err instanceof Error ? err.message : '创建知识库失败'
      throw err
    }
  }

  async function remove(id: string) {
    error.value = null
    try {
      await api.deleteKnowledgeBase(id)
      list.value = list.value.filter((kb) => kb.id !== id)
      if (currentId.value === id) {
        setCurrent(null)
      }
    } catch (err) {
      error.value = err instanceof Error ? err.message : '删除知识库失败'
      throw err
    }
  }

  function setCurrent(id: string | null) {
    currentId.value = id
    if (id) {
      localStorage.setItem(STORAGE_KEY, id)
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
  }

  return {
    list,
    currentId,
    current,
    loading,
    error,
    fetchList,
    create,
    remove,
    setCurrent
  }
})
