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

export type Difficulty = 'MICRO' | 'EASY' | 'MEDIUM' | 'HARD'
export type TaskStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'ABANDONED'

/** Mirrors quest-engine-service TaskDto. */
export interface QuestTask {
  id: string
  parentTaskId: string | null
  position: number
  title: string
  description: string
  minutes: number
  difficulty: Difficulty
  coins: number
  xp: number
  hpRestore: number
  status: TaskStatus
}

/** Mirrors quest-engine-service QuestDto. */
export interface Quest {
  id: string
  title: string
  goal: string
  status: 'ACTIVE' | 'COMPLETED' | 'ARCHIVED'
  /** FALLBACK = built-in template because the AI was unavailable. */
  source: 'AI_GENERATED' | 'FALLBACK'
  provider: string
  createdAt: string
  totalCoins: number
  totalXp: number
  totalMinutes: number
  tasks: QuestTask[]
}

/** bank-store-service GET /api/wallet/me */
export interface Wallet {
  balance: number
  updatedAt: string | null
}

/** quest-engine-service focus session. */
export interface FocusSession {
  sessionId: string
  taskId: string
  questId: string
  taskTitle: string
  plannedMinutes: number
  startedAt: string
  status: 'ACTIVE' | 'COMPLETED' | 'ABANDONED'
}

export interface CompletionResult {
  taskId: string
  questId: string
  coinsEarned: number
  xpEarned: number
  hpRestored: number
  focusMinutes: number
  questCompleted: boolean
  eventId: string
}

export interface AbandonResult {
  taskId: string
  hpLost: number
  eventId: string
}

/** RFC 7807 body returned by every service and the gateway. */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  errors?: Record<string, string>
}
