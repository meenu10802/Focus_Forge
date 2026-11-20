import { problemOf } from '../api/client'
import { useAuth } from '../auth/authContext'
import { useDashboard } from '../hooks/useDashboard'

const upcoming = [
  { phase: 2, title: 'AI Quest Generator', body: '“What do you need to conquer today?” → a questline of 10-minute micro-tasks.' },
  { phase: 3, title: 'Focus Loop', body: 'Timer, idempotent task completion, coins minted via Kafka fan-out.' },
  { phase: 4, title: 'Reward Store', body: 'Spend Focus Coins on real-world treats with race-safe inventory.' },
  { phase: 5, title: 'Live Dopamine Hits', body: 'Server-Sent Events animate your HUD the moment a reward lands.' },
]

export function Dashboard() {
  const { user } = useAuth()
  const { data, error, refetch } = useDashboard()
  const name = data?.displayName ?? user?.displayName ?? 'Adventurer'

  return (
    <main className="mx-auto max-w-5xl px-4 py-8">
      <div className="mb-8">
        <h1 className="text-3xl font-black tracking-tight">Welcome, {name}</h1>
        <p className="mt-1 text-muted">
          {data
            ? `Level ${data.level} · ${data.xpForNextLevel - data.xp} XP to level ${data.level + 1}`
            : 'Loading your stats…'}
          {data?.role === 'ADMIN' && (
            <span className="ml-2 rounded bg-xp-glow/15 px-1.5 py-0.5 text-xs font-bold text-xp-glow">ADMIN</span>
          )}
        </p>
      </div>

      {error && (
        <div role="alert" className="mb-6 flex items-center justify-between gap-4 rounded-xl border border-hp-low/40 bg-hp-low/10 px-4 py-3 text-sm">
          <span>Couldn’t load your profile: {problemOf(error).message}</span>
          <button type="button" onClick={() => refetch()} className="font-semibold text-slate-100 hover:underline">
            Retry
          </button>
        </div>
      )}

      <section aria-labelledby="roadmap" className="grid gap-3 sm:grid-cols-2">
        <h2 id="roadmap" className="sr-only">
          Coming next
        </h2>
        {upcoming.map((item) => (
          <article key={item.phase} className="rounded-xl border border-panel-edge bg-panel p-4">
            <p className="text-xs font-semibold tracking-wide text-xp-glow uppercase">Phase {item.phase}</p>
            <h3 className="mt-1 font-bold">{item.title}</h3>
            <p className="mt-1 text-sm text-muted">{item.body}</p>
          </article>
        ))}
      </section>
    </main>
  )
}
