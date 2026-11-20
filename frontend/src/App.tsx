import { Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/authContext'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { HudBar } from './components/HudBar'
import { useDashboard } from './hooks/useDashboard'
import { AuthPage } from './pages/AuthPage'
import { Dashboard } from './pages/Dashboard'

/** Authenticated shell: the HUD stays pinned above every in-game page. */
function GameLayout() {
  const { logout } = useAuth()
  const { data } = useDashboard()
  return (
    <>
      <HudBar stats={data} onLogout={logout} />
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
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
