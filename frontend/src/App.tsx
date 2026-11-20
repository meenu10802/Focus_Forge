import { Navigate, Route, Routes } from 'react-router-dom'
import { HudBar } from './components/HudBar'
import { Dashboard } from './pages/Dashboard'
import type { PlayerStats } from './api/types'

// Placeholder until identity-rpg-service serves /api/profile/me (Phase 1).
const demoStats: PlayerStats = {
  displayName: 'Adventurer',
  hp: 72,
  maxHp: 100,
  xp: 340,
  xpForNextLevel: 520,
  level: 3,
  coins: 1250,
  streakDays: 4,
}

export default function App() {
  return (
    <>
      <HudBar stats={demoStats} />
      <Routes>
        <Route path="/" element={<Dashboard />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  )
}
