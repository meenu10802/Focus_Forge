import { useEffect, useRef, useState, type FormEvent } from 'react'
import { problemOf } from '../api/client'
import type { Quest } from '../api/types'
import { useGenerateQuest } from '../hooks/useQuests'

const EXAMPLES = [
  'Learn Spring Security and JWT in 3 days',
  'Understand Kafka consumer groups',
  'Revise linear algebra for Friday’s exam',
]

const MAX = 300

function ForgingSkeleton() {
  return (
    <div aria-live="polite" aria-busy="true" className="space-y-3">
      <p className="text-sm text-muted">
        <span className="mr-2 inline-block animate-spin">⚙️</span>
        Forging your questline into bite-sized steps…
      </p>
      {Array.from({ length: 5 }, (_, i) => (
        <div key={i} className="flex items-center gap-3 rounded-lg border border-panel-edge p-3">
          <div className="size-5 shrink-0 animate-pulse rounded-full bg-panel-edge" />
          <div className="flex-1 space-y-2">
            <div className="h-3 animate-pulse rounded bg-panel-edge" style={{ width: `${80 - i * 9}%` }} />
            <div className="h-2 w-1/3 animate-pulse rounded bg-panel-edge/70" />
          </div>
        </div>
      ))}
    </div>
  )
}

interface Props {
  open: boolean
  onClose: () => void
  onCreated: (quest: Quest) => void
}

export function QuestGeneratorDialog({ open, onClose, onCreated }: Props) {
  const ref = useRef<HTMLDialogElement>(null)
  const [goal, setGoal] = useState('')
  const generate = useGenerateQuest()

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  function close() {
    if (generate.isPending) return // don't orphan an in-flight generation
    generate.reset()
    onClose()
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    const trimmed = goal.trim()
    if (trimmed.length < 3) return
    const quest = await generate.mutateAsync(trimmed).catch(() => null)
    if (quest) {
      setGoal('')
      generate.reset()
      onCreated(quest)
    }
  }

  const problem = generate.error ? problemOf(generate.error) : null

  return (
    <dialog
      ref={ref}
      onCancel={(e) => {
        e.preventDefault()
        close()
      }}
      onClick={(e) => e.target === ref.current && close()}
      aria-labelledby="quest-gen-title"
      className="m-auto w-[min(100%-2rem,36rem)] rounded-2xl border border-panel-edge bg-panel p-0 text-slate-100 shadow-2xl backdrop:bg-black/70 backdrop:backdrop-blur-sm"
    >
      <form onSubmit={onSubmit} className="space-y-4 p-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 id="quest-gen-title" className="text-xl font-black">
              What do you need to conquer today?
            </h2>
            <p className="mt-1 text-sm text-muted">Describe the big scary goal. We’ll break it into 10–15 minute quests.</p>
          </div>
          <button
            type="button"
            onClick={close}
            disabled={generate.isPending}
            aria-label="Close"
            className="rounded-lg px-2 py-1 text-muted hover:bg-ink hover:text-slate-200 disabled:opacity-40"
          >
            ✕
          </button>
        </div>

        {generate.isPending ? (
          <ForgingSkeleton />
        ) : (
          <>
            <label className="block">
              <span className="sr-only">Your goal</span>
              <textarea
                autoFocus
                rows={3}
                maxLength={MAX}
                value={goal}
                onChange={(e) => setGoal(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) e.currentTarget.form?.requestSubmit()
                }}
                placeholder="e.g. Learn Spring Security and JWT in 3 days"
                className="w-full resize-none rounded-lg border border-panel-edge bg-ink px-3 py-2 text-slate-100 outline-none placeholder:text-slate-500 focus:border-xp focus:ring-2 focus:ring-xp/30"
              />
              <span className="mt-1 block text-right text-xs text-muted tabular-nums">
                {goal.length}/{MAX}
              </span>
            </label>

            <div className="flex flex-wrap gap-2">
              {EXAMPLES.map((ex) => (
                <button
                  key={ex}
                  type="button"
                  onClick={() => setGoal(ex)}
                  className="rounded-full border border-panel-edge px-3 py-1 text-xs text-muted hover:border-xp hover:text-slate-200"
                >
                  {ex}
                </button>
              ))}
            </div>

            {problem && (
              <p role="alert" className="rounded-lg bg-hp-low/10 px-3 py-2 text-sm text-hp-low">
                {problem.errors?.goal ? `Goal ${problem.errors.goal}` : problem.message}
              </p>
            )}
          </>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <button
            type="submit"
            disabled={generate.isPending || goal.trim().length < 3}
            className="rounded-lg bg-xp px-4 py-2.5 font-bold text-white transition hover:bg-xp/90 disabled:opacity-50"
          >
            {generate.isPending ? 'Forging…' : 'Forge my quest ⚔️'}
          </button>
        </div>
      </form>
    </dialog>
  )
}
