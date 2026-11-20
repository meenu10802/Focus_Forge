import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { postIdempotent } from '../api/idempotent'
import type { Redemption, Reward, RewardInput, Wallet } from '../api/types'
import { walletKey } from './useWallet'

export const rewardsKey = ['store', 'rewards'] as const
export const redemptionsKey = ['store', 'redemptions'] as const

export function useRewards() {
  return useQuery({
    queryKey: rewardsKey,
    queryFn: async () => (await api.get<Reward[]>('/api/store/rewards')).data,
  })
}

export function useRedemptions() {
  return useQuery({
    queryKey: redemptionsKey,
    queryFn: async () => (await api.get<Redemption[]>('/api/store/redemptions')).data,
  })
}

export function useRedeem() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (rewardId: string) =>
      postIdempotent<Redemption>(`/api/store/rewards/${rewardId}/redeem`, `redeem.${rewardId}`),
    onSuccess: (r) => {
      // Redemption is synchronous (one DB transaction), so we can update the HUD immediately.
      qc.setQueryData<Wallet>(walletKey, (w) => ({ balance: r.newBalance, updatedAt: r.redeemedAt ?? w?.updatedAt ?? null }))
      qc.setQueryData<Reward[]>(rewardsKey, (list) =>
        list?.map((x) => (x.id === r.rewardId ? { ...x, stock: Math.max(0, x.stock - 1) } : x)),
      )
      void qc.invalidateQueries({ queryKey: rewardsKey })
      void qc.invalidateQueries({ queryKey: redemptionsKey })
    },
    onError: () => {
      // stock/balance may have changed under us (e.g. sold out): refresh the truth
      void qc.invalidateQueries({ queryKey: rewardsKey })
      void qc.invalidateQueries({ queryKey: walletKey })
    },
  })
}

export function useSaveReward() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, input }: { id?: string; input: RewardInput }) =>
      id
        ? (await api.put<Reward>(`/api/store/rewards/${id}`, input)).data
        : (await api.post<Reward>('/api/store/rewards', input)).data,
    onSuccess: () => void qc.invalidateQueries({ queryKey: rewardsKey }),
  })
}

export function useDeleteReward() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (id: string) => {
      await api.delete(`/api/store/rewards/${id}`)
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: rewardsKey }),
  })
}
