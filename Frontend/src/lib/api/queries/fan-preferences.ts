import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'

/** Minimum picks required to finish onboarding. The backend enforces the genre half. */
export const MIN_ONBOARDING_GENRES = 3
export const MIN_ONBOARDING_ARTISTS = 3

export interface FanPreferencesWire {
  preferredGenres: string[]
  onboarded: boolean
  completedAt: string | null
}

export interface FanPreferences {
  preferredGenres: string[]
  /** Whether this fan has been through onboarding. Drives the gate. */
  onboarded: boolean
  completedAt: string | null
}

function toPreferences(w: FanPreferencesWire): FanPreferences {
  return {
    preferredGenres: w.preferredGenres ?? [],
    onboarded: w.onboarded ?? false,
    completedAt: w.completedAt ?? null,
  }
}

/**
 * `GET /v1/me/preferences` — never 404s; a fan who has never onboarded reads back
 * `{ preferredGenres: [], onboarded: false }`.
 *
 * `staleTime: Infinity` because this only changes when the fan themselves changes it, and the
 * gate consults it on every navigation — refetching would put a request in front of every route.
 */
export function fanPreferencesQuery() {
  return queryOptions({
    queryKey: ['me', 'preferences'],
    queryFn: async () => toPreferences(await apiFetch<FanPreferencesWire>('/me/preferences')),
    staleTime: Infinity,
  })
}

/** `POST /v1/me/preferences/onboarding` — 422 if fewer than 3 genres, or any genre is unknown. */
export async function apiCompleteOnboarding(genres: string[]): Promise<FanPreferences> {
  return toPreferences(
    await apiFetch<FanPreferencesWire>('/me/preferences/onboarding', {
      method: 'POST',
      body: { genres },
    }),
  )
}

/** `PUT /v1/me/preferences` — edit the taste profile later without re-running the gate. */
export async function apiUpdatePreferredGenres(genres: string[]): Promise<FanPreferences> {
  return toPreferences(
    await apiFetch<FanPreferencesWire>('/me/preferences', { method: 'PUT', body: { genres } }),
  )
}
