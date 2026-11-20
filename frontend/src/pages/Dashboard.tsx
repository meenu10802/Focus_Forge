import { useState } from 'react'
import { problemOf } from '../api/client'
import { useAuth } from '../auth/authContext'
import { QuestCard } from '../components/QuestCard'
import { QuestGeneratorDialog } from '../components/QuestGeneratorDialog'
import { useDashboard } from '../hooks/useDashboard'
import { useQuests } from '../hooks/useQuests'

function QuestListSkeleton() {
  return (
    <div className="space-y-4" aria-busy="true" aria-label="Loading quests">
      {[0, 1].map((i) => (
        <div key={i} className="h-48 animate-pulse rounded-2xl border border-panel-edge bg-panel" />
      ))}
    </div>
  )
}

export function Dashboard() {
  const { user } = useAuth()
  const profile = useDashboard()
  const quests = useQuests()
  const [dialogOpen, setDialogOpen] = useState(false)
  const [freshId, setFreshId] = useState<string | null>(null)

  const name = profile.data?.displayName ?? user?.displayName ?? 'Adventurer'
  const error = profile.error ?? quests.error

  return (
    <main className="mx-auto max-w-5xl px-4 py-8">
      <div className="mb-8 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-black tracking-tight">Welcome, {name}</h1>
          <p className="mt-1 text-muted">
            {profile.data
              ? `Level ${profile.data.level} · ${profile.data.xpForNextLevel - profile.data.xp} XP to level ${profile.data.level + 1}`
              : 'Loading your stats…'}
            {profile.data?.role === 'ADMIN' && (
              <span className="ml-2 rounded bg-xp-glow/15 px-1.5 py-0.5 text-xs font-bold text-xp-glow">ADMIN</span>
            )}
          </p>
        </div>
        <button
          type="button"
          onClick={() => setDialogOpen(true)}
          className="rounded-xl bg-xp px-4 py-2.5 font-bold text-white shadow-lg shadow-xp/20 transition hover:bg-xp/90"
        >
          + New quest
        </button>
      </div>

      {error && (
        <div role="alert" className="mb-6 flex items-center justify-between gap-4 rounded-xl border border-hp-low/40 bg-hp-low/10 px-4 py-3 text-sm">
          <span>Couldn’t load your data: {problemOf(error).message}</span>
          <button
            type="button"
            onClick={() => {
              void profile.refetch()
              void quests.refetch()
            }}
            className="font-semibold text-slate-100 hover:underline"
          >
            Retry
          </button>
        </div>
      )}

      <section aria-labelledby="quests-heading">
        <h2 id="quests-heading" className="mb-4 text-sm font-semibold tracking-wide text-muted uppercase">
          Your quests
        </h2>

        {quests.isPending ? (
          <QuestListSkeleton />
        ) : quests.data && quests.data.length > 0 ? (
          <div className="space-y-4">
            {quests.data.map((q) => (
              <QuestCard key={q.id} quest={q} highlight={q.id === freshId} />
            ))}
          </div>
        ) : (
          !quests.isError && (
            <div className="rounded-2xl border border-dashed border-panel-edge p-10 text-center">
              <p className="text-4xl" aria-hidden="true">
                🗺️
              </p>
              <h3 className="mt-3 text-lg font-bold">No quests yet</h3>
              <p className="mx-auto mt-1 max-w-sm text-sm text-muted">
                Got something big and scary to learn? Tell us, and we’ll turn it into small wins worth coins and XP.
              </p>
              <button
                type="button"
                onClick={() => setDialogOpen(true)}
                className="mt-5 rounded-xl bg-xp px-4 py-2.5 font-bold text-white hover:bg-xp/90"
              >
                Forge your first quest
              </button>
            </div>
          )
        )}
      </section>

      <QuestGeneratorDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        onCreated={(q) => {
          setFreshId(q.id)
          setDialogOpen(false)
          window.scrollTo({ top: 0, behavior: 'smooth' })
        }}
      />
    </main>
  )
}
