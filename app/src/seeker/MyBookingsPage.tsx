import { useEffect, useState } from 'react'
import { api, ApiError, type Booking } from '../lib/api'
import { currentUser, type AuthedUser } from '../lib/auth'
import { LoginPage } from '../auth/LoginPage'

const OPEN_STATUSES = new Set(['REQUESTED', 'CONFIRMED'])

export default function MyBookingsPage() {
  const [user, setUser] = useState<AuthedUser | null>(currentUser())

  if (!user || user.role !== 'SEEKER') {
    return (
      <div className="page">
        <h2>My bookings</h2>
        <LoginPage role="SEEKER" onSignedIn={setUser} />
      </div>
    )
  }

  return <BookingsList />
}

function BookingsList() {
  const [bookings, setBookings] = useState<Booking[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [now, setNow] = useState(Date.now())

  async function load() {
    try {
      setBookings(await api.get<Booking[]>('/bookings/mine'))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    }
  }

  useEffect(() => {
    void load()
  }, [])

  // Live countdown, ticking every 30s - no need for anything finer for a multi-hour/minute hold window.
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 30_000)
    return () => clearInterval(t)
  }, [])

  async function cancel(id: number) {
    try {
      await api.del(`/bookings/${id}`)
      void load()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    }
  }

  return (
    <div className="page">
      <h2>My bookings</h2>
      {error && <p className="bad">{error}</p>}
      {bookings === null && <p className="muted">Loading…</p>}
      {bookings?.length === 0 && <p className="muted">No bookings yet. <a href="/search">Find a PG</a> to request a visit.</p>}
      {bookings?.map((b) => {
        const open = OPEN_STATUSES.has(b.status)
        const msLeft = new Date(b.holdExpiresAt).getTime() - now
        return (
          <div key={b.id} className="card">
            <div className="row" style={{ justifyContent: 'space-between' }}>
              <strong>Bed #{b.bedId}</strong>
              <span className="muted">{b.status.toLowerCase()}</span>
            </div>
            <p className="muted">Visit: {new Date(b.visitSlot).toLocaleString()}</p>
            {open && (
              <p className="muted">{msLeft > 0 ? `Hold expires in ${formatCountdown(msLeft)}` : 'Hold expiring…'}</p>
            )}
            {open && <button onClick={() => cancel(b.id)}>Cancel</button>}
          </div>
        )
      })}
    </div>
  )
}

function formatCountdown(ms: number): string {
  const totalMinutes = Math.floor(ms / 60_000)
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  if (hours > 0) return `${hours}h ${minutes}m`
  return `${minutes}m`
}
