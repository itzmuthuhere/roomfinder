import { useEffect, useState } from 'react'
import { api, ApiError, type OwnerBooking } from '../lib/api'

export function VisitRequests({ pgId, onChanged }: { pgId: number; onChanged: () => void }) {
  const [requests, setRequests] = useState<OwnerBooking[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState<number | null>(null)

  async function load() {
    try {
      setRequests(await api.get<OwnerBooking[]>(`/bookings/pg/${pgId}`))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    }
  }

  useEffect(() => {
    void load()
  }, [pgId])

  async function act(id: number, action: 'accept' | 'decline') {
    setBusy(id)
    setError(null)
    try {
      await api.patch(`/bookings/${id}/${action}`, {})
      await load()
      onChanged()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(null)
    }
  }

  if (requests.length === 0 && !error) return null

  return (
    <section className="card">
      <h4>Visit requests</h4>
      {error && <p className="bad">{error}</p>}
      {requests.map((r) => (
        <div key={r.id} className="row" style={{ justifyContent: 'space-between', borderBottom: '1px solid var(--line)', padding: '8px 0' }}>
          <span>Bed #{r.bedId} · {r.seekerPhone}</span>
          <span className="muted">{new Date(r.visitSlot).toLocaleString()}</span>
          <span className="muted">{r.status.toLowerCase()}</span>
          {r.status === 'REQUESTED' && (
            <span className="row">
              <button disabled={busy === r.id} onClick={() => act(r.id, 'accept')}>Accept</button>
              <button disabled={busy === r.id} onClick={() => act(r.id, 'decline')}>Decline</button>
            </span>
          )}
          {r.status === 'CONFIRMED' && (
            <button disabled={busy === r.id} onClick={() => act(r.id, 'decline')}>Decline</button>
          )}
        </div>
      ))}
    </section>
  )
}
