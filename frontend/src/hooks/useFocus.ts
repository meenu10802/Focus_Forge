import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import axios from 'axios'
import { api } from '../api/client'
import type { AbandonResult, CompletionResult, FocusSession, Quest } from '../api/types'
import { dashboardKey } from './useDashboard'
import { questsKey } from './useQuests'
import { walletKey } from './useWallet'

export const activeSessionKey = ['focus', 'active'] as const
export const questKey = (id: string) => ['quest', id] as const

export function useQuest(questId: string) {
  return useQuery({
    queryKey: questKey(questId),
    queryFn: async () => (await api.get<Quest>(`/api/quests/${questId}`)).data,
  })
}

export function useActiveSession() {
  return useQuery({
    queryKey: activeSessionKey,
    // 204 No Content -> no session running
    queryFn: async () => (await api.get<FocusSession | ''>('/api/quests/sessions/active')).data || null,
  })
}

function refreshQuestData(qc: QueryClient, questId: string) {
  void qc.invalidateQueries({ queryKey: questKey(questId) })
  void qc.invalidateQueries({ queryKey: questsKey })
  void qc.invalidateQueries({ queryKey: activeSessionKey })
}

/**
 * Coins/XP/HP are applied asynchronously by Kafka consumers, so poll the HUD a
 * couple of times. (Phase 5 replaces this with a live SSE push.)
 */
function refreshHudSoon(qc: QueryClient) {
  for (const delay of [700, 2000, 4500]) {
    setTimeout(() => {
      void qc.invalidateQueries({ queryKey: dashboardKey })
      void qc.invalidateQueries({ queryKey: walletKey })
    }, delay)
  }
}

export function useStartFocus(questId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (taskId: string) => (await api.post<FocusSession>(`/api/quests/tasks/${taskId}/start`)).data,
    onSuccess: (session) => {
      qc.setQueryData(activeSessionKey, session)
      refreshQuestData(qc, questId)
    },
  })
}

export function useAbandonFocus(questId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (taskId: string) => (await api.post<AbandonResult>(`/api/quests/tasks/${taskId}/abandon`)).data,
    onSuccess: () => {
      qc.setQueryData(activeSessionKey, null)
      refreshQuestData(qc, questId)
      refreshHudSoon(qc)
    },
  })
}

// --- Idempotent completion ---------------------------------------------------

const keyStorage = (taskId: string) => `ff.idem.complete.${taskId}`

/**
 * One Idempotency-Key per logical "complete this task" attempt. Persisted in
 * sessionStorage so a reload in the middle of a request re-uses the same key
 * and the server replays its answer instead of crediting twice.
 */
function idempotencyKeyFor(taskId: string): string {
  try {
    const existing = sessionStorage.getItem(keyStorage(taskId))
    if (existing) return existing
    const fresh = crypto.randomUUID()
    sessionStorage.setItem(keyStorage(taskId), fresh)
    return fresh
  } catch {
    return crypto.randomUUID()
  }
}

function forgetKey(taskId: string) {
  try {
    sessionStorage.removeItem(keyStorage(taskId))
  } catch {
    /* ignore */
  }
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))

/** Retry only what is safe to retry with the same key: network failures, 5xx, and "still in flight" 409s. */
function isRetryable(err: unknown): boolean {
  if (!axios.isAxiosError(err)) return false
  if (!err.response) return true
  const { status, data } = err.response
  if (status >= 500) return true
  return status === 409 && typeof data?.detail === 'string' && data.detail.includes('still being processed')
}

export function useCompleteTask(questId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (taskId: string) => {
      const key = idempotencyKeyFor(taskId)
      let lastError: unknown
      for (const backoff of [0, 600, 1800]) {
        if (backoff) await sleep(backoff)
        try {
          const res = await api.post<CompletionResult>(`/api/quests/tasks/${taskId}/complete`, null, {
            headers: { 'Idempotency-Key': key },
          })
          forgetKey(taskId)
          return res.data
        } catch (err) {
          lastError = err
          if (!isRetryable(err)) break
        }
      }
      forgetKey(taskId) // definitive failure: next attempt is a new logical request
      throw lastError
    },
    onSuccess: () => {
      qc.setQueryData(activeSessionKey, null)
      refreshQuestData(qc, questId)
      refreshHudSoon(qc)
    },
  })
}
