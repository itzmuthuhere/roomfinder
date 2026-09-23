import { useEffect, useState } from 'react'
import OwnerPage from './owner/OwnerPage'
import SearchPage from './seeker/SearchPage'
import ListingPage from './seeker/ListingPage'

type Ping = { ok: boolean; beds: number; pgs: number }

/**
 * No router library yet (one-line justification for not adding one: this app has exactly three
 * screens and full-page navigation between them is fine for now) - a plain pathname switch plus
 * ordinary <a> links. Revisit with a real router once there are enough screens for client-side
 * navigation to matter.
 */
function currentRoute(): { name: 'owner' } | { name: 'search' } | { name: 'listing'; pgId: number } {
  const path = window.location.pathname
  const listingMatch = path.match(/^\/pgs\/(\d+)$/)
  if (listingMatch) return { name: 'listing', pgId: Number(listingMatch[1]) }
  if (path === '/search') return { name: 'search' }
  return { name: 'owner' }
}

export default function App() {
  const [ping, setPing] = useState<Ping | null>(null)
  const [error, setError] = useState<string | null>(null)
  const route = currentRoute()

  useEffect(() => {
    fetch('/api/ping')
      .then((r) => (r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`))))
      .then(setPing)
      .catch((e: Error) => setError(e.message))
  }, [])

  return (
    <div className="shell">
      <nav>
        <span className="row">
          <strong>RoomFinder</strong>
          <a href="/search">Find a PG</a>
          <a href="/">Owner</a>
        </span>
        <span className="muted">
          {error ? `API down: ${error}` : ping ? `${ping.pgs} PGs · ${ping.beds} beds` : 'connecting…'}
        </span>
      </nav>
      {route.name === 'search' && <SearchPage />}
      {route.name === 'listing' && <ListingPage pgId={route.pgId} />}
      {route.name === 'owner' && <OwnerPage />}
    </div>
  )
}
