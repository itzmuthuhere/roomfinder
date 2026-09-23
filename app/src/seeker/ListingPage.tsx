import { useEffect, useState } from 'react'
import { api, ApiError, type Bed, type Booking, type PgDetail } from '../lib/api'
import { currentUser, type AuthedUser } from '../lib/auth'
import { LoginPage } from '../auth/LoginPage'

export default function ListingPage({ pgId }: { pgId: number }) {
  const [detail, setDetail] = useState<PgDetail | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.get<PgDetail>(`/pgs/${pgId}`)
      .then(setDetail)
      .catch((e) => setError(e instanceof ApiError ? e.message : String(e)))
  }, [pgId])

  if (error) {
    return (
      <div className="page">
        <p className="bad">{error}</p>
        <a href="/search">Back to search</a>
      </div>
    )
  }
  if (!detail) {
    return <div className="page"><p className="muted">Loading…</p></div>
  }

  const { pg, beds, media } = detail
  const photos = media.filter((m) => m.kind === 'PHOTO')
  const video = media.find((m) => m.kind === 'VIDEO')

  const rooms = new Map<number, Bed[]>()
  for (const b of beds) rooms.set(b.roomId, [...(rooms.get(b.roomId) ?? []), b])

  return (
    <div className="page">
      <a href="/search" className="muted">← Back to search</a>
      <h2>{pg.name}</h2>
      <p className="muted">{pg.address} · {pg.area} · {pg.genderType.toLowerCase().replace('_', ' ')}</p>

      {photos.length > 0 && (
        <div className="photo-strip">
          {photos.map((p) => (
            <figure key={p.id} className="photo">
              <img src={p.url} alt="" loading="lazy" />
            </figure>
          ))}
        </div>
      )}
      {photos.length === 0 && <p className="muted">No photos yet.</p>}

      {video && (
        <div className="card">
          <strong>Walkthrough video</strong>
          <video src={video.url} controls playsInline width={320} />
        </div>
      )}

      <section className="card">
        <h3>Rooms</h3>
        {[...rooms.entries()].map(([roomId, roomBeds]) => {
          const vacant = roomBeds.filter((b) => b.state === 'VACANT').length
          const b0 = roomBeds[0]
          return (
            <div key={roomId} className="row" style={{ justifyContent: 'space-between', borderBottom: '1px solid var(--line)', padding: '8px 0' }}>
              <span>{b0.sharingType.toLowerCase()} sharing{b0.ac ? ' · AC' : ''}</span>
              <span>₹{b0.rentPerBed}/bed</span>
              <span className="muted">{vacant} of {roomBeds.length} vacant</span>
            </div>
          )
        })}
        {rooms.size === 0 && <p className="muted">No rooms listed yet.</p>}
      </section>

      <section className="card">
        <h3>Details</h3>
        <p>Food: {pg.foodIncluded == null ? 'Not specified' : pg.foodIncluded ? `Included${pg.foodType ? ` (${pg.foodType.toLowerCase()})` : ''}` : 'Not included'}</p>
        <p>Notice period: {pg.noticePeriodDays != null ? `${pg.noticePeriodDays} days` : 'Not specified'}</p>
      </section>

      <VisitRequestSection vacantBeds={beds.filter((b) => b.state === 'VACANT')} />
    </div>
  )
}

function VisitRequestSection({ vacantBeds }: { vacantBeds: Bed[] }) {
  const [user, setUser] = useState<AuthedUser | null>(currentUser())

  if (!user || user.role !== 'SEEKER') {
    return (
      <section className="card">
        <h3>Want to visit?</h3>
        <p className="muted">Sign in as a seeker to request a visit slot.</p>
        <LoginPage role="SEEKER" onSignedIn={setUser} />
      </section>
    )
  }

  if (vacantBeds.length === 0) {
    return (
      <section className="card">
        <h3>Want to visit?</h3>
        <p className="muted">No vacant beds right now - check back later.</p>
      </section>
    )
  }

  return <VisitRequestForm vacantBeds={vacantBeds} />
}

function VisitRequestForm({ vacantBeds }: { vacantBeds: Bed[] }) {
  const [bedId, setBedId] = useState(vacantBeds[0].id)
  const [slot, setSlot] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [confirmed, setConfirmed] = useState<Booking | null>(null)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const visitSlot = new Date(slot).toISOString()
      const booking = await api.post<Booking>('/bookings/visit', { bedId, visitSlot })
      setConfirmed(booking)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : String(err))
    } finally {
      setBusy(false)
    }
  }

  if (confirmed) {
    return (
      <section className="card">
        <h3>Visit requested</h3>
        <p>
          This bed is held for you until {new Date(confirmed.holdExpiresAt).toLocaleString()} while the
          owner confirms.
        </p>
        <a href="/bookings">See my bookings</a>
      </section>
    )
  }

  const minSlot = new Date(Date.now() + 30 * 60 * 1000).toISOString().slice(0, 16)

  return (
    <form className="card" onSubmit={submit}>
      <h3>Want to visit?</h3>
      {error && <p className="bad">{error}</p>}
      <label className="field">
        <span>Bed</span>
        <select value={bedId} onChange={(e) => setBedId(Number(e.target.value))}>
          {vacantBeds.map((b) => (
            <option key={b.id} value={b.id}>
              Room {b.roomNo ?? b.roomId} · Bed {b.bedLabel} · ₹{b.rentPerBed}/bed
            </option>
          ))}
        </select>
      </label>
      <label className="field">
        <span>Visit time</span>
        <input type="datetime-local" min={minSlot} value={slot} onChange={(e) => setSlot(e.target.value)} required />
      </label>
      <button type="submit" disabled={busy || !slot}>{busy ? 'Requesting…' : 'Request a visit'}</button>
      <p className="muted">This holds the bed for you until the owner confirms or the hold expires.</p>
    </form>
  )
}
