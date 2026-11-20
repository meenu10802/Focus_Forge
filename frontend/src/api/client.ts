import axios from 'axios'

export const API_URL: string = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

const TOKEN_KEY = 'ff.token'

export const tokenStore = {
  get: (): string | null => {
    try {
      return localStorage.getItem(TOKEN_KEY)
    } catch {
      return null
    }
  },
  set: (token: string | null) => {
    try {
      if (token) localStorage.setItem(TOKEN_KEY, token)
      else localStorage.removeItem(TOKEN_KEY)
    } catch {
      /* storage unavailable — token lives for this tab only */
    }
  },
}

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
