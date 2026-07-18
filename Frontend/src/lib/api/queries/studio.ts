import { queryOptions } from '@tanstack/react-query'
import type { AnalyticsRange, AudienceRange } from '../../studio-analytics'
import type { StudioProfile, StudioSettings, StudioRelease } from '../../studio-data'
import type { ReleaseType } from '../../studio-data'
import type { UploadedTrack } from '../../../features/studio/release-draft-context'
import { apiFetch } from '../client'
import { unwrapPage, type PageWire } from '../pagination'
import {
  toAnalytics, toAudience, type AnalyticsWire, type AudienceWire,
  toStudioProfile, type StudioProfileWire, toSaveProfileBody, type SaveStudioProfileBody,
  toStudioSettings, type StudioSettingsWire, toSaveSettingsBody, type SaveStudioSettingsBody,
  toStudioRelease, type StudioReleaseWire, toWizardTrack, type UploadedTrackWire,
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

/** `GET /v1/studio/releases` — the creator's releases (one large page; summary is client-side). */
export function studioReleasesQuery() {
  return queryOptions({
    queryKey: ['studio', 'releases'],
    queryFn: async () =>
      unwrapPage(await apiFetch<PageWire<StudioReleaseWire>>('/studio/releases?size=100'), toStudioRelease),
  })
}

/** `GET /v1/studio/releases/:id` — one release (404s if not the caller's). */
export function studioReleaseQuery(id: string) {
  return queryOptions({
    queryKey: ['studio', 'release', id],
    queryFn: async () => toStudioRelease(await apiFetch<StudioReleaseWire>(`/studio/releases/${id}`)),
  })
}

/** `PATCH /v1/studio/releases/:id` — rename (title is the only updatable field). */
export function apiRenameRelease(id: string, title: string): Promise<StudioRelease> {
  return apiFetch<StudioReleaseWire>(`/studio/releases/${id}`, { method: 'PATCH', body: { title } })
    .then(toStudioRelease)
}

/** `DELETE /v1/studio/releases/:id` — draft/in_review only; throws ApiError for live. */
export function apiDeleteRelease(id: string): Promise<void> {
  return apiFetch<void>(`/studio/releases/${id}`, { method: 'DELETE' })
}

// ── Release create-flow (WU-CAT-5 draft flow) ─────────────────────

export interface CreateDraftInput {
  title?: string
  type: ReleaseType
  genre?: string
  description?: string
  visibility?: 'public' | 'scheduled'
  scheduledAt?: string
}

export interface TrackPatch { trackId: string; position: number; priceMinor: number }

export interface UpdateReleaseInput {
  title?: string
  genre?: string
  description?: string
  visibility?: 'public' | 'scheduled'
  scheduledAt?: string
  tracks?: TrackPatch[]
}

/** `POST /v1/studio/releases` — create a metadata-only draft; returns the new id. */
export function apiCreateDraft(input: CreateDraftInput): Promise<string> {
  return apiFetch<{ id: string }>('/studio/releases', { method: 'POST', body: input }).then((r) => r.id)
}

/** `POST /v1/studio/releases/:id/tracks` — multipart upload-attach; part name is "file". */
export function apiUploadTrack(releaseId: string, file: File): Promise<UploadedTrack> {
  const form = new FormData()
  form.append('file', file)
  return apiFetch<UploadedTrackWire>(`/studio/releases/${releaseId}/tracks`, { method: 'POST', body: form })
    .then(toWizardTrack)
}

/** `PATCH /v1/studio/releases/:id` — metadata + wholesale track list (draft-only). */
export function apiUpdateRelease(releaseId: string, patch: UpdateReleaseInput): Promise<void> {
  return apiFetch<unknown>(`/studio/releases/${releaseId}`, { method: 'PATCH', body: patch }).then(() => undefined)
}

/** `POST /v1/studio/releases/:id/submit` — finalize draft → in_review. */
export function apiSubmitRelease(releaseId: string, idempotencyKey: string): Promise<void> {
  return apiFetch<unknown>(`/studio/releases/${releaseId}/submit`, { method: 'POST', idempotencyKey }).then(() => undefined)
}

/** `DELETE /v1/studio/releases/:id/tracks/:trackId` — remove a draft track. */
export function apiDeleteTrack(releaseId: string, trackId: string): Promise<void> {
  return apiFetch<void>(`/studio/releases/${releaseId}/tracks/${trackId}`, { method: 'DELETE' })
}
