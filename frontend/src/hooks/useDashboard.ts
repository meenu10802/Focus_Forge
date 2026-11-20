import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import type { Dashboard } from '../api/types'

export const dashboardKey = ['dashboard'] as const

export function useDashboard() {
  return useQuery({
    queryKey: dashboardKey,
    queryFn: async () => (await api.get<Dashboard>('/api/profile/me')).data,
  })
}
