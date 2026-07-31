import { queryOptions } from '@tanstack/react-query'
import type { PlatformSettings } from '../../admin-data'
import { apiFetch } from '../client'
import { toPlatformSettings, toSettingsRequest, type PlatformSettingsWire } from '../mappers'

/** `GET /v1/admin/settings` — the platform settings. `super-admin` only. */
export function platformSettingsQuery() {
  return queryOptions({
    queryKey: ['admin', 'settings'],
    queryFn: async () => toPlatformSettings(await apiFetch<PlatformSettingsWire>('/admin/settings')),
  })
}

/**
 * `PUT /v1/admin/settings` — a FULL REPLACE. Every field including the nested `providers` and
 * `flags` objects is required; a partial body is a 422, not a merge. `super-admin` only.
 *
 * Note the server accepts but does not persist `providers.*` (no per-provider subsystem), and it
 * preserves the tip-fee, bundle-discount and service-fee constants, which are not on this contract.
 */
export function apiSaveSettings(settings: PlatformSettings): Promise<PlatformSettings> {
  return apiFetch<PlatformSettingsWire>('/admin/settings', {
    method: 'PUT',
    body: toSettingsRequest(settings),
  }).then(toPlatformSettings)
}
