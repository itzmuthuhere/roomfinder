import { useEffect, useState } from 'react'
import { api, ApiError, type Bed, type PgDetail } from '../lib/api'

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
    </div>
  )
}
