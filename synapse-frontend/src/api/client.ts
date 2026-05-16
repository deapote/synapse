/// <reference types="vite/client" />
import axios, { AxiosError } from 'axios'
import type { ApiError } from '@/types'
import { clearToken, getStoredToken } from '@/api/token'

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

client.interceptors.request.use((config) => {
  const token = getStoredToken()
  if (token) {
    config.headers[token.tokenName] = token.tokenValue
  }
  return config
})

client.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiError>) => {
    if (error.response?.status === 401) {
      clearToken()
      if (window.location.pathname !== '/login') {
        window.location.href = `/login?redirect=${encodeURIComponent(window.location.pathname + window.location.search)}`
      }
    }
    if (error.response?.data?.message) {
      return Promise.reject(new Error(error.response.data.message))
    }
    if (error.message === 'Network Error') {
      return Promise.reject(new Error('网络异常，请检查后端服务是否运行'))
    }
    return Promise.reject(error)
  }
)

export default client
