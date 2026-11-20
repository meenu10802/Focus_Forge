import axios, { AxiosError } from 'axios'
import type { ProblemDetail } from './types'

export const API_URL: string = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

function safeStorage<T>(fn: () => T, fallback: T): T {
  try {
    return fn()
  } catch {
    return fallback // storage blocked (private mode etc.) — session lives in memory only
  }
}

function persisted(key: string) {
  let memory: string | null = safeStorage(() => localStorage.getItem(key), null)
  return {
    get: () => memory,
    set: (value: string | null) => {
      memory = value
      safeStorage(() => (value ? localStorage.setItem(key, value) : localStorage.removeItem(key)), undefined)
    },
  }
}

export const tokenStore = persisted('ff.token')
export const userStore = persisted('ff.user')

/** Single HTTP client; every request goes through the API gateway. */
export const api = axios.create({
  baseURL: API_URL,
  timeout: 15_000,
})

api.interceptors.request.use((config) => {
  const token = tokenStore.get()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

let onUnauthorized: (() => void) | null = null

/** AuthProvider registers a handler so an expired token logs the player out. */
export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler
}

api.interceptors.response.use(
  (res) => res,
  (error: AxiosError) => {
    const isAuthCall = error.config?.url?.startsWith('/api/auth/')
    if (error.response?.status === 401 && !isAuthCall) onUnauthorized?.()
    return Promise.reject(error)
  },
)

/** Human-readable message from an axios error carrying a ProblemDetail body. */
export function problemOf(error: unknown): ProblemDetail & { message: string } {
  if (axios.isAxiosError<ProblemDetail>(error)) {
    const body = error.response?.data
    if (body && typeof body === 'object') {
      return { ...body, message: body.detail ?? body.title ?? error.message }
    }
    if (!error.response) return { message: 'Cannot reach the server. Is the gateway running?' }
    return { message: error.message }
  }
  return { message: error instanceof Error ? error.message : 'Something went wrong.' }
}
