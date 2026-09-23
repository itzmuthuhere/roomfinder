import { useEffect, useState } from 'react'
import { api, ApiError, type Bed, type PgDetail, type RentPayment, type Tenancy } from '../lib/api'

export function TenancyPanel({ detail, onChanged }: { detail: PgDetail; onChanged: () => void }) {
  const pgId = detail.pg.id
  const [tenancies, setTenancies] = useState<Tenancy[]>([])
  const [rent, setRent] = useState<RentPayment[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState<string | null>(null)

  async function load() {
    try {
      const [t, r] = await Promise.all([
        api.get<Tenancy[]>(`/pgs/${pgId}/tenancies`),
        api.get<RentPayment[]>(`/pgs/${pgId}/rent`),
      ])
      setTenancies(t)
      setRent(r)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    }
  }

  useEffect(() => {
    void load()
  }, [pgId])

  async function giveNotice(tenancyId: number) {
    const vacateDate = window.prompt('Last day in the room (YYYY-MM-DD)?')
    if (!vacateDate) return
    setError(null)
    setBusy(`notice-${tenancyId}`)
    try {
      await api.patch(`/tenancies/${tenancyId}/notice`, { vacateDate })
      await load()
      onChanged()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(null)
    }
  }

  async function markPaid(paymentId: number) {
    setError(null)
    setBusy(`pay-${paymentId}`)
    try {
      await api.patch(`/rent/${paymentId}/paid`, {})
      await load()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(null)
    }
  }

  async function remind(paymentId: number) {
    setError(null)
    setBusy(`remind-${paymentId}`)
    try {
      await api.post(`/rent/${paymentId}/remind`, {})
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(null)
    }
  }

  const openTenancies = tenancies.filter((t) => t.status === 'ACTIVE' || t.status === 'ON_NOTICE')
  const availableBeds = detail.beds.filter((b) => b.state === 'VACANT' || b.state === 'BOOKED')

  function latestPaymentFor(tenancyId: number): RentPayment | undefined {
    return rent.find((r) => r.tenancyId === tenancyId) // listForPg orders newest for_month first
  }

  function bedLabel(bedId: number): string {
    const bed = detail.beds.find((b) => b.id === bedId)
    return bed ? `Room ${bed.roomNo ?? bed.roomId} · Bed ${bed.bedLabel}` : `Bed #${bedId}`
  }

  return (
    <section className="card">
      <h4>Tenants & rent</h4>
      {error && <p className="bad">{error}</p>}

      {openTenancies.length === 0 && <p className="muted">No tenants yet.</p>}
      {openTenancies.map((t) => {
        const payment = latestPaymentFor(t.id)
        return (
          <div key={t.id} className="tenancy-row">
            <div className="row" style={{ justifyContent: 'space-between' }}>
              <strong>{bedLabel(t.bedId)}</strong>
              <span>{t.tenantName ? `${t.tenantName} · ${t.tenantPhone}` : t.tenantPhone}</span>
            </div>
            <div className="row muted">
              <span>₹{t.rentAmount}/month · due the {t.rentDueDay}{daySuffix(t.rentDueDay)}</span>
              <span>{t.status === 'ON_NOTICE' ? `leaving ${t.vacateDate}` : 'active'}</span>
            </div>
            <div className="row">
              {payment && (
                <span className="muted">{payment.status.toLowerCase()} for {monthLabel(payment.forMonth)} (₹{payment.amount})</span>
              )}
              {payment && payment.status !== 'PAID' && (
                <>
                  <button disabled={busy === `pay-${payment.id}`} onClick={() => markPaid(payment.id)}>Mark paid</button>
                  <button disabled={busy === `remind-${payment.id}`} onClick={() => remind(payment.id)}>Remind</button>
                </>
              )}
              {t.status === 'ACTIVE' && (
                <button disabled={busy === `notice-${t.id}`} onClick={() => giveNotice(t.id)}>Give notice</button>
              )}
            </div>
          </div>
        )
      })}

      <MoveInForm beds={availableBeds} onMoved={() => { void load(); onChanged() }} />
    </section>
  )
}

function MoveInForm({ beds, onMoved }: { beds: Bed[]; onMoved: () => void }) {
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const f = new FormData(e.currentTarget)
    setBusy(true)
    setError(null)
    try {
      await api.post(`/tenancies`, {
        bedId: Number(f.get('bedId')),
        tenantPhone: f.get('tenantPhone'),
        tenantName: f.get('tenantName') || null,
        moveInDate: f.get('moveInDate'),
        rentAmount: Number(f.get('rentAmount')),
        rentDueDay: f.get('rentDueDay') ? Number(f.get('rentDueDay')) : null,
      })
      setOpen(false)
      onMoved()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : String(err))
    } finally {
      setBusy(false)
    }
  }

  if (beds.length === 0) return null

  if (!open) {
    return <button onClick={() => setOpen(true)}>+ Move in a tenant</button>
  }

  return (
    <form className="inline-form" onSubmit={submit} style={{ marginTop: 12 }}>
      {error && <p className="bad">{error}</p>}
      <label className="field small">
        <span>Bed</span>
        <select name="bedId" defaultValue={beds[0].id}>
          {beds.map((b) => (
            <option key={b.id} value={b.id}>
              Room {b.roomNo ?? b.roomId} · Bed {b.bedLabel} · ₹{b.rentPerBed}
            </option>
          ))}
        </select>
      </label>
      <label className="field small">
        <span>Tenant phone</span>
        <input name="tenantPhone" placeholder="9xxxxxxxxx" required pattern="[6-9][0-9]{9}" />
      </label>
      <label className="field small">
        <span>Tenant name</span>
        <input name="tenantName" placeholder="optional" />
      </label>
      <label className="field small">
        <span>Move-in date</span>
        <input name="moveInDate" type="date" required />
      </label>
      <label className="field small">
        <span>Rent (₹/month)</span>
        <input name="rentAmount" type="number" defaultValue={beds[0].rentPerBed} required />
      </label>
      <label className="field small">
        <span>Due day</span>
        <input name="rentDueDay" type="number" min={1} max={28} defaultValue={5} />
      </label>
      <div className="row">
        <button type="submit" disabled={busy}>{busy ? 'Moving in…' : 'Move in'}</button>
        <button type="button" onClick={() => setOpen(false)}>Cancel</button>
      </div>
    </form>
  )
}

function monthLabel(forMonth: string): string {
  return new Date(forMonth + 'T00:00:00').toLocaleDateString(undefined, { month: 'long', year: 'numeric' })
}

function daySuffix(day: number): string {
  if (day >= 11 && day <= 13) return 'th'
  switch (day % 10) {
    case 1: return 'st'
    case 2: return 'nd'
    case 3: return 'rd'
    default: return 'th'
  }
}
