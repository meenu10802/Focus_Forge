import { useEffect, useState } from 'react'
import { problemOf } from '../api/client'
import type { Redemption, Reward } from '../api/types'
import { RewardFormDialog } from '../components/RewardFormDialog'
import { useDeleteReward, useRedeem, useRedemptions, useRewards } from '../hooks/useStore'
import { useWallet } from '../hooks/useWallet'

type Toast = { kind: 'success' | 'error'; text: string }

function RewardCard({
  reward,
  balance,
  busy,
  onRedeem,
  onEdit,
  onDelete,
}: {
  reward: Reward
  balance: number | undefined
  busy: boolean
  onRedeem: () => void
  onEdit: () => void
  onDelete: () => void
}) {
  const soldOut = reward.stock <= 0
  const shortBy = balance === undefined ? 0 : Math.max(0, reward.price - balance)
  const canAfford = shortBy === 0
  const lowStock = !soldOut && reward.stock <= 5

  return (
    <article className={`flex flex-col rounded-2xl border bg-panel p-5 ${soldOut ? 'border-panel-edge opacity-60' : 'border-panel-edge'}`}>
      <div className="flex items-start justify-between gap-3">
        <span className="text-4xl" aria-hidden="true">
          {reward.emoji}
        </span>
        {reward.mine ? (
          <span className="rounded-full border border-xp/40 px-2 py-0.5 text-xs font-semibold text-xp">Your reward</span>
        ) : null}
      </div>
      <h3 className="mt-3 text-lg font-black">{reward.name}</h3>
      {reward.description && <p className="mt-1 text-sm text-muted">{reward.description}</p>}

      <div className="mt-4 flex items-baseline justify-between">
        <span className="text-xl font-black tabular-nums text-coin">🪙 {reward.price}</span>
        <span className={`text-sm font-semibold ${soldOut ? 'text-hp-low' : lowStock ? 'text-amber-300' : 'text-muted'}`}>
          {soldOut ? 'Sold out' : `${reward.stock} left`}
        </span>
      </div>

      <div className="mt-auto pt-4">
        <button
          type="button"
          disabled={soldOut || !canAfford || busy}
          onClick={onRedeem}
          className="w-full rounded-xl bg-xp-glow px-4 py-3 font-black text-ink transition hover:brightness-110 disabled:cursor-not-allowed disabled:bg-panel-edge disabled:text-muted"
        >
          {busy ? 'Redeeming…' : soldOut ? 'Sold out' : canAfford ? 'Redeem' : `Need ${shortBy} more`}
        </button>
        {reward.mine && (
          <div className="mt-2 flex justify-center gap-4 text-sm">
            <button type="button" onClick={onEdit} className="font-semibold text-muted hover:text-slate-200">
              Edit
            </button>
            <button type="button" onClick={onDelete} className="font-semibold text-muted hover:text-hp-low">
              Delete
            </button>
          </div>
        )}
      </div>
    </article>
  )
}

function History({ items }: { items: Redemption[] }) {
  if (items.length === 0) {
    return <p className="text-sm text-muted">Nothing redeemed yet. Your first treat is waiting.</p>
  }
  return (
    <ul className="divide-y divide-panel-edge rounded-2xl border border-panel-edge bg-panel">
      {items.map((r) => (
        <li key={r.redemptionId} className="flex items-center justify-between gap-4 px-4 py-3 text-sm">
          <span className="min-w-0 truncate font-semibold">{r.rewardName}</span>
          <span className="shrink-0 tabular-nums text-muted">
            <span className="text-hp-low">−{r.price}</span> · {new Date(r.redeemedAt).toLocaleDateString()}
          </span>
        </li>
      ))}
    </ul>
  )
}

export function StorePage() {
  const rewards = useRewards()
  const history = useRedemptions()
  const wallet = useWallet()
  const redeem = useRedeem()
  const remove = useDeleteReward()
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<Reward | null>(null)
  const [toast, setToast] = useState<Toast | null>(null)
  const [pendingId, setPendingId] = useState<string | null>(null)

  useEffect(() => {
    if (!toast) return
    const id = setTimeout(() => setToast(null), 4500)
    return () => clearTimeout(id)
  }, [toast])

  const balance = wallet.data?.balance
  const all = rewards.data ?? []
  const mine = all.filter((r) => r.mine)
  const store = all.filter((r) => !r.mine)

  function doRedeem(reward: Reward) {
    setPendingId(reward.id)
    redeem.mutate(reward.id, {
      onSuccess: (r) => setToast({ kind: 'success', text: `${reward.emoji} Enjoy your ${r.rewardName}! You earned it.` }),
      onError: (err) => setToast({ kind: 'error', text: problemOf(err).message }),
      onSettled: () => setPendingId(null),
    })
  }

  const grid = (items: Reward[]) => (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {items.map((r) => (
        <RewardCard
          key={r.id}
          reward={r}
          balance={balance}
          busy={pendingId === r.id}
          onRedeem={() => doRedeem(r)}
          onEdit={() => {
            setEditing(r)
            setFormOpen(true)
          }}
          onDelete={() => {
            if (window.confirm(`Delete "${r.name}" from your rewards?`)) remove.mutate(r.id)
          }}
        />
      ))}
    </div>
  )

  return (
    <main className="mx-auto max-w-5xl px-4 py-8">
      <div className="mb-8 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-black tracking-tight">Reward Store</h1>
          <p className="mt-1 text-muted">Trade the coins you earned focusing for real-life treats.</p>
        </div>
        <div className="rounded-2xl border border-coin/30 bg-coin/5 px-5 py-3 text-right">
          <div className="text-xs font-semibold tracking-wide text-muted uppercase">Your balance</div>
          <div className="text-2xl font-black tabular-nums text-coin">🪙 {balance ?? '–'}</div>
        </div>
      </div>

      {rewards.isError && (
        <p role="alert" className="mb-6 rounded-xl bg-hp-low/10 px-4 py-3 text-sm text-hp-low">
          Couldn’t load the store: {problemOf(rewards.error).message}
        </p>
      )}

      <section aria-labelledby="my-rewards" className="mb-10">
        <div className="mb-4 flex items-center justify-between gap-4">
          <h2 id="my-rewards" className="text-sm font-semibold tracking-wide text-muted uppercase">
            My custom rewards
          </h2>
          <button
            type="button"
            onClick={() => {
              setEditing(null)
              setFormOpen(true)
            }}
            className="rounded-xl border border-panel-edge px-4 py-2 text-sm font-bold hover:border-xp hover:text-white"
          >
            + Create reward
          </button>
        </div>
        {rewards.isPending ? (
          <div className="h-40 animate-pulse rounded-2xl bg-panel" />
        ) : mine.length ? (
          grid(mine)
        ) : (
          <p className="rounded-2xl border border-dashed border-panel-edge p-6 text-center text-sm text-muted">
            What actually motivates you? Add your own reward and set its price.
          </p>
        )}
      </section>

      <section aria-labelledby="store-rewards" className="mb-10">
        <h2 id="store-rewards" className="mb-4 text-sm font-semibold tracking-wide text-muted uppercase">
          Store
        </h2>
        {rewards.isPending ? <div className="h-64 animate-pulse rounded-2xl bg-panel" /> : grid(store)}
      </section>

      <section aria-labelledby="history">
        <h2 id="history" className="mb-4 text-sm font-semibold tracking-wide text-muted uppercase">
          Recent redemptions
        </h2>
        <History items={history.data ?? []} />
      </section>

      {toast && (
        <div
          role={toast.kind === 'error' ? 'alert' : 'status'}
          className={`fixed inset-x-4 bottom-6 z-50 mx-auto max-w-md rounded-2xl px-5 py-4 text-center font-bold shadow-2xl ${
            toast.kind === 'success' ? 'bg-hp text-ink' : 'bg-hp-low text-white'
          }`}
        >
          {toast.text}
        </div>
      )}

      <RewardFormDialog open={formOpen} editing={editing} onClose={() => setFormOpen(false)} />
    </main>
  )
}
