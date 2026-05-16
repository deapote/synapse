import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as api from '@/api/auth'
import { clearToken, getStoredToken, saveToken } from '@/api/token'
import type { CurrentUser, LoginRequest, LoginResponse, RoleName } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<CurrentUser | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const isAuthenticated = computed(() => !!user.value || !!getStoredToken())
  const isAdmin = computed(() => user.value?.roles.includes('ADMIN') ?? false)

  async function login(credentials: LoginRequest) {
    loading.value = true
    error.value = null
    try {
      const result: LoginResponse = await api.login(credentials)
      saveToken(result.tokenName, result.tokenValue)
      user.value = {
        id: result.id,
        username: result.username,
        displayName: result.displayName,
        roles: result.roles,
        permissions: result.permissions,
        enabled: true,
        createdAt: null
      }
      return user.value
    } catch (err) {
      error.value = err instanceof Error ? err.message : '登录失败'
      throw err
    } finally {
      loading.value = false
    }
  }

  async function fetchMe() {
    if (!getStoredToken()) {
      user.value = null
      return null
    }
    loading.value = true
    error.value = null
    try {
      user.value = await api.me()
      return user.value
    } catch (err) {
      clearToken()
      user.value = null
      error.value = err instanceof Error ? err.message : '登录状态已失效'
      throw err
    } finally {
      loading.value = false
    }
  }

  async function logout() {
    try {
      if (getStoredToken()) {
        await api.logout()
      }
    } finally {
      clearToken()
      user.value = null
    }
  }

  function hasRole(role: RoleName) {
    return user.value?.roles.includes(role) ?? false
  }

  return {
    user,
    loading,
    error,
    isAuthenticated,
    isAdmin,
    login,
    fetchMe,
    logout,
    hasRole
  }
})
