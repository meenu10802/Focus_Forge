/** Mirrors identity-rpg-service DashboardDto (GET /api/profile/me). */
export interface Dashboard {
  userId: string
  displayName: string
  role: 'PLAYER' | 'ADMIN'
  level: number
  hp: number
  maxHp: number
  xp: number
  xpForNextLevel: number
  streakDays: number
}

export interface UserSummary {
  id: string
  email: string
  displayName: string
  role: 'PLAYER' | 'ADMIN'
}

export interface AuthResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: UserSummary
}

/** RFC 7807 body returned by every service and the gateway. */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  errors?: Record<string, string>
}
