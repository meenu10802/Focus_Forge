import { useEffect, useRef, useState, type FormEvent } from 'react'
import { problemOf } from '../api/client'
import type { Reward, RewardInput } from '../api/types'
import { useSaveReward } from '../hooks/useStore'

const EMOJIS = ['🎁', '🎮', '🍕', '🍛', '☕', '🧋', '📺', '🎬', '😴', '📚', '🛍️', '🍦']

const empty: RewardInput = { name: '', description: '', emoji: '🎁', price: 50, stock: 10 }

const inputClass =
  'w-full rounded-xl border border-panel-edge bg-ink px-4 py-3 text-base text-slate-100 outline-none ' +
  'placeholder:text-slate-500 focus:border-xp focus:ring-2 focus:ring-xp/30 aria-[invalid=true]:border-hp-low'

interface Props {
  open: boolean
  editing: Reward | null
  onClose: () => void
}

export function RewardFormDialog({ open, editing, onClose }: Props) {
  const ref = useRef<HTMLDialogElement>(null)
  const [form, setForm] = useState<RewardInput>(empty)
  const save = useSaveReward()

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) {
      setForm(
        editing
          ? { name: editing.name, description: editing.description, emoji: editing.emoji, price: editing.price, stock: editing.stock }
          : empty,
      )
      save.reset()
      dialog.showModal()
    }
    if (!open && dialog.open) dialog.close()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, editing])

  const problem = save.error ? problemOf(save.error) : null
  const errors = problem?.errors ?? {}

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    const ok = await save.mutateAsync({ id: editing?.id, input: form }).then(
      () => true,
      () => false,
    )
    if (ok) onClose()
  }

  const set = <K extends keyof RewardInput>(k: K, v: RewardInput[K]) => setForm((f) => ({ ...f, [k]: v }))

  return (
    <dialog
      ref={ref}
      onCancel={(e) => {
        e.preventDefault()
        onClose()
      }}
      onClick={(e) => e.target === ref.current && onClose()}
      aria-labelledby="reward-form-title"
      className="m-auto w-[min(100%-2rem,32rem)] rounded-2xl border border-panel-edge bg-panel p-0 text-slate-100 shadow-2xl backdrop:bg-black/70 backdrop:backdrop-blur-sm"
    >
      <form onSubmit={onSubmit} className="space-y-5 p-6" noValidate>
        <div className="flex items-start justify-between gap-4">
          <h2 id="reward-form-title" className="text-xl font-black">
            {editing ? 'Edit reward' : 'Create a custom reward'}
          </h2>
          <button type="button" onClick={onClose} aria-label="Close" className="rounded-lg px-2 py-1 text-muted hover:bg-ink">
            ✕
          </button>
        </div>

        <fieldset>
          <legend className="mb-2 text-base font-semibold text-slate-200">Icon</legend>
          <div className="flex flex-wrap gap-2">
            {EMOJIS.map((em) => (
              <button
                key={em}
                type="button"
                aria-pressed={form.emoji === em}
                onClick={() => set('emoji', em)}
                className={`grid size-11 place-items-center rounded-xl border text-2xl ${
                  form.emoji === em ? 'border-xp-glow bg-xp-glow/10' : 'border-panel-edge hover:border-slate-500'
                }`}
              >
                {em}
              </button>
            ))}
          </div>
        </fieldset>

        <label className="block">
          <span className="mb-2 block text-base font-semibold text-slate-200">Reward</span>
          <input
            className={inputClass}
            value={form.name}
            maxLength={60}
            placeholder="e.g. Bubble tea run"
            aria-invalid={errors.name ? true : undefined}
            onChange={(e) => set('name', e.target.value)}
          />
          {errors.name && <span className="mt-1 block text-sm text-hp-low">{errors.name}</span>}
        </label>

        <label className="block">
          <span className="mb-2 block text-base font-semibold text-slate-200">Description (optional)</span>
          <input
            className={inputClass}
            value={form.description}
            maxLength={200}
            onChange={(e) => set('description', e.target.value)}
          />
        </label>

        <div className="grid grid-cols-2 gap-4">
          <label className="block">
            <span className="mb-2 block text-base font-semibold text-slate-200">Price (coins)</span>
            <input
              className={inputClass}
              type="number"
              inputMode="numeric"
              min={1}
              max={100000}
              value={form.price}
              aria-invalid={errors.price ? true : undefined}
              onChange={(e) => set('price', Number(e.target.value))}
            />
            {errors.price && <span className="mt-1 block text-sm text-hp-low">{errors.price}</span>}
          </label>
          <label className="block">
            <span className="mb-2 block text-base font-semibold text-slate-200">Stock</span>
            <input
              className={inputClass}
              type="number"
              inputMode="numeric"
              min={0}
              max={10000}
              value={form.stock}
              aria-invalid={errors.stock ? true : undefined}
              onChange={(e) => set('stock', Number(e.target.value))}
            />
            {errors.stock && <span className="mt-1 block text-sm text-hp-low">{errors.stock}</span>}
          </label>
        </div>

        {problem && !problem.errors && (
          <p role="alert" className="rounded-xl bg-hp-low/10 px-4 py-3 text-sm text-hp-low">
            {problem.message}
          </p>
        )}

        <div className="flex justify-end gap-2">
          <button type="button" onClick={onClose} className="rounded-xl px-4 py-2.5 font-semibold hover:bg-ink">
            Cancel
          </button>
          <button
            type="submit"
            disabled={save.isPending}
            className="rounded-xl bg-xp px-5 py-2.5 font-bold text-white hover:bg-xp/90 disabled:opacity-60"
          >
            {save.isPending ? 'Saving…' : editing ? 'Save changes' : 'Add to store'}
          </button>
        </div>
      </form>
    </dialog>
  )
}
