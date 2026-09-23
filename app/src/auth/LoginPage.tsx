import { useState } from 'react'
import { ApiError } from '../lib/api'
import { requestOtp, verifyOtp, type AuthedUser } from '../lib/auth'

/** Phone -> OTP -> signed in. Always signs up new numbers as OWNER; this is the owner app for now. */
export function LoginPage({ onSignedIn }: { onSignedIn: (user: AuthedUser) => void }) {
  const [phone, setPhone] = useState('')
  const [code, setCode] = useState('')
  const [stage, setStage] = useState<'phone' | 'code'>('phone')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function sendCode(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await requestOtp(phone, 'OWNER')
      setStage('code')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : String(err))
    } finally {
      setBusy(false)
    }
  }

  async function verify(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const user = await verifyOtp(phone, code)
      onSignedIn(user)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : String(err))
    } finally {
      setBusy(false)
    }
  }

  if (stage === 'phone') {
    return (
      <form className="card" onSubmit={sendCode}>
        <h3>Owner sign in</h3>
        {error && <p className="bad">{error}</p>}
        <label className="field">
          <span>Mobile number</span>
          <input
            type="tel"
            inputMode="numeric"
            placeholder="9xxxxxxxxx"
            value={phone}
            onChange={(e) => setPhone(e.target.value.replace(/\D/g, '').slice(0, 10))}
            required
          />
        </label>
        <button type="submit" disabled={busy || phone.length !== 10}>
          {busy ? 'Sending…' : 'Send code'}
        </button>
      </form>
    )
  }

  return (
    <form className="card" onSubmit={verify}>
      <h3>Enter the code</h3>
      <p className="muted">Sent to {phone}. Valid for 5 minutes.</p>
      {error && <p className="bad">{error}</p>}
      <label className="field">
        <span>6-digit code</span>
        <input
          type="tel"
          inputMode="numeric"
          placeholder="123456"
          value={code}
          onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
          autoFocus
          required
        />
      </label>
      <div className="row">
        <button type="submit" disabled={busy || code.length !== 6}>
          {busy ? 'Verifying…' : 'Verify'}
        </button>
        <button type="button" onClick={() => { setStage('phone'); setCode(''); setError(null) }}>
          Change number
        </button>
      </div>
    </form>
  )
}
