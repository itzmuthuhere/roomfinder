import { currentToken } from './auth'

export class ApiError extends Error {
  constructor(public code: string, message: string) {
    super(message)
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const token = currentToken()
  const res = await fetch(`/api${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
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
  foodIncluded: boolean | null
  foodType: string | null
  noticePeriodDays: number | null
}

export type PgDetail = { pg: Pg; beds: Bed[]; media: Media[] }

export type SearchResult = {
  id: number
  name: string
  area: string
  address: string
  genderType: string
  coverPhotoUrl: string | null
  minRentPerBed: number
  vacantBeds: number
  distanceKm: number | null
}
