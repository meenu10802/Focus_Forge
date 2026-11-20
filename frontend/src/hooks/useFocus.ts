import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { postIdempotent } from '../api/idempotent'
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
export function refreshHudSoon(qc: QueryClient) {
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

export function useCompleteTask(questId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (taskId: string) =>
      postIdempotent<CompletionResult>(`/api/quests/tasks/${taskId}/complete`, `complete.${taskId}`),
    onSuccess: () => {
      qc.setQueryData(activeSessionKey, null)
      refreshQuestData(qc, questId)
      refreshHudSoon(qc)
    },
  })
}
