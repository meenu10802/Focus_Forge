import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { problemOf } from '../api/client'
import { useAuth } from '../auth/authContext'
import { GatewayStatus } from '../components/GatewayStatus'

type Mode = 'login' | 'register'

const inputClass =
  'w-full rounded-xl border border-panel-edge bg-ink px-4 py-3.5 text-lg text-slate-100 outline-none ' +
  'placeholder:text-slate-500 focus:border-xp focus:ring-2 focus:ring-xp/30 aria-[invalid=true]:border-hp-low'

function Field({ label, error, ...props }: { label: string; error?: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <label className="block">
      <span className="mb-2 block text-base font-semibold text-slate-200">{label}</span>
      <input className={inputClass} aria-invalid={error ? true : undefined} {...props} />
      {error && <span className="mt-1.5 block text-sm text-hp-low">{error}</span>}
    </label>
  )
}

export function AuthPage({ mode }: { mode: Mode }) {
  const { isAuthenticated, login, register } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/'

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  if (isAuthenticated) return <Navigate to={from} replace />

  const isRegister = mode === 'register'

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    setFieldErrors({})
    try {
      if (isRegister) await register({ email, password, displayName })
      else await login(email, password)
      navigate(from, { replace: true })
    } catch (err) {
      const problem = problemOf(err)
      setError(problem.message)
      setFieldErrors(problem.errors ?? {})
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="grid min-h-screen place-items-center px-4 py-10">
      <div className="w-full max-w-md">
        <div className="mb-8 text-center">
          <div className="mx-auto mb-4 grid size-16 place-items-center rounded-2xl border-2 border-xp-glow text-3xl">⚔️</div>
          <h1 className="text-3xl font-black tracking-tight sm:text-4xl">{isRegister ? 'Create your hero' : 'Welcome back'}</h1>
          <p className="mt-2 text-lg text-muted">
            {isRegister ? 'Start at level 1 with a full 100 HP.' : 'Your quests are waiting.'}
          </p>
        </div>

        <form onSubmit={onSubmit} noValidate className="space-y-5 rounded-2xl border border-panel-edge bg-panel p-6 sm:p-8">
          {isRegister && (
            <Field
              label="Hero name"
              autoComplete="nickname"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              error={fieldErrors.displayName}
              required
            />
          )}
          <Field
            label="Email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            error={fieldErrors.email}
            required
          />
          <Field
            label="Password"
            type="password"
            autoComplete={isRegister ? 'new-password' : 'current-password'}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={fieldErrors.password}
            placeholder={isRegister ? 'At least 8 characters' : undefined}
            required
          />

          {error && Object.keys(fieldErrors).length === 0 && (
            <p role="alert" className="rounded-xl bg-hp-low/10 px-4 py-3 text-base text-hp-low">
              {error}
            </p>
          )}

          <button
            type="submit"
            disabled={submitting}
            className="w-full rounded-xl bg-xp px-4 py-3.5 text-lg font-bold text-white transition hover:bg-xp/90 disabled:opacity-60"
          >
            {submitting ? 'One moment…' : isRegister ? 'Begin the quest' : 'Log in'}
          </button>
        </form>

        <p className="mt-6 text-center text-base text-muted">
          {isRegister ? 'Already a hero? ' : 'New here? '}
          <Link to={isRegister ? '/login' : '/register'} state={location.state} className="font-semibold text-xp-glow hover:underline">
            {isRegister ? 'Log in' : 'Create an account'}
          </Link>
        </p>
        <div className="mt-6 flex justify-center">
          <GatewayStatus />
        </div>
      </div>
    </main>
  )
}
