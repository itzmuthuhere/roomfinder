import { useEffect, useState } from 'react'
import OwnerPage from './owner/OwnerPage'

type Ping = { ok: boolean; beds: number; pgs: number }

export default function App() {
  const [ping, setPing] = useState<Ping | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetch('/api/ping')
      .then((r) => (r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`))))
      .then(setPing)
      .catch((e: Error) => setError(e.message))
  }, [])

  return (
    <div className="shell">
      <nav>
        <strong>RoomFinder</strong>
        <span className="muted">
          {error ? `API down: ${error}` : ping ? `${ping.pgs} PGs · ${ping.beds} beds` : 'connecting…'}
        </span>
      </nav>
      <OwnerPage />
    </div>
  )
}
