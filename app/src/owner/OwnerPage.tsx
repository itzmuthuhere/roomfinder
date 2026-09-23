import { useEffect, useState } from 'react'
import { api, ApiError, type Pg, type PgDetail } from '../lib/api'
import { currentUser, logout, type AuthedUser } from '../lib/auth'
import { LoginPage } from '../auth/LoginPage'
import { BedGrid } from './BedGrid'
import { MediaManager } from './MediaManager'
import { VisitRequests } from './VisitRequests'
import { TenancyPanel } from './TenancyPanel'

export default function OwnerPage() {
  const [user, setUser] = useState<AuthedUser | null>(currentUser())
  const [pgs, setPgs] = useState<Pg[]>([])
  const [selected, setSelected] = useState<PgDetail | null>(null)
  const [error, setError] = useState<string | null>(null)

  function signOut() {
    logout()
    setUser(null)
    setPgs([])
    setSelected(null)
  }

  /** A token that expired or was signed with an old secret reads as UNAUTHENTICATED - send the owner back to login. */
  function handleApiError(e: unknown) {
    if (e instanceof ApiError && e.code === 'UNAUTHENTICATED') {
      signOut()
      return
    }
    setError(e instanceof ApiError ? e.message : String(e))
  }

  async function loadPgs() {
    if (!user) return
    try {
      setPgs(await api.get<Pg[]>('/pgs'))
    } catch (e) {
      handleApiError(e)
    }
  }

  async function openPg(id: number) {
    try {
      setSelected(await api.get<PgDetail>(`/pgs/${id}`))
    } catch (e) {
      handleApiError(e)
    }
  }

  useEffect(() => {
    void loadPgs()
  }, [user])

  if (!user) {
    return <LoginPage role="OWNER" onSignedIn={setUser} />
  }

  return (
    <div className="page">
      <header>
        <h2>My PGs</h2>
        <span className="row">
          <span className="muted">{user.phone}</span>
          <button onClick={signOut}>Sign out</button>
        </span>
      </header>

      {error && <p className="bad">{error}</p>}

      <CreatePgForm onCreated={loadPgs} />

      <ul className="pg-list">
        {pgs.map((p) => (
          <li key={p.id}>
            <button className="link" onClick={() => openPg(p.id)}>
              {p.name}
            </button>
            <span className="muted">
              {p.area} · {p.genderType.toLowerCase()} · {p.vacantBeds}/{p.totalBeds} vacant · {p.status}
            </span>
          </li>
        ))}
        {pgs.length === 0 && <p className="muted">No PGs yet. Add your first one above.</p>}
      </ul>

      {selected && (
        <section className="card">
          <header>
            <h3>{selected.pg.name}</h3>
            <span className="muted">
              {selected.pg.vacantBeds} of {selected.pg.totalBeds} beds vacant
            </span>
          </header>
          <AddRoomForm pgId={selected.pg.id} onAdded={(d) => { setSelected(d); void loadPgs() }} />
          <VisitRequests pgId={selected.pg.id} onChanged={() => { void openPg(selected.pg.id); void loadPgs() }} />
          <BedGrid detail={selected} onChanged={() => { void openPg(selected.pg.id); void loadPgs() }} />
          <TenancyPanel detail={selected} onChanged={() => { void openPg(selected.pg.id); void loadPgs() }} />
          <MediaManager detail={selected} onChanged={() => { void openPg(selected.pg.id) }} />
          <UpiSetting pg={selected.pg} onChanged={() => void openPg(selected.pg.id)} />
          <StatusToggle pg={selected.pg} onChanged={() => { void openPg(selected.pg.id); void loadPgs() }} />
        </section>
      )}
    </div>
  )
}

function CreatePgForm({ onCreated }: { onCreated: () => void }) {
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [lat, setLat] = useState('')
  const [lng, setLng] = useState('')
  const [locating, setLocating] = useState(false)

  function useMyLocation() {
    setLocating(true)
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setLat(pos.coords.latitude.toFixed(6))
        setLng(pos.coords.longitude.toFixed(6))
        setLocating(false)
      },
      (err) => {
        setError(`Could not get location: ${err.message}. Type it manually or copy from Google Maps.`)
        setLocating(false)
      },
      { enableHighAccuracy: true, timeout: 10000 },
    )
  }

  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const form = e.currentTarget
    const f = new FormData(form)
    setBusy(true)
    setError(null)
    try {
      await api.post('/pgs', {
        name: f.get('name'),
        genderType: f.get('genderType'),
        address: f.get('address'),
        area: f.get('area'),
        lat: Number(f.get('lat')),
        lng: Number(f.get('lng')),
        foodIncluded: f.get('foodIncluded') === 'on',
        foodType: f.get('foodType'),
        noticePeriodDays: Number(f.get('noticePeriodDays') || 30),
      })
      form.reset()
      setLat('')
      setLng('')
      setOpen(false)
      onCreated()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : String(err))
    } finally {
      setBusy(false)
    }
  }

  if (!open) return <button onClick={() => setOpen(true)}>+ Add a PG</button>

  return (
    <form className="card" onSubmit={submit}>
      <h3>New PG</h3>
      {error && <p className="bad">{error}</p>}

      <label className="field">
        <span>PG name</span>
        <input name="name" placeholder="e.g. Sri Sai Mens PG" required />
      </label>

      <label className="field">
        <span>Who can stay</span>
        <select name="genderType" defaultValue="BOYS">
          <option value="BOYS">Boys</option>
          <option value="GIRLS">Girls</option>
          <option value="UNISEX">Unisex</option>
          <option value="WORKING_WOMEN">Working women</option>
        </select>
      </label>

      <label className="field">
        <span>Full address</span>
        <input name="address" placeholder="Door no, street, landmark" required />
      </label>

      <label className="field">
        <span>Area</span>
        <input name="area" placeholder="e.g. Thoraipakkam" required />
      </label>

      <fieldset className="field">
        <legend>Map location</legend>
        <p className="muted">
          Stand at the PG and tap Use my location, or copy the coordinates from Google Maps (long-press the
          building, the numbers appear at the top).
        </p>
        <div className="row">
          <button type="button" onClick={useMyLocation} disabled={locating}>
            {locating ? 'Locating…' : 'Use my location'}
          </button>
        </div>
        <div className="row">
          <label className="field small">
            <span>Latitude</span>
            <input name="lat" value={lat} onChange={(e) => setLat(e.target.value)} placeholder="12.9391" required />
          </label>
          <label className="field small">
            <span>Longitude</span>
            <input name="lng" value={lng} onChange={(e) => setLng(e.target.value)} placeholder="80.2297" required />
          </label>
        </div>
      </fieldset>

      <label className="field checkbox">
        <input type="checkbox" name="foodIncluded" defaultChecked />
        <span>Food included in rent</span>
      </label>

      <label className="field">
        <span>Food type</span>
        <select name="foodType" defaultValue="BOTH">
          <option value="VEG">Veg</option>
          <option value="NONVEG">Non-veg</option>
          <option value="BOTH">Both</option>
        </select>
      </label>

      <label className="field">
        <span>Notice period (days a tenant must inform before leaving)</span>
        <input name="noticePeriodDays" type="number" defaultValue={30} min={0} max={180} />
      </label>

      <div className="row">
        <button type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save PG'}</button>
        <button type="button" onClick={() => setOpen(false)}>Cancel</button>
      </div>
    </form>
  )
}

function AddRoomForm({ pgId, onAdded }: { pgId: number; onAdded: (d: PgDetail) => void }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const form = e.currentTarget
    const f = new FormData(form)
    setBusy(true)
    setError(null)
    try {
      const detail = await api.post<PgDetail>(`/pgs/${pgId}/rooms`, {
        roomNo: f.get('roomNo'),
        sharingType: f.get('sharingType'),
        ac: f.get('ac') === 'on',
        attachedBath: f.get('attachedBath') === 'on',
        rentPerBed: Number(f.get('rentPerBed')),
        depositAmount: Number(f.get('depositAmount')),
        bedCount: Number(f.get('bedCount')),
      })
      form.reset()
      onAdded(detail)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : String(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="inline-form" onSubmit={submit}>
      {error && <p className="bad">{error}</p>}
      <label className="field small">
        <span>Room no</span>
        <input name="roomNo" placeholder="101" />
      </label>
      <label className="field small">
        <span>Sharing</span>
        <select name="sharingType" defaultValue="DOUBLE">
          <option value="SINGLE">Single</option>
          <option value="DOUBLE">Double</option>
          <option value="TRIPLE">Triple</option>
          <option value="FOUR_PLUS">4+</option>
        </select>
      </label>
      <label className="field small">
        <span>Rent per bed (₹/month)</span>
        <input name="rentPerBed" type="number" placeholder="9500" required />
      </label>
      <label className="field small">
        <span>Deposit (₹)</span>
        <input name="depositAmount" type="number" placeholder="19000" required />
      </label>
      <label className="field small">
        <span>Beds in this room</span>
        <input name="bedCount" type="number" defaultValue={2} min={1} max={20} required />
      </label>
      <label className="field checkbox"><input type="checkbox" name="ac" /><span>AC</span></label>
      <label className="field checkbox"><input type="checkbox" name="attachedBath" /><span>Attached bath</span></label>
      <button type="submit" disabled={busy}>{busy ? 'Adding…' : 'Add room'}</button>
    </form>
  )
}

function UpiSetting({ pg, onChanged }: { pg: Pg; onChanged: () => void }) {
  const [value, setValue] = useState(pg.upiVpa ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function save(e: React.FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api.patch(`/pgs/${pg.id}/upi`, { upiVpa: value })
      onChanged()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : String(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="row" onSubmit={save}>
      <label className="field small">
        <span>UPI ID for rent reminders</span>
        <input value={value} onChange={(e) => setValue(e.target.value)} placeholder="yourname@bank" />
      </label>
      <button type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save'}</button>
      {error && <span className="bad">{error}</span>}
    </form>
  )
}

function StatusToggle({ pg, onChanged }: { pg: Pg; onChanged: () => void }) {
  const next = pg.status === 'LIVE' ? 'PAUSED' : 'LIVE'
  return (
    <div className="row">
      <span className="muted">Listing status: {pg.status}</span>
      <button
        onClick={async () => {
          await api.patch(`/pgs/${pg.id}/status`, { status: next })
          onChanged()
        }}
      >
        Make {next.toLowerCase()}
      </button>
    </div>
  )
}
