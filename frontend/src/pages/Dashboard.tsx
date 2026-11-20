import { GatewayStatus } from '../components/GatewayStatus'

const upcoming = [
  { phase: 1, title: 'Identity & HUD', body: 'Register, log in, and see live HP / XP / level from identity-rpg-service.' },
  { phase: 2, title: 'AI Quest Generator', body: '“What do you need to conquer today?” → a questline of 10-minute micro-tasks.' },
  { phase: 3, title: 'Focus Loop', body: 'Timer, idempotent task completion, coins minted via Kafka fan-out.' },
  { phase: 4, title: 'Reward Store', body: 'Spend Focus Coins on real-world treats with race-safe inventory.' },
]

export function Dashboard() {
  return (
    <main className="mx-auto max-w-5xl px-4 py-8">
      <div className="mb-8 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-black tracking-tight">FocusForge</h1>
          <p className="mt-1 text-muted">Turn overwhelming goals into micro-quests. Earn coins. Spend them on real life.</p>
        </div>
        <GatewayStatus />
      </div>

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
