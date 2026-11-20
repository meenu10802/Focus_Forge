import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import type { Wallet } from '../api/types'

export const walletKey = ['wallet'] as const

export function useWallet() {
  return useQuery({
    queryKey: walletKey,
    queryFn: async () => (await api.get<Wallet>('/api/wallet/me')).data,
  })
}
