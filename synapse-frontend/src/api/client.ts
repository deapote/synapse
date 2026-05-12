/// <reference types="vite/client" />
import axios, { AxiosError } from 'axios'
import type { ApiError } from '@/types'

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

client.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiError>) => {
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
