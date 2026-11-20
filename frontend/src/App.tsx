import { NavLink, Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/authContext'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { HudBar } from './components/HudBar'
import { useDashboard } from './hooks/useDashboard'
import { useWallet } from './hooks/useWallet'
import { AuthPage } from './pages/AuthPage'
import { Dashboard } from './pages/Dashboard'
import { FocusPage } from './pages/FocusPage'
import { StorePage } from './pages/StorePage'

const tabClass = ({ isActive }: { isActive: boolean }) =>
  `rounded-lg px-4 py-2 text-sm font-bold transition ${
    isActive ? 'bg-panel-edge text-white' : 'text-muted hover:bg-panel hover:text-slate-200'
  }`

/** Authenticated shell: the HUD and tabs stay pinned above every in-game page. */
function GameLayout() {
  const { logout } = useAuth()
  const { data } = useDashboard()
  const wallet = useWallet()
  return (
    <>
      <HudBar stats={data} coins={wallet.data?.balance} onLogout={logout} />
      <nav aria-label="Main" className="border-b border-panel-edge bg-ink">
        <div className="mx-auto flex max-w-5xl gap-1 px-4 py-2">
          <NavLink to="/" end className={tabClass}>
            ⚔️ Quests
          </NavLink>
          <NavLink to="/store" className={tabClass}>
            🛍️ Reward Store
          </NavLink>
        </div>
      </nav>
      <Outlet />
    </>
  )
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<AuthPage mode="login" />} />
      <Route path="/register" element={<AuthPage mode="register" />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<GameLayout />}>
          <Route path="/" element={<Dashboard />} />
          <Route path="/focus/:questId/:taskId" element={<FocusPage />} />
          <Route path="/store" element={<StorePage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
