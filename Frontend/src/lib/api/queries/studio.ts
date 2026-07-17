import { queryOptions } from '@tanstack/react-query'
import type { AnalyticsRange, AudienceRange } from '../../studio-analytics'
import type { StudioProfile, StudioSettings } from '../../studio-data'
import { apiFetch } from '../client'
import {
  toAnalytics, toAudience, type AnalyticsWire, type AudienceWire,
  toStudioProfile, type StudioProfileWire, toSaveProfileBody, type SaveStudioProfileBody,
  toStudioSettings, type StudioSettingsWire, toSaveSettingsBody, type SaveStudioSettingsBody,
} from '../mappers'

/**
 * `GET /v1/studio/analytics?range=` — the signed-in creator's analytics for a range.
 * The range is part of the query key so switching it refetches rather than reusing
 * the previous range's cached view.
 */
export function studioAnalyticsQuery(range: AnalyticsRange) {
  return queryOptions({
    queryKey: ['studio', 'analytics', range],
    queryFn: async () => toAnalytics(await apiFetch<AnalyticsWire>(`/studio/analytics?range=${range}`)),
  })
}

/** `GET /v1/studio/audience?range=` — the signed-in creator's audience for a range. */
export function studioAudienceQuery(range: AudienceRange) {
  return queryOptions({
    queryKey: ['studio', 'audience', range],
    queryFn: async () => toAudience(await apiFetch<AudienceWire>(`/studio/audience?range=${range}`)),
  })
}

/** `GET /v1/studio/profile` — the signed-in creator's own profile (never 404s). */
export function studioProfileQuery() {
  return queryOptions({
    queryKey: ['studio', 'profile'],
    queryFn: async () => toStudioProfile(await apiFetch<StudioProfileWire>('/studio/profile')),
  })
}

/** `GET /v1/studio/settings` — the signed-in creator's settings (full shape). */
export function studioSettingsQuery() {
  return queryOptions({
    queryKey: ['studio', 'settings'],
    queryFn: async () => toStudioSettings(await apiFetch<StudioSettingsWire>('/studio/settings')),
  })
}

/** `PUT /v1/studio/profile`. Throws ApiError (409 USERNAME_TAKEN / 422 INVALID_GENRE). */
export function apiSaveStudioProfile(profile: StudioProfile): Promise<StudioProfile> {
  const body: SaveStudioProfileBody = toSaveProfileBody(profile)
  return apiFetch<StudioProfileWire>('/studio/profile', { method: 'PUT', body }).then(toStudioProfile)
}

/** `PUT /v1/studio/settings`. Sends only the Category-A writable subset. */
export function apiSaveStudioSettings(settings: StudioSettings): Promise<StudioSettings> {
  const body: SaveStudioSettingsBody = toSaveSettingsBody(settings)
  return apiFetch<StudioSettingsWire>('/studio/settings', { method: 'PUT', body }).then(toStudioSettings)
}
