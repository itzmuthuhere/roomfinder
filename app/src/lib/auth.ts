import { api } from './api'

export type Role = 'OWNER' | 'SEEKER'

export type AuthedUser = { id: number; phone: string; role: Role }

const TOKEN_KEY = 'authToken'

export function currentToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function logout() {
  localStorage.removeItem(TOKEN_KEY)
}

/**
 * Reads id/phone/role out of the token for immediate UI use, without checking the signature -
 * the server is the only thing that needs to trust this. A forged token simply gets 401s from
 * every real endpoint, same as no token at all.
 */
export function currentUser(): AuthedUser | null {
  const token = currentToken()
  if (!token) return null
  try {
    const payload = token.split('.')[1]
    const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')))
    return { id: Number(json.sub), phone: json.phone, role: json.role }
  } catch {
    return null
  }
}

export async function requestOtp(phone: string, role?: Role): Promise<{ expiresInSeconds: number }> {
  return api.post('/auth/otp/request', role ? { phone, role } : { phone })
}

export async function verifyOtp(phone: string, code: string): Promise<AuthedUser> {
  const res = await api.post<{ token: string; user: AuthedUser }>('/auth/otp/verify', { phone, code })
  setToken(res.token)
  return res.user
}
