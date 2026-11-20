import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api, setUnauthorizedHandler, tokenStore, userStore } from '../api/client'
import type { AuthResponse, UserSummary } from '../api/types'
import { AuthContext, type AuthState, type RegisterInput } from './authContext'

function readStoredUser(): UserSummary | null {
  const raw = userStore.get()
  if (!raw || !tokenStore.get()) return null
  try {
    return JSON.parse(raw) as UserSummary
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [user, setUser] = useState<UserSummary | null>(readStoredUser)

  const accept = useCallback((res: AuthResponse) => {
    tokenStore.set(res.accessToken)
    userStore.set(JSON.stringify(res.user))
    setUser(res.user)
  }, [])

  const logout = useCallback(() => {
    tokenStore.set(null)
    userStore.set(null)
    setUser(null)
    queryClient.clear()
  }, [queryClient])

  useEffect(() => {
    setUnauthorizedHandler(logout)
    return () => setUnauthorizedHandler(null)
  }, [logout])

  const login = useCallback(
    async (email: string, password: string) => {
      accept((await api.post<AuthResponse>('/api/auth/login', { email, password })).data)
    },
    [accept],
  )

  const register = useCallback(
    async (input: RegisterInput) => {
      accept((await api.post<AuthResponse>('/api/auth/register', input)).data)
    },
    [accept],
  )

  const value = useMemo<AuthState>(
    () => ({ user, isAuthenticated: user !== null, login, register, logout }),
    [user, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
