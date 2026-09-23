import { useState } from 'react'
import { api, ApiError, type Bed, type BedState, type PgDetail } from '../lib/api'

// OCCUPIED and ON_NOTICE are deliberately not quick-actions here: moving a tenant in or giving
// notice needs to create/update a tenancy row too (see the Tenants & rent section below), and a
// bare state change here would leave that record missing or stale. VACANT/BOOKED -> OCCUPIED and
// OCCUPIED -> ON_NOTICE both still go through BedService the same way, just via TenancyService.
const NEXT_STATES: Record<BedState, BedState[]> = {
  VACANT: ['HELD', 'BLOCKED'],
  HELD: ['VACANT', 'BOOKED'],
  BOOKED: ['VACANT'],
  OCCUPIED: ['VACANT'],
  ON_NOTICE: ['VACANT', 'OCCUPIED'],
  BLOCKED: ['VACANT'],
}

const LABEL: Record<BedState, string> = {
  VACANT: 'Vacant',
  HELD: 'Held',
  BOOKED: 'Booked',
  OCCUPIED: 'Occupied',
  ON_NOTICE: 'On notice',
  BLOCKED: 'Blocked',
}

export function BedGrid({ detail, onChanged }: { detail: PgDetail; onChanged: () => void }) {
  const [busy, setBusy] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function change(bed: Bed, toState: BedState) {
    setError(null)
    let availableFrom: string | null = null
    if (toState === 'ON_NOTICE') {
      availableFrom = window.prompt('Free from which date? (YYYY-MM-DD)')
      if (!availableFrom) return
    }
    setBusy(bed.id)
    try {
      await api.patch(`/beds/${bed.id}/state`, { toState, availableFrom, reason: 'owner update' })
      onChanged()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
      onChanged() // re-read: someone else may have moved it
    } finally {
      setBusy(null)
    }
  }

  const rooms = new Map<number, Bed[]>()
  for (const b of detail.beds) {
    rooms.set(b.roomId, [...(rooms.get(b.roomId) ?? []), b])
  }

  if (detail.beds.length === 0) {
    return <p className="muted">No rooms yet. Add one above.</p>
  }

  return (
    <div>
      {error && <p className="bad">{error}</p>}
      {[...rooms.entries()].map(([roomId, beds]) => (
        <div key={roomId} className="room">
          <h4>
            Room {beds[0].roomNo ?? roomId} · {beds[0].sharingType.toLowerCase()} · ₹{beds[0].rentPerBed}/bed
            {beds[0].ac ? ' · AC' : ''}
          </h4>
          <div className="beds">
            {beds.map((bed) => (
              <div key={bed.id} className={`bed ${bed.state.toLowerCase()}`}>
                <div className="bed-head">
                  <strong>{bed.bedLabel}</strong>
                  <span>{LABEL[bed.state]}</span>
                </div>
                {bed.availableFrom && <div className="muted">free {bed.availableFrom}</div>}
                {(bed.state === 'VACANT' || bed.state === 'BOOKED') && (
                  <div className="muted">Move in a tenant below</div>
                )}
                {bed.state === 'OCCUPIED' && <div className="muted">Give notice below</div>}
                <div className="actions">
                  {NEXT_STATES[bed.state].map((s) => (
                    <button key={s} disabled={busy === bed.id} onClick={() => change(bed, s)}>
                      {LABEL[s]}
                    </button>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}
