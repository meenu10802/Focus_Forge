import { createContext, useContext } from 'react'
import type { UserSummary } from '../api/types'

export interface RegisterInput {
  email: string
  password: string
  displayName: string
}

export interface AuthState {
  user: UserSummary | null
  isAuthenticated: boolean
  login: (email: string, password: string) => Promise<void>
  register: (input: RegisterInput) => Promise<void>
  logout: () => void
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
