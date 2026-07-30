import { queryOptions } from '@tanstack/react-query'
import type { FeaturedSlot } from '../../admin-data'
import { apiFetch } from '../client'
import {
  toFeaturedSlot, toPushItem, toCuratedPlaylist, toFeaturedSlotRequest,
  type FeaturedSlotWire, type PushItemWire, type CuratedPlaylistWire,
} from '../mappers'

/** `GET /v1/admin/editorial/featured` — the ordered home-featured slots. */
export function featuredQuery() {
  return queryOptions({
    queryKey: ['admin', 'editorial', 'featured'],
    queryFn: async () => (await apiFetch<FeaturedSlotWire[]>('/admin/editorial/featured')).map(toFeaturedSlot),
  })
}

/** `GET /v1/admin/editorial/push` — this week's scheduled push notifications. */
export function pushScheduleQuery() {
  return queryOptions({
    queryKey: ['admin', 'editorial', 'push'],
    queryFn: async () => (await apiFetch<PushItemWire[]>('/admin/editorial/push')).map(toPushItem),
  })
}

/** `GET /v1/admin/editorial/playlists` — the curated playlist tiles. */
export function curatedPlaylistsQuery() {
  return queryOptions({
    queryKey: ['admin', 'editorial', 'playlists'],
    queryFn: async () => (await apiFetch<CuratedPlaylistWire[]>('/admin/editorial/playlists')).map(toCuratedPlaylist),
  })
}

/**
 * `PUT /v1/admin/editorial/featured` — a **full ordered replace**, not a patch: whatever list is
 * sent becomes the featured set, so callers must send the complete list in display order.
 * Requires `super-admin` or `editor` (a `support` admin can read this page but not save).
 * 422 on a blank title or a duplicate id.
 */
export function apiSaveFeatured(slots: FeaturedSlot[]): Promise<FeaturedSlot[]> {
  return apiFetch<FeaturedSlotWire[]>('/admin/editorial/featured', {
    method: 'PUT',
    body: slots.map(toFeaturedSlotRequest),
  }).then((saved) => saved.map(toFeaturedSlot))
}
