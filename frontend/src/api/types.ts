/** Mirrors identity-rpg-service's dashboard DTO (implemented in Phase 1). */
export interface PlayerStats {
  displayName: string
  hp: number
  maxHp: number
  xp: number
  xpForNextLevel: number
  level: number
  coins: number
  streakDays: number
}
