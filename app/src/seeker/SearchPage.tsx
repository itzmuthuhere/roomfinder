import { useState } from 'react'
import { api, ApiError, type SearchResult } from '../lib/api'

type Coords = { lat: number; lng: number } | null

export default function SearchPage() {
  const [area, setArea] = useState('')
  const [minRent, setMinRent] = useState('')
  const [maxRent, setMaxRent] = useState('')
  const [sharingType, setSharingType] = useState('')
  const [genderType, setGenderType] = useState('')
  const [ac, setAc] = useState(false)
  const [foodIncluded, setFoodIncluded] = useState(false)
  const [radiusKm, setRadiusKm] = useState('5')
  const [coords, setCoords] = useState<Coords>(null)
  const [locating, setLocating] = useState(false)
  const [results, setResults] = useState<SearchResult[] | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function useMyLocation() {
    setLocating(true)
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setCoords({ lat: pos.coords.latitude, lng: pos.coords.longitude })
        setLocating(false)
      },
      (err) => {
        setError(`Could not get your location: ${err.message}`)
        setLocating(false)
      },
      { enableHighAccuracy: true, timeout: 10000 },
    )
  }

  async function runSearch(e?: React.FormEvent) {
    e?.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const params = new URLSearchParams()
      if (area) params.set('area', area)
      if (minRent) params.set('minRent', minRent)
      if (maxRent) params.set('maxRent', maxRent)
      if (sharingType) params.set('sharingType', sharingType)
      if (genderType) params.set('genderType', genderType)
      if (ac) params.set('ac', 'true')
      if (foodIncluded) params.set('foodIncluded', 'true')
      if (coords) {
        params.set('lat', String(coords.lat))
        params.set('lng', String(coords.lng))
        params.set('radiusKm', radiusKm)
      }
      setResults(await api.get<SearchResult[]>(`/search?${params.toString()}`))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : String(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <h2>Find a PG</h2>

      <form className="card" onSubmit={runSearch}>
        <label className="field">
          <span>Area</span>
          <input value={area} onChange={(e) => setArea(e.target.value)} placeholder="e.g. Velachery" />
        </label>

        <div className="row">
          <button type="button" onClick={useMyLocation} disabled={locating}>
            {locating ? 'Locating…' : coords ? 'Location set ✓' : 'Search near me'}
          </button>
          {coords && (
            <label className="field small">
              <span>Within</span>
              <select value={radiusKm} onChange={(e) => setRadiusKm(e.target.value)}>
                <option value="1">1 km</option>
                <option value="3">3 km</option>
                <option value="5">5 km</option>
                <option value="10">10 km</option>
              </select>
            </label>
          )}
        </div>

        <div className="row">
          <label className="field small">
            <span>Min rent (₹)</span>
            <input type="number" value={minRent} onChange={(e) => setMinRent(e.target.value)} placeholder="5000" />
          </label>
          <label className="field small">
            <span>Max rent (₹)</span>
            <input type="number" value={maxRent} onChange={(e) => setMaxRent(e.target.value)} placeholder="12000" />
          </label>
        </div>

        <div className="row">
          <label className="field small">
            <span>Sharing</span>
            <select value={sharingType} onChange={(e) => setSharingType(e.target.value)}>
              <option value="">Any</option>
              <option value="SINGLE">Single</option>
              <option value="DOUBLE">Double</option>
              <option value="TRIPLE">Triple</option>
              <option value="FOUR_PLUS">4+</option>
            </select>
          </label>
          <label className="field small">
            <span>Who can stay</span>
            <select value={genderType} onChange={(e) => setGenderType(e.target.value)}>
              <option value="">Any</option>
              <option value="BOYS">Boys</option>
              <option value="GIRLS">Girls</option>
              <option value="WORKING_WOMEN">Working women</option>
              <option value="UNISEX">Unisex</option>
            </select>
          </label>
        </div>

        <div className="row">
          <label className="field checkbox">
            <input type="checkbox" checked={ac} onChange={(e) => setAc(e.target.checked)} />
            <span>AC only</span>
          </label>
          <label className="field checkbox">
            <input type="checkbox" checked={foodIncluded} onChange={(e) => setFoodIncluded(e.target.checked)} />
            <span>Food included</span>
          </label>
        </div>

        {error && <p className="bad">{error}</p>}
        <button type="submit" disabled={busy}>{busy ? 'Searching…' : 'Search'}</button>
      </form>

      {results !== null && (
        <section>
          <p className="muted">{results.length} PG{results.length === 1 ? '' : 's'} found</p>
          <div className="search-results">
            {results.map((r) => (
              <a key={r.id} className="result-card" href={`/pgs/${r.id}`}>
                {r.coverPhotoUrl ? (
                  <img src={r.coverPhotoUrl} alt="" loading="lazy" />
                ) : (
                  <div className="result-card-noimg muted">No photo yet</div>
                )}
                <div className="result-card-body">
                  <strong>{r.name}</strong>
                  <span className="muted">{r.area} · {r.genderType.toLowerCase().replace('_', ' ')}</span>
                  <span>₹{r.minRentPerBed}/bed <span className="muted">from</span></span>
                  <span className="muted">
                    {r.vacantBeds} bed{r.vacantBeds === 1 ? '' : 's'} vacant
                    {r.distanceKm != null && <> · {r.distanceKm.toFixed(1)} km away</>}
                  </span>
                </div>
              </a>
            ))}
            {results.length === 0 && <p className="muted">No PGs match yet. Try widening your filters.</p>}
          </div>
        </section>
      )}
    </div>
  )
}
