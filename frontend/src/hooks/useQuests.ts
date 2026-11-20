import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import type { Quest } from '../api/types'

export const questsKey = ['quests'] as const

export function useQuests() {
  return useQuery({
    queryKey: questsKey,
    queryFn: async () => (await api.get<Quest[]>('/api/quests')).data,
  })
}

export function useGenerateQuest() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (goal: string) =>
      // LLM + one retry can take a while; ai-quest falls back well before this.
      (await api.post<Quest>('/api/quests/generate', { goal }, { timeout: 45_000 })).data,
    onSuccess: (quest) => {
      queryClient.setQueryData<Quest[]>(questsKey, (old) => [quest, ...(old ?? []).filter((q) => q.id !== quest.id)])
    },
  })
}
