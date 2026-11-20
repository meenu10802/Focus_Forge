import type { PlayerStats } from '../api/types'

interface StatBarProps {
  label: string
  value: number
  max: number
  fillClass: string
}

function StatBar({ label, value, max, fillClass }: StatBarProps) {
  const pct = max > 0 ? Math.min(100, Math.max(0, (value / max) * 100)) : 0
  return (
    <div className="min-w-0 flex-1">
      <div className="mb-1 flex items-baseline justify-between text-xs font-semibold tracking-wide text-muted uppercase">
        <span>{label}</span>
        <span className="tabular-nums text-slate-200">
          {value} / {max}
        </span>
      </div>
      <div
        className="h-3 overflow-hidden rounded-full bg-panel-edge"
        role="progressbar"
        aria-label={label}
        aria-valuenow={value}
        aria-valuemin={0}
        aria-valuemax={max}
      >
        <div
          className={`h-full rounded-full transition-[width] duration-700 ease-out ${fillClass}`}
          style={{ width: `${pct}%` }}
        />
      </div>
    </div>
  )
}

export function HudBar({ stats }: { stats: PlayerStats }) {
  const hpLow = stats.hp / stats.maxHp < 0.3
  return (
    <header className="sticky top-0 z-10 border-b border-panel-edge bg-panel/90 backdrop-blur">
      <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-4 px-4 py-3">
        <div
          className="grid size-12 shrink-0 place-items-center rounded-xl border-2 border-xp-glow bg-ink text-lg font-black text-xp-glow"
          title={`Level ${stats.level}`}
        >
          {stats.level}
        </div>

        <div className="flex min-w-0 flex-1 basis-64 gap-4">
          <StatBar
            label="Health"
            value={stats.hp}
            max={stats.maxHp}
            fillClass={hpLow ? 'bg-hp-low' : 'bg-gradient-to-r from-hp-low via-amber-400 to-hp'}
          />
          <StatBar
            label="XP"
            value={stats.xp}
            max={stats.xpForNextLevel}
            fillClass="bg-gradient-to-r from-xp to-xp-glow"
          />
        </div>

        <div className="flex shrink-0 items-center gap-3 text-sm font-bold">
          <span className="rounded-lg bg-ink px-3 py-1.5 text-orange-400" title="Daily streak">
            🔥 {stats.streakDays}
          </span>
          <span className="rounded-lg bg-ink px-3 py-1.5 tabular-nums text-coin" title="Focus Coins">
            🪙 {stats.coins.toLocaleString()}
          </span>
        </div>
      </div>
    </header>
  )
}
