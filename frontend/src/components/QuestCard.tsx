import type { Difficulty, Quest, QuestTask } from '../api/types'

const difficultyStyle: Record<Difficulty, string> = {
  MICRO: 'bg-sky-400/10 text-sky-300',
  EASY: 'bg-hp/10 text-hp',
  MEDIUM: 'bg-amber-400/10 text-amber-300',
  HARD: 'bg-hp-low/10 text-hp-low',
}

function TaskRow({ task }: { task: QuestTask }) {
  const done = task.status === 'COMPLETED'
  return (
    <li className="flex gap-3 rounded-lg border border-panel-edge bg-ink/40 p-3">
      <span
        aria-hidden="true"
        title="Focus timer arrives in Phase 3"
        className={`mt-0.5 grid size-5 shrink-0 place-items-center rounded-full border-2 text-[10px] ${
          done ? 'border-hp bg-hp text-ink' : 'border-panel-edge'
        }`}
      >
        {done && '✓'}
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
          <span className={`font-semibold ${done ? 'text-muted line-through' : ''}`}>{task.title}</span>
          <span className={`rounded px-1.5 py-0.5 text-[10px] font-bold tracking-wide ${difficultyStyle[task.difficulty]}`}>
            {task.difficulty}
          </span>
        </div>
        {task.description && <p className="mt-1 text-sm text-muted">{task.description}</p>}
      </div>
      <div className="shrink-0 text-right text-xs leading-5 tabular-nums text-muted">
        <div>⏱ {task.minutes}m</div>
        <div className="text-coin">🪙 {task.coins}</div>
        <div className="text-xp">{task.xp} XP</div>
      </div>
    </li>
  )
}

export function QuestCard({ quest, highlight = false }: { quest: Quest; highlight?: boolean }) {
  const done = quest.tasks.filter((t) => t.status === 'COMPLETED').length
  const pct = quest.tasks.length ? Math.round((done / quest.tasks.length) * 100) : 0

  return (
    <article
      className={`rounded-2xl border bg-panel p-5 transition-shadow ${
        highlight ? 'border-xp-glow shadow-[0_0_0_3px_rgb(245_184_61/0.15)]' : 'border-panel-edge'
      }`}
    >
      <header className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="text-lg font-black">{quest.title}</h3>
          {!quest.title.includes(quest.goal) && (
            <p className="mt-0.5 truncate text-sm text-muted" title={quest.goal}>
              “{quest.goal}”
            </p>
          )}
        </div>
        {quest.source === 'FALLBACK' ? (
          <span
            className="rounded-full border border-amber-400/40 px-2.5 py-1 text-xs font-semibold text-amber-300"
            title="The AI was unavailable, so a built-in study template was used."
          >
            Offline template
          </span>
        ) : (
          <span className="rounded-full border border-xp/40 px-2.5 py-1 text-xs font-semibold text-xp">
            ✨ AI · {quest.provider}
          </span>
        )}
      </header>

      <dl className="mb-3 flex flex-wrap gap-x-5 gap-y-1 text-sm">
        <div className="flex gap-1.5">
          <dt className="text-muted">Steps done</dt>
          <dd className="font-semibold tabular-nums">
            {done}/{quest.tasks.length}
          </dd>
        </div>
        <div className="flex gap-1.5">
          <dt className="text-muted">Time</dt>
          <dd className="font-semibold tabular-nums">{quest.totalMinutes} min</dd>
        </div>
        <div className="flex gap-1.5">
          <dt className="text-muted">Loot</dt>
          <dd className="font-semibold tabular-nums text-coin">🪙 {quest.totalCoins}</dd>
        </div>
        <div className="flex gap-1.5">
          <dt className="sr-only">Experience</dt>
          <dd className="font-semibold tabular-nums text-xp">{quest.totalXp} XP</dd>
        </div>
      </dl>

      <div className="mb-4 h-1.5 overflow-hidden rounded-full bg-panel-edge" aria-hidden="true">
        <div className="h-full rounded-full bg-hp transition-[width] duration-700" style={{ width: `${pct}%` }} />
      </div>

      <ol className="space-y-2">
        {quest.tasks.map((t) => (
          <TaskRow key={t.id} task={t} />
        ))}
      </ol>
    </article>
  )
}
