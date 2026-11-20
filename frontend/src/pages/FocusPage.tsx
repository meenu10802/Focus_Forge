import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { problemOf } from '../api/client'
import type { CompletionResult, QuestTask } from '../api/types'
import { useAbandonFocus, useActiveSession, useCompleteTask, useQuest, useStartFocus } from '../hooks/useFocus'

function useNow(active: boolean) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!active) return
    const id = setInterval(() => setNow(Date.now()), 250)
    return () => clearInterval(id)
  }, [active])
  return now
}

const fmt = (totalSeconds: number) => {
  const s = Math.max(0, Math.round(totalSeconds))
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
}

function TimerRing({ remaining, total, running }: { remaining: number; total: number; running: boolean }) {
  const r = 110
  const c = 2 * Math.PI * r
  const progress = total > 0 ? Math.min(1, Math.max(0, 1 - remaining / total)) : 0
  const done = running && remaining <= 0
  return (
    <div className="relative mx-auto size-64 sm:size-72">
      <svg viewBox="0 0 260 260" className="size-full -rotate-90" aria-hidden="true">
        <circle cx="130" cy="130" r={r} fill="none" strokeWidth="14" className="stroke-panel-edge" />
        <circle
          cx="130"
          cy="130"
          r={r}
          fill="none"
          strokeWidth="14"
          strokeLinecap="round"
          strokeDasharray={c}
          strokeDashoffset={c * (1 - progress)}
          className={`transition-[stroke-dashoffset] duration-300 ${done ? 'stroke-hp' : 'stroke-xp'}`}
        />
      </svg>
      <div className="absolute inset-0 grid place-items-center text-center">
        <div>
          <div className="text-6xl font-black tabular-nums sm:text-7xl" role="timer" aria-live="off">
            {fmt(remaining)}
          </div>
          <div className="mt-1 text-sm font-semibold tracking-wide text-muted uppercase">
            {done ? 'Time’s up — claim it!' : running ? 'Focusing' : 'Ready'}
          </div>
        </div>
      </div>
    </div>
  )
}

function Rewards({ task }: { task: QuestTask }) {
  return (
    <div className="flex flex-wrap justify-center gap-3 text-sm font-bold">
      <span className="rounded-lg bg-panel px-3 py-1.5 text-coin">🪙 +{task.coins}</span>
      <span className="rounded-lg bg-panel px-3 py-1.5 text-xp">+{task.xp} XP</span>
      <span className="rounded-lg bg-panel px-3 py-1.5 text-hp">❤ +{task.hpRestore} HP</span>
    </div>
  )
}

function Victory({ result, questId, nextTask }: { result: CompletionResult; questId: string; nextTask?: QuestTask }) {
  const navigate = useNavigate()
  return (
    <div className="text-center" role="status">
      <div className="text-6xl" aria-hidden="true">
        {result.questCompleted ? '🏆' : '⚔️'}
      </div>
      <h2 className="mt-4 text-3xl font-black">{result.questCompleted ? 'Quest complete!' : 'Micro-quest cleared!'}</h2>
      <p className="mt-2 text-muted">You focused for {result.focusMinutes} min. Loot incoming…</p>
      <div className="mt-6 flex flex-wrap justify-center gap-3 text-lg font-black">
        <span className="rounded-xl bg-coin/10 px-4 py-2 text-coin">🪙 +{result.coinsEarned}</span>
        <span className="rounded-xl bg-xp/10 px-4 py-2 text-xp">+{result.xpEarned} XP</span>
        <span className="rounded-xl bg-hp/10 px-4 py-2 text-hp">❤ +{result.hpRestored}</span>
      </div>
      <div className="mt-8 flex flex-wrap justify-center gap-3">
        {nextTask && (
          <button
            type="button"
            onClick={() => navigate(`/focus/${questId}/${nextTask.id}`)}
            className="rounded-xl bg-xp px-5 py-3 font-bold text-white hover:bg-xp/90"
          >
            Next: {nextTask.title} →
          </button>
        )}
        <Link to="/" className="rounded-xl border border-panel-edge px-5 py-3 font-semibold hover:bg-panel">
          Back to quests
        </Link>
      </div>
    </div>
  )
}

export function FocusPage() {
  const { questId = '', taskId = '' } = useParams()
  const quest = useQuest(questId)
  const active = useActiveSession()
  const start = useStartFocus(questId)
  const complete = useCompleteTask(questId)
  const abandon = useAbandonFocus(questId)
  const [confirmAbandon, setConfirmAbandon] = useState(false)
  const [result, setResult] = useState<CompletionResult | null>(null)

  // reset per-task UI state when navigating to the next task
  useEffect(() => {
    setResult(null)
    setConfirmAbandon(false)
    start.reset()
    complete.reset()
    abandon.reset()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [taskId])

  const task = quest.data?.tasks.find((t) => t.id === taskId)
  const session = active.data
  const runningHere = session?.taskId === taskId
  const busyElsewhere = session && !runningHere

  const now = useNow(runningHere)
  const total = (task?.minutes ?? 0) * 60
  const remaining = runningHere ? total - (now - Date.parse(session.startedAt)) / 1000 : total

  useEffect(() => {
    document.title = runningHere ? `${fmt(remaining)} · FocusForge` : 'FocusForge'
    return () => {
      document.title = 'FocusForge'
    }
  }, [runningHere, remaining])

  const error = start.error ?? complete.error ?? abandon.error

  if (quest.isPending || active.isPending) {
    return <main className="mx-auto max-w-xl px-4 py-16 text-center text-muted">Loading your quest…</main>
  }
  if (quest.isError || !task) {
    return (
      <main className="mx-auto max-w-xl px-4 py-16 text-center">
        <p className="text-lg">That quest step couldn’t be found.</p>
        <Link to="/" className="mt-4 inline-block font-semibold text-xp-glow hover:underline">
          ← Back to quests
        </Link>
      </main>
    )
  }

  const nextTask = quest.data.tasks.find((t) => t.id !== taskId && t.status !== 'COMPLETED')

  return (
    <main className="mx-auto max-w-xl px-4 py-8">
      <Link to="/" className="text-sm font-semibold text-muted hover:text-slate-200">
        ← {quest.data.title}
      </Link>

      <section className="mt-6 rounded-2xl border border-panel-edge bg-panel/60 p-6 sm:p-8">
        {result ? (
          <Victory result={result} questId={questId} nextTask={nextTask} />
        ) : task.status === 'COMPLETED' ? (
          <div className="text-center">
            <div className="text-5xl" aria-hidden="true">
              ✅
            </div>
            <h1 className="mt-3 text-2xl font-black">{task.title}</h1>
            <p className="mt-2 text-muted">Already conquered. Nice.</p>
            <Link to="/" className="mt-6 inline-block rounded-xl border border-panel-edge px-5 py-3 font-semibold hover:bg-panel">
              Back to quests
            </Link>
          </div>
        ) : (
          <>
            <div className="text-center">
              <p className="text-sm font-semibold tracking-wide text-xp-glow uppercase">
                Step {task.position} of {quest.data.tasks.length} · {task.difficulty}
              </p>
              <h1 className="mt-2 text-2xl font-black sm:text-3xl">{task.title}</h1>
              {task.description && <p className="mx-auto mt-2 max-w-md text-muted">{task.description}</p>}
            </div>

            <div className="my-8">
              <TimerRing remaining={remaining} total={total} running={runningHere} />
            </div>

            <Rewards task={task} />

            {busyElsewhere && (
              <p className="mt-6 rounded-xl bg-amber-400/10 px-4 py-3 text-center text-sm text-amber-200">
                You’re already focusing on “{session.taskTitle}”.{' '}
                <Link to={`/focus/${session.questId}/${session.taskId}`} className="font-semibold underline">
                  Go there
                </Link>
              </p>
            )}

            {error && (
              <p role="alert" className="mt-6 rounded-xl bg-hp-low/10 px-4 py-3 text-center text-sm text-hp-low">
                {problemOf(error).message}
              </p>
            )}

            <div className="mt-8 flex flex-col gap-3">
              {!runningHere ? (
                <>
                  <button
                    type="button"
                    disabled={start.isPending || !!busyElsewhere}
                    onClick={() => start.mutate(task.id)}
                    className="rounded-xl bg-xp px-5 py-4 text-lg font-black text-white shadow-lg shadow-xp/20 hover:bg-xp/90 disabled:opacity-50"
                  >
                    {start.isPending ? 'Starting…' : `▶ Start ${task.minutes}-minute focus`}
                  </button>
                  <button
                    type="button"
                    disabled={complete.isPending}
                    onClick={() => complete.mutate(task.id, { onSuccess: setResult })}
                    className="rounded-xl px-5 py-3 text-sm font-semibold text-muted hover:bg-panel hover:text-slate-200 disabled:opacity-50"
                  >
                    {complete.isPending ? 'Claiming…' : 'Already did it? Mark complete'}
                  </button>
                </>
              ) : (
                <>
                  <button
                    type="button"
                    disabled={complete.isPending}
                    onClick={() => complete.mutate(task.id, { onSuccess: setResult })}
                    className={`rounded-xl px-5 py-4 text-lg font-black text-ink shadow-lg disabled:opacity-60 ${
                      remaining <= 0 ? 'animate-pulse bg-hp shadow-hp/30' : 'bg-hp/90 hover:bg-hp'
                    }`}
                  >
                    {complete.isPending ? 'Claiming loot…' : '✓ Complete & claim loot'}
                  </button>

                  {!confirmAbandon ? (
                    <button
                      type="button"
                      onClick={() => setConfirmAbandon(true)}
                      className="rounded-xl px-5 py-3 text-sm font-semibold text-muted hover:bg-panel hover:text-hp-low"
                    >
                      Abandon this session
                    </button>
                  ) : (
                    <div className="rounded-xl border border-hp-low/40 bg-hp-low/10 p-4 text-center">
                      <p className="text-sm">Abandoning costs 10 HP. You can pick this task up again later.</p>
                      <div className="mt-3 flex justify-center gap-2">
                        <button
                          type="button"
                          onClick={() => setConfirmAbandon(false)}
                          className="rounded-lg px-4 py-2 text-sm font-semibold hover:bg-panel"
                        >
                          Keep going
                        </button>
                        <button
                          type="button"
                          disabled={abandon.isPending}
                          onClick={() => abandon.mutate(task.id, { onSuccess: () => setConfirmAbandon(false) })}
                          className="rounded-lg bg-hp-low px-4 py-2 text-sm font-bold text-white disabled:opacity-60"
                        >
                          {abandon.isPending ? 'Abandoning…' : 'Abandon (−10 HP)'}
                        </button>
                      </div>
                    </div>
                  )}
                </>
              )}
            </div>
          </>
        )}
      </section>
    </main>
  )
}
