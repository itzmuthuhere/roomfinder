// Temporary: the owner id is chosen in the UI until phone OTP login exists.
export function currentOwnerId(): number | null {
  const v = localStorage.getItem('ownerId')
  return v ? Number(v) : null
}

export function setOwnerId(id: number) {
  localStorage.setItem('ownerId', String(id))
}

export class ApiError extends Error {
  constructor(public code: string, message: string) {
    super(message)
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const ownerId = currentOwnerId()
  const res = await fetch(`/api${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(ownerId ? { 'X-Owner-Id': String(ownerId) } : {}),
      ...(init?.headers ?? {}),
    },
  })
  const text = await res.text()
  const body = text ? JSON.parse(text) : null
  if (!res.ok) {
    throw new ApiError(body?.code ?? 'UNKNOWN', body?.message ?? `HTTP ${res.status}`)
  }
  return body as T
}

export const api = {
  get: <T,>(p: string) => request<T>(p),
  post: <T,>(p: string, body: unknown) => request<T>(p, { method: 'POST', body: JSON.stringify(body) }),
  patch: <T,>(p: string, body: unknown) => request<T>(p, { method: 'PATCH', body: JSON.stringify(body) }),
  del: <T,>(p: string) => request<T>(p, { method: 'DELETE' }),
}

export type BedState = 'VACANT' | 'HELD' | 'BOOKED' | 'OCCUPIED' | 'ON_NOTICE' | 'BLOCKED'

export type Bed = {
  id: number
  roomId: number
  roomNo: string | null
  sharingType: string
  ac: boolean
  rentPerBed: number
  depositAmount: number
  bedLabel: string
  state: BedState
  availableFrom: string | null
  stateChangedAt: string
}

import type { Media } from './upload'

export type Pg = {
  id: number
  name: string
  genderType: string
  area: string
  address: string
  status: string
  instantBook: boolean
  totalBeds: number
  vacantBeds: number
}

export type PgDetail = { pg: Pg; beds: Bed[]; media: Media[] }
